package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val refreshing: Boolean = false,
    val notice: String? = null,
)

internal class ChapterListViewModel(
    private val repositoryFactory: ChapterRepositoryFactory = DefaultChapterRepositoryFactory,
    private val cacheStore: ChapterProgressCacheStore = NoOpChapterProgressCacheStore,
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
            val cached = withContext(Dispatchers.IO) { cacheStore.load(settings) }
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        files = cached.files,
                        progressByPath = cached.progressByPath,
                        refreshing = true,
                    )
                }
            }

            val repository = repositoryFactory.create(settings, token)
            try {
                val listed = repository.listFiles()
                val listedPaths = listed.mapTo(hashSetOf()) { it.path }
                _uiState.update { state ->
                    state.copy(
                        files = listed,
                        progressByPath = state.progressByPath.filterKeys { it in listedPaths },
                        refreshing = false,
                    )
                }
                scheduleCacheSave(settings)

                listed.forEach { file ->
                    launch {
                        val loaded = progressConcurrency.withPermit {
                            runCatching { repository.loadBaseProgress(file) }.getOrNull()
                        } ?: return@launch

                        _uiState.update { state ->
                            state.copy(progressByPath = state.progressByPath + (file.path to loaded.progress))
                        }
                        scheduleCacheSave(settings)

                        val qaCounts = progressConcurrency.withPermit {
                            runCatching {
                                repository.loadQaCounts(file, loaded.editorContentSha256)
                            }.getOrNull()
                        } ?: return@launch

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
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        refreshing = false,
                        notice = t.message ?: "Could not refresh chapter list.",
                    )
                }
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
            withContext(Dispatchers.IO) { cacheStore.save(settings, snapshot) }
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
