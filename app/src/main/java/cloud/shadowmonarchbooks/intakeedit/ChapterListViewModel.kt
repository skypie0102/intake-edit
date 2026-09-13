package cloud.shadowmonarchbooks.intakeedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class ChapterListUiState(
    val files: List<ChapterFile> = emptyList(),
    val progressByPath: Map<String, ChapterProgress> = emptyMap(),
    val refreshing: Boolean = false,
    val notice: String? = null,
)

internal class ChapterListViewModel() : ViewModel() {
    private var repositoryFactory: ChapterRepositoryFactory = DefaultChapterRepositoryFactory
    private val _uiState = MutableStateFlow(ChapterListUiState())
    val uiState: StateFlow<ChapterListUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private val progressConcurrency = Semaphore(4)

    internal constructor(repositoryFactory: ChapterRepositoryFactory) : this() {
        this.repositoryFactory = repositoryFactory
    }

    fun refresh(settings: RepoSettings, token: String) {
        if (token.isBlank()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val repository = repositoryFactory.create(settings, token)
            _uiState.update { it.copy(refreshing = true, notice = null) }
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
                listed.forEach { file ->
                    launch {
                        val loaded = progressConcurrency.withPermit {
                            runCatching { repository.loadBaseProgress(file) }.getOrNull()
                        } ?: return@launch

                        _uiState.update { state ->
                            state.copy(progressByPath = state.progressByPath + (file.path to loaded.progress))
                        }

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
}
