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

internal class ChapterListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChapterListUiState())
    val uiState: StateFlow<ChapterListUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private val progressConcurrency = Semaphore(4)

    fun refresh(settings: RepoSettings, token: String) {
        if (token.isBlank()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val client = GitHubApi(settings, token)
            _uiState.update { it.copy(refreshing = true, notice = null) }
            try {
                val listed = client.listIntakeFiles()
                val listedPaths = listed.mapTo(hashSetOf()) { it.path }
                _uiState.update { state ->
                    state.copy(
                        files = listed,
                        progressByPath = state.progressByPath.filterKeys { it in listedPaths },
                    )
                }
                listed.forEach { file ->
                    launch {
                        progressConcurrency.withPermit {
                            runCatching { loadChapterProgress(client, file) }
                                .onSuccess { progress ->
                                    _uiState.update { state ->
                                        state.copy(progressByPath = state.progressByPath + (file.path to progress))
                                    }
                                }
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(notice = t.message ?: "Could not refresh chapter list.")
                }
            } finally {
                _uiState.update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun loadChapterProgress(client: GitHubApi, file: ChapterFile): ChapterProgress {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val qa = runCatching { loadQa(client, file) }.getOrNull()
        val editorSha = QaFindingsParser.sha256(remote.content)
        return ChapterProgress(
            englishSupplied = document.englishSupplied,
            englishTotal = document.englishTotal,
            editorReviewComplete = document.editorReviewComplete,
            qaActive = qa?.document?.active(editorSha)?.size ?: 0,
            qaTotal = qa?.document?.findings?.size ?: 0,
        )
    }

    private suspend fun loadQa(client: GitHubApi, file: ChapterFile): QaFindingsSnapshot? {
        val path = QaFindingsParser.path(file.volume, file.chapter)
        val snapshot = client.getFileOrNull(path) ?: return null
        return QaFindingsSnapshot(path, snapshot.sha, snapshot.content, QaFindingsParser.parse(snapshot.content))
    }
}
