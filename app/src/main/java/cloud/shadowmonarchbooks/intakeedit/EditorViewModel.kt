package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

private const val EDITOR_DRAFT_SAVE_DEBOUNCE_MS = 650L

internal data class EditorUiState(
    val open: OpenChapter? = null,
    val loadingPath: String? = null,
    val actionInProgress: Boolean = false,
    val notice: String? = null,
)

internal sealed interface EditorEvent {
    data object RefreshChapterList : EditorEvent
    data object ReturnChapterList : EditorEvent
}

internal class EditorViewModel(
    private val draftRepository: DraftRepository,
    private val repositoryFactory: ChapterRepositoryFactory = DefaultChapterRepositoryFactory,
) : ViewModel() {
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
                val repository = repositoryFactory.create(settings, token)
                val remoteChapter = repository.loadChapter(file)
                val draft = draftRepository.load(file.path)
                val opened = if (draft?.baseSha == remoteChapter.remote.sha) {
                    repository.restoreRaw(remoteChapter, draft.raw)
                } else {
                    remoteChapter
                }
                latest = opened
                touched = false
                _uiState.update {
                    it.copy(
                        open = opened,
                        loadingPath = null,
                        notice = if (draft != null && draft.baseSha != remoteChapter.remote.sha) {
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
            draftRepository.persist(next)
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
                draftRepository.persist(current)
            }
        } else {
            pending?.cancel()
        }
    }

    fun commit(next: OpenChapter, tagForQa: Boolean, settings: RepoSettings, token: String) {
        if (token.isBlank() || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, notice = null) }
            try {
                draftSaveJob?.cancelAndJoin()
                draftSaveJob = null
                draftFlushJob?.join()
                draftRepository.persist(next)
                val repository = repositoryFactory.create(settings, token)
                val committedResult = repository.commitChapter(next, tagForQa)
                draftRepository.delete(next.file.path)
                touched = false
                if (tagForQa) {
                    latest = null
                    _uiState.update { it.copy(open = null, actionInProgress = false, notice = null) }
                    eventChannel.send(EditorEvent.ReturnChapterList)
                } else {
                    val restored = repository.restoreRaw(next, committedResult.remote.content)
                    val committed = restored.copy(
                        remote = committedResult.remote,
                        endnoteProposals = committedResult.endnoteProposals,
                        approved = false,
                    )
                    latest = committed
                    _uiState.update {
                        it.copy(
                            open = committed,
                            actionInProgress = false,
                            notice = "Committed as Pending Review.",
                        )
                    }
                    eventChannel.send(EditorEvent.RefreshChapterList)
                }
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
        if (next.qa == null || token.isBlank() || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, notice = null) }
            try {
                val repository = repositoryFactory.create(settings, token)
                val updated = repository.overrideQa(next, findingId, reason)
                latest = updated
                _uiState.update {
                    it.copy(
                        open = updated,
                        actionInProgress = false,
                        notice = "QA finding overridden.",
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        actionInProgress = false,
                        notice = t.message ?: "Could not override QA finding.",
                    )
                }
            }
        }
    }

    fun resolveQa(next: OpenChapter, findingId: String, settings: RepoSettings, token: String) {
        if (next.qa == null || token.isBlank() || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, notice = null) }
            try {
                val repository = repositoryFactory.create(settings, token)
                val updated = repository.resolveQa(next, findingId)
                latest = updated
                _uiState.update {
                    it.copy(
                        open = updated,
                        actionInProgress = false,
                        notice = "QA finding resolved.",
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        actionInProgress = false,
                        notice = t.message ?: "Could not resolve QA finding.",
                    )
                }
            }
        }
    }
}

internal class EditorViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(EditorViewModel::class.java))
        return EditorViewModel(
            draftRepository = LocalDraftRepository(applicationContext),
            repositoryFactory = DefaultChapterRepositoryFactory,
        ) as T
    }
}
