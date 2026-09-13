package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class ChapterListUiState(
    val files: List<ChapterFile> = emptyList(),
    val progressByPath: Map<String, ChapterProgress> = emptyMap(),
    val availableVolumes: List<Int> = emptyList(),
    val selectedVolume: Int? = null,
    val refreshing: Boolean = false,
    val notice: String? = null,
)

internal class ChapterListViewModel(
    private val repositoryFactory: ChapterRepositoryFactory = DefaultChapterRepositoryFactory,
    private val cacheStore: ChapterProgressCacheStore = NoOpChapterProgressCacheStore,
    private val cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChapterListUiState())
    val uiState: StateFlow<ChapterListUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var cacheSaveJob: Job? = null
    private val progressConcurrency = Semaphore(4)

    fun refresh(settings: RepoSettings, token: String) {
        if (token.isBlank()) return
        refreshJob?.cancel()
        cacheSaveJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, notice = null) }
            val cached = withContext(cacheDispatcher) { cacheStore.load(settings) }
            val cachedVolume = _uiState.value.selectedVolume ?: cached?.files?.firstOrNull()?.volume
            if (cached != null && cachedVolume != null) {
                val cachedFiles = cached.files.filter { it.volume == cachedVolume }
                val cachedPaths = cachedFiles.mapTo(hashSetOf()) { it.path }
                _uiState.update { state ->
                    state.copy(
                        files = cachedFiles,
                        progressByPath = cached.progressByPath.filterKeys { it in cachedPaths },
                        availableVolumes = (state.availableVolumes + cached.files.map { it.volume }).distinct().sorted(),
                        selectedVolume = cachedVolume,
                        refreshing = true,
                    )
                }
            }

            val repository = repositoryFactory.create(settings, token)
            try {
                val volumes = repository.listVolumes()
                val currentSelection = _uiState.value.selectedVolume
                val targetVolume = when {
                    currentSelection != null && currentSelection in volumes -> currentSelection
                    cachedVolume != null && cachedVolume in volumes -> cachedVolume
                    else -> volumes.maxOrNull()
                }
                _uiState.update { state ->
                    val keepCurrent = targetVolume != null && state.files.all { it.volume == targetVolume }
                    state.copy(
                        availableVolumes = volumes,
                        selectedVolume = targetVolume,
                        files = if (keepCurrent) state.files else emptyList(),
                        progressByPath = if (keepCurrent) state.progressByPath else emptyMap(),
                        refreshing = targetVolume != null,
                    )
                }
                if (targetVolume == null) {
                    _uiState.update { it.copy(refreshing = false, notice = "No intake volumes were found.") }
                    return@launch
                }
                loadVolume(repository, settings, targetVolume)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        refreshing = false,
                        notice = t.message ?: "Could not refresh chapter list.",
                    )
                }
            }
        }
    }

    fun selectVolume(volume: Int, settings: RepoSettings, token: String) {
        if (token.isBlank() || volume == _uiState.value.selectedVolume) return
        if (_uiState.value.availableVolumes.isNotEmpty() && volume !in _uiState.value.availableVolumes) return
        refreshJob?.cancel()
        cacheSaveJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { state ->
                val retainedFiles = state.files.filter { it.volume == volume }
                val retainedPaths = retainedFiles.mapTo(hashSetOf()) { it.path }
                state.copy(
                    selectedVolume = volume,
                    files = retainedFiles,
                    progressByPath = state.progressByPath.filterKeys { it in retainedPaths },
                    refreshing = true,
                    notice = null,
                )
            }
            try {
                loadVolume(repositoryFactory.create(settings, token), settings, volume)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _uiState.update {
                    it.copy(
                        refreshing = false,
                        notice = t.message ?: "Could not load Volume $volume.",
                    )
                }
            }
        }
    }

    private suspend fun loadVolume(
        repository: ChapterRepository,
        settings: RepoSettings,
        volume: Int,
    ) {
        val listed = repository.listFiles(volume)
        val listedPaths = listed.mapTo(hashSetOf()) { it.path }
        _uiState.update { state ->
            state.copy(
                files = listed,
                progressByPath = state.progressByPath.filterKeys { it in listedPaths },
                selectedVolume = volume,
                refreshing = false,
            )
        }
        scheduleCacheSave(settings)

        listed.forEach { file ->
            viewModelScope.launch {
                val loaded = progressConcurrency.withPermit {
                    try {
                        repository.loadBaseProgress(file)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        null
                    }
                } ?: return@launch

                if (_uiState.value.selectedVolume != volume) return@launch
                _uiState.update { state ->
                    state.copy(progressByPath = state.progressByPath + (file.path to loaded.progress))
                }
                scheduleCacheSave(settings)

                val qaCounts = progressConcurrency.withPermit {
                    try {
                        repository.loadQaCounts(file, loaded.editorContentSha256)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        null
                    }
                } ?: return@launch

                if (_uiState.value.selectedVolume != volume) return@launch
                _uiState.update { state ->
                    val current = state.progressByPath[file.path] ?: loaded.progress
                    state.copy(
                        progressByPath = state.progressByPath + (
                            file.path to current.copy(
                                qaActive = qaCounts.active,
                                qaTotal = qaCounts.total,
                            )
                        ),
                    )
                }
                scheduleCacheSave(settings)
            }
        }
    }

    private fun scheduleCacheSave(settings: RepoSettings) {
        cacheSaveJob?.cancel()
        cacheSaveJob = viewModelScope.launch {
            delay(250)
            val state = _uiState.value
            val snapshot = ChapterListCacheSnapshot(
                files = state.files,
                progressByPath = state.progressByPath,
            )
            withContext(cacheDispatcher) { cacheStore.save(settings, snapshot) }
        }
    }
}

internal class ChapterListViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChapterListViewModel::class.java))
        return ChapterListViewModel(
            repositoryFactory = DefaultChapterRepositoryFactory,
            cacheStore = SharedPreferencesChapterProgressCacheStore(applicationContext),
        ) as T
    }
}
