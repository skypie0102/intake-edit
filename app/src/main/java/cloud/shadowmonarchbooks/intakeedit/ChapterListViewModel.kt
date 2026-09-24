package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    val bulkApproving: Boolean = false,
    val approvalRequestedPaths: Set<String> = emptySet(),
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

    fun approveAllReady(settings: RepoSettings, token: String) {
        if (token.isBlank() || _uiState.value.bulkApproving) return
        val snapshot = _uiState.value
        val ready = snapshot.files.filter { file ->
            file.path !in snapshot.approvalRequestedPaths &&
                snapshot.progressByPath[file.path]?.workflowState == ChapterWorkflowState.READY_FOR_APPROVAL
        }
        if (ready.isEmpty()) {
            _uiState.update { it.copy(notice = "No Ready chapters are waiting for approval.") }
            return
        }

        viewModelScope.launch {
            val repository = repositoryFactory.create(settings, token)
            val ordered = ready.sortedBy { it.chapter }
            val paths = ordered.mapTo(linkedSetOf()) { it.path }
            var dispatch: ApprovalDispatch? = null

            _uiState.update {
                it.copy(
                    bulkApproving = true,
                    notice = "Validating ${ordered.size} chapter(s) for one batch approval…",
                )
            }

            try {
                dispatch = repository.approveBatch(ordered)
                _uiState.update { state ->
                    state.copy(
                        approvalRequestedPaths = state.approvalRequestedPaths + paths,
                        notice = "Batch approval started for ${ordered.size} chapter(s). Waiting for one GitHub Actions run…",
                    )
                }

                val run = repository.waitForApproval(dispatch)
                if (run.successful) {
                    _uiState.update { state ->
                        val updatedProgress = state.progressByPath.toMutableMap()
                        ordered.forEach { file ->
                            val current = updatedProgress[file.path]
                            if (current != null) {
                                updatedProgress[file.path] = current.copy(approved = true)
                            }
                        }
                        state.copy(
                            progressByPath = updatedProgress,
                            approvalRequestedPaths = state.approvalRequestedPaths - paths,
                            notice = "Approved ${ordered.size} chapter(s) in one batch workflow.",
                        )
                    }
                } else {
                    val conclusion = run.conclusion ?: run.status
                    _uiState.update { state ->
                        state.copy(
                            approvalRequestedPaths = state.approvalRequestedPaths - paths,
                            notice = "Batch approval failed: workflow $conclusion.",
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = t.message ?: "batch approval failed"
                _uiState.update { state ->
                    state.copy(
                        // If dispatch succeeded but monitoring failed, keep the paths reserved
                        // until a refresh confirms the run result so a duplicate batch is not sent.
                        approvalRequestedPaths = if (dispatch == null) {
                            state.approvalRequestedPaths - paths
                        } else {
                            state.approvalRequestedPaths + paths
                        },
                        notice = if (dispatch == null) {
                            "Batch approval was not started: $message"
                        } else {
                            "Batch approval was dispatched, but its result could not be confirmed: $message Refresh before retrying."
                        },
                    )
                }
            } finally {
                _uiState.update { it.copy(bulkApproving = false) }
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
        val indexed = runCatching { repository.loadVolumeStatus(volume) }.getOrNull()
        if (indexed != null) {
            if (_uiState.value.selectedVolume != volume) return
            _uiState.update { state ->
                state.copy(
                    files = indexed.files,
                    progressByPath = indexed.progressByPath,
                    refreshing = indexed.refreshFiles.isNotEmpty(),
                    approvalRequestedPaths = state.approvalRequestedPaths.filterTo(mutableSetOf()) { path ->
                        indexed.progressByPath[path]?.workflowState == ChapterWorkflowState.READY_FOR_APPROVAL
                    },
                    notice = null,
                )
            }
            scheduleCacheSave(settings)
            if (indexed.refreshFiles.isNotEmpty()) {
                refreshChapterProgress(repository, settings, volume, indexed.refreshFiles)
            }
            return
        }

        _uiState.update { state ->
            state.copy(notice = "Workflow index unavailable; using slower compatibility refresh for Volume $volume.")
        }

        val listed = repository.listFiles(volume)
        val listedPaths = listed.mapTo(hashSetOf()) { it.path }
        _uiState.update { state ->
            state.copy(
                files = listed,
                // Once the remote file list is current, discard cached workflow states for
                // these chapters. A base editor fetch without its QA record can only tell us
                // that the chapter is editor-complete; publishing that partial state would
                // temporarily and incorrectly classify Ready-for-Approval chapters as Pending QA.
                progressByPath = state.progressByPath.filterKeys { it !in listedPaths },
                selectedVolume = volume,
                refreshing = true,
            )
        }

        refreshChapterProgress(repository, settings, volume, listed)
    }

    private suspend fun refreshChapterProgress(
        repository: ChapterRepository,
        settings: RepoSettings,
        volume: Int,
        files: List<ChapterFile>,
    ) {
        coroutineScope {
            files.forEach { file ->
                launch {
                    val loaded = progressConcurrency.withPermit {
                        try {
                            repository.loadBaseProgress(file)
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            _uiState.update { state ->
                                state.copy(notice = "Could not load Chapter ${file.chapter} status: ${t.message ?: "editor fetch failed"}")
                            }
                            null
                        }
                    } ?: return@launch

                    val qaCounts = progressConcurrency.withPermit {
                        try {
                            repository.loadQaCounts(file, loaded.document, loaded.editorContentSha256)
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            _uiState.update { state ->
                                state.copy(notice = "Could not load Chapter ${file.chapter} QA status: ${t.message ?: "QA fetch failed"}")
                            }
                            null
                        }
                    } ?: return@launch

                    if (_uiState.value.selectedVolume != volume) return@launch
                    _uiState.update { state ->
                        state.copy(
                            progressByPath = state.progressByPath + (
                                file.path to loaded.progress.copy(
                                    qaActive = qaCounts.active,
                                    qaTotal = qaCounts.total,
                                    qaReusable = qaCounts.reusable,
                                )
                            ),
                        )
                    }
                    scheduleCacheSave(settings)
                }
            }
        }

        if (_uiState.value.selectedVolume == volume) {
            _uiState.update { state ->
                state.copy(
                    refreshing = false,
                    approvalRequestedPaths = state.approvalRequestedPaths.filterTo(mutableSetOf()) { path ->
                        state.progressByPath[path]?.workflowState == ChapterWorkflowState.READY_FOR_APPROVAL
                    },
                )
            }
            scheduleCacheSave(settings)
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
