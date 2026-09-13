package cloud.shadowmonarchbooks.intakeedit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EDITOR_DRAFT_SAVE_DEBOUNCE_MS = 650L

internal data class EditorUiState(
    val open: OpenChapter? = null,
    val loadingPath: String? = null,
    val actionInProgress: Boolean = false,
    val notice: String? = null,
)

internal sealed interface EditorEvent {
    data object ReturnHome : EditorEvent
}

internal class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val draftStore = DraftStore(application)
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var latest: OpenChapter? = null
    private var touched = false
    private var draftSaveJob: Job? = null
    private var draftFlushJob: Job? = null

    fun chapterForDisplay(): OpenChapter? = latest ?: _uiState.value.open

    fun open(file: ChapterFile, settings: RepoSettings, token: String) {
        if (token.isBlank() || _uiState.value.loadingPath != null || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingPath = file.path, notice = null) }
            try {
                draftFlushJob?.join()
                val client = GitHubApi(settings, token)
                val remote = client.getFile(file.path)
                val draft = withContext(Dispatchers.IO) { draftStore.load(file.path) }
                val raw = if (draft?.baseSha == remote.sha) draft.raw else remote.content
                val document = IntakeParser.parse(raw)
                val qa = runCatching { loadQa(client, file) }.getOrNull()
                val opened = OpenChapter(file, remote, raw, document, qa)
                latest = opened
                touched = false
                _uiState.update {
                    it.copy(
                        open = opened,
                        loadingPath = null,
                        notice = if (draft != null && draft.baseSha != remote.sha) {
                            "A local draft exists for an older GitHub revision. The latest remote copy was opened to avoid an unsafe overwrite."
                        } else null,
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        loadingPath = null,
                        notice = t.message ?: "Could not open ${file.path}.",
                    )
                }
            }
        }
    }

    fun onDraft(next: OpenChapter) {
        latest = next
        touched = true
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(EDITOR_DRAFT_SAVE_DEBOUNCE_MS)
            persistDraft(next)
        }
    }

    fun closeEditor() {
        val current = latest ?: _uiState.value.open
        val shouldPersist = touched
        val pending = draftSaveJob
        draftSaveJob = null
        latest = null
        touched = false
        _uiState.update { it.copy(open = null, actionInProgress = false) }
        if (current != null && shouldPersist) {
            draftFlushJob = viewModelScope.launch {
                pending?.cancelAndJoin()
                persistDraft(current)
            }
        } else {
            pending?.cancel()
        }
    }

    fun commit(next: OpenChapter, markReviewed: Boolean, settings: RepoSettings, token: String) {
        if (token.isBlank() || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, notice = null) }
            try {
                draftSaveJob?.cancelAndJoin()
                draftSaveJob = null
                draftFlushJob?.join()
                persistDraft(next)
                val document = if (markReviewed) {
                    require(next.document.englishSupplied == next.document.englishTotal) {
                        "Supply English for every paragraph before marking editor review complete."
                    }
                    next.document.copy(editorReviewComplete = true)
                } else next.document
                val raw = IntakeParser.patchDocument(next.raw, document)
                IntakeParser.validate(raw).getOrThrow()
                val client = GitHubApi(settings, token)
                client.updateFile(
                    next.file.path,
                    next.remote.sha,
                    raw,
                    "edit: revise ch_${next.file.chapter.toString().padStart(4, '0')} English",
                )
                withContext(Dispatchers.IO) { draftStore.delete(next.file.path) }
                latest = null
                touched = false
                _uiState.update { it.copy(open = null, actionInProgress = false, notice = null) }
                eventChannel.send(EditorEvent.ReturnHome)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        actionInProgress = false,
                        notice = t.message ?: "Commit failed.",
                    )
                }
            }
        }
    }

    fun overrideQa(next: OpenChapter, findingId: String, reason: String, settings: RepoSettings, token: String) {
        val snapshot = next.qa ?: return
        if (token.isBlank() || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, notice = null) }
            try {
                val client = GitHubApi(settings, token)
                val override = QaOverride(
                    reason,
                    Instant.now().toString(),
                    client.verifyUser(),
                    QaFindingsParser.sha256(next.raw),
                )
                val document = snapshot.document.withOverride(findingId, override)
                val raw = QaFindingsParser.serialize(document)
                val sha = client.updateFile(snapshot.path, snapshot.sha, raw, "qa: override $findingId")
                val updated = next.copy(
                    qa = snapshot.copy(
                        sha = sha.ifBlank { snapshot.sha },
                        raw = raw,
                        document = document,
                    ),
                )
                latest = updated
                _uiState.update {
                    it.copy(
                        open = updated,
                        actionInProgress = false,
                        notice = "QA override committed.",
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        actionInProgress = false,
                        notice = t.message ?: "Could not commit QA override.",
                    )
                }
            }
        }
    }

    private suspend fun persistDraft(chapter: OpenChapter) {
        withContext(Dispatchers.IO) {
            if (chapter.raw == chapter.remote.content) {
                draftStore.delete(chapter.file.path)
            } else {
                draftStore.save(chapter.file.path, chapter.remote.sha, chapter.raw)
            }
        }
    }

    private suspend fun loadQa(client: GitHubApi, file: ChapterFile): QaFindingsSnapshot? {
        val path = QaFindingsParser.path(file.volume, file.chapter)
        val snapshot = client.getFileOrNull(path) ?: return null
        return QaFindingsSnapshot(path, snapshot.sha, snapshot.content, QaFindingsParser.parse(snapshot.content))
    }
}
