package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val recoveryDrafts: List<LocalDraft> = emptyList(),
)

internal sealed interface EditorEvent {
    data object RefreshChapterList : EditorEvent
    data object ReturnChapterList : EditorEvent
}

internal class EditorViewModel(
    private val draftRepository: DraftRepository,
    private val repositoryFactory: ChapterRepositoryFactory = DefaultChapterRepositoryFactory,
    private val onTaggedForQa: (RepoSettings, ChapterFile) -> Unit = { _, _ -> },
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var latest: OpenChapter? = null
    private var touched = false
    private var activeDraftSettings: RepoSettings? = null
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
                val draft = draftRepository.load(settings, file.path)
                if (draft != null && draft.baseSha != remoteChapter.remote.sha) {
                    // Archive first, then clear the obsolete working copy; otherwise reopening
                    // would recreate a dismissed recovery entry from that stale copy.
                    draftRepository.archive(settings, draft)
                    draftRepository.delete(settings, file.path)
                }
                val recoveryDrafts = draftRepository.archived(settings, file.path)
                val opened = if (draft?.baseSha == remoteChapter.remote.sha) {
                    try {
                        withContext(parseDispatcher) { repository.restoreRaw(remoteChapter, draft.raw) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        remoteChapter.copy(raw = draft.raw, approved = false, rawNeedsRepair = true)
                    }
                } else {
                    remoteChapter
                }
                latest = opened
                activeDraftSettings = settings
                touched = false
                _uiState.update {
                    it.copy(
                        open = opened,
                        recoveryDrafts = recoveryDrafts,
                        loadingPath = null,
                        notice = when {
                            opened.rawNeedsRepair -> "Your saved draft is incomplete. Repair it in Whole File mode; the saved text has been preserved."
                            draft != null && draft.baseSha != remoteChapter.remote.sha -> "An older local draft was preserved. Open Saved drafts to inspect or copy it."
                            recoveryDrafts.any { it.legacyUnassigned } -> "A draft from an older app version is available in Saved drafts. Its original project is unknown; inspect it before restoring."
                            else -> null
                        },
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
        val draftSettings = activeDraftSettings ?: return
        latest = next
        touched = true
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(EDITOR_DRAFT_SAVE_DEBOUNCE_MS)
            try {
                draftRepository.persist(draftSettings, next)
                _uiState.update { state ->
                    if (state.notice?.startsWith("Local draft save failed:") == true) state.copy(notice = null) else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(notice = "Local draft save failed: ${error.message ?: "unknown error"}. Keep the editor open and retry by editing or closing it.") }
            }
        }
    }

    fun dismissSavedDraft(draft: LocalDraft) {
        val chapter = latest ?: _uiState.value.open ?: return
        val settings = activeDraftSettings ?: return
        if (_uiState.value.actionInProgress ||
            draft.path != chapter.file.path ||
            draft !in _uiState.value.recoveryDrafts
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true) }
            try {
                draftRepository.dismissArchived(settings, draft)
                val remaining = draftRepository.archived(settings, chapter.file.path)
                _uiState.update {
                    it.copy(
                        recoveryDrafts = remaining,
                        actionInProgress = false,
                        notice = "Saved older draft dismissed from this device.",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        actionInProgress = false,
                        notice = "Could not dismiss saved draft: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun closeEditor() {
        if (_uiState.value.actionInProgress) return
        val current = latest ?: _uiState.value.open
        val shouldPersist = touched
        val draftSettings = activeDraftSettings
        val pending = draftSaveJob
        draftSaveJob = null
        if (current != null && shouldPersist && draftSettings != null) {
            _uiState.update { it.copy(actionInProgress = true) }
            draftFlushJob = viewModelScope.launch {
                try {
                    pending?.cancelAndJoin()
                    draftRepository.persist(draftSettings, current)
                    latest = null
                    activeDraftSettings = null
                    touched = false
                    _uiState.update { it.copy(open = null, actionInProgress = false, notice = null, recoveryDrafts = emptyList()) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _uiState.update { it.copy(actionInProgress = false, notice = "Local draft save failed: ${error.message ?: "unknown error"}. Your edits remain open; try again.") }
                }
            }
        } else {
            pending?.cancel()
            latest = null
            activeDraftSettings = null
            touched = false
            _uiState.update { it.copy(open = null, actionInProgress = false, recoveryDrafts = emptyList()) }
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
                draftRepository.persist(settings, next)
                val repository = repositoryFactory.create(settings, token)
                val committedResult = repository.commitChapter(next, tagForQa)
                if (tagForQa) runCatching { onTaggedForQa(settings, next.file) }
                val cleanupWarning = try {
                    draftRepository.delete(settings, next.file.path)
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "The local draft could not be removed; it remains on this device."
                }
                touched = false
                if (tagForQa) {
                    latest = null
                    _uiState.update { it.copy(open = null, actionInProgress = false, notice = cleanupWarning?.let { message -> "Chapter committed. $message" }) }
                    eventChannel.send(EditorEvent.ReturnChapterList)
                } else {
                    val restored = withContext(parseDispatcher) { repository.restoreRaw(next, committedResult.remote.content) }
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
                            notice = listOfNotNull("Committed as Pending Review.", cleanupWarning).joinToString(" "),
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

    fun approve(next: OpenChapter, settings: RepoSettings, token: String) {
        if (token.isBlank() || _uiState.value.actionInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, notice = null) }
            try {
                draftSaveJob?.cancelAndJoin()
                draftSaveJob = null
                draftFlushJob?.join()
                val repository = repositoryFactory.create(settings, token)
                repository.approveChapter(next)
                val cleanupWarning = try {
                    draftRepository.delete(settings, next.file.path)
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "The local draft could not be removed; it remains on this device."
                }
                latest = null
                touched = false
                _uiState.update {
                    it.copy(
                        open = null,
                        actionInProgress = false,
                        notice = listOfNotNull("Approval workflow started. Refresh to check when the chapter becomes Approved.", cleanupWarning).joinToString(" "),
                    )
                }
                eventChannel.send(EditorEvent.ReturnChapterList)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        actionInProgress = false,
                        notice = t.message ?: "Could not start chapter approval.",
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
            onTaggedForQa = { settings, file ->
                PetStore(applicationContext).feed(settings, file)
                TranslationPetWidget.refreshAll(applicationContext)
            },
        ) as T
    }
}

