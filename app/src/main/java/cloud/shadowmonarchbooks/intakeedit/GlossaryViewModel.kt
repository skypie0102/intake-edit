package cloud.shadowmonarchbooks.intakeedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GlossaryUiState(
    val snapshot: GlossarySnapshot? = null,
    val busy: Boolean = false,
    val notice: String? = null,
    val approvalDrafts: Map<String, GlossaryEntry> = emptyMap(),
    val rejectionDraftIds: Set<String> = emptySet(),
) {
    val pooledApprovalCount: Int get() = approvalDrafts.size
    val pooledDecisionCount: Int get() = approvalDrafts.size + rejectionDraftIds.size
}

internal class GlossaryViewModel() : ViewModel() {
    private var repositoryFactory: GlossaryRepositoryFactory = DefaultGlossaryRepositoryFactory
    private val _uiState = MutableStateFlow(GlossaryUiState())
    val uiState: StateFlow<GlossaryUiState> = _uiState.asStateFlow()
    private var activeJob: Job? = null

    internal constructor(repositoryFactory: GlossaryRepositoryFactory) : this() {
        this.repositoryFactory = repositoryFactory
    }

    fun refresh(settings: RepoSettings, token: String) {
        if (token.isBlank() || _uiState.value.busy) return
        activeJob = viewModelScope.launch {
            val repository = repositoryFactory.create(settings, token)
            _uiState.update { it.copy(busy = true, notice = null) }
            try {
                val snapshot = repository.load()
                val pendingIds = snapshot.documents.pendingProposals.mapTo(mutableSetOf()) { it.id }
                _uiState.update { current ->
                    current.copy(
                        snapshot = snapshot,
                        busy = false,
                        approvalDrafts = current.approvalDrafts.filterKeys { it in pendingIds },
                        rejectionDraftIds = current.rejectionDraftIds.filterTo(mutableSetOf()) { it in pendingIds },
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        notice = t.message ?: "Could not load glossary.",
                    )
                }
            }
        }
    }

    fun proposalEntry(proposal: GlossaryProposal, settings: RepoSettings, token: String): GlossaryEntry? {
        val documents = _uiState.value.snapshot?.documents ?: return null
        return repositoryFactory.create(settings, token).proposalEntry(proposal, documents)
    }

    fun poolApproval(proposal: GlossaryProposal, entry: GlossaryEntry) {
        val pending = _uiState.value.snapshot?.documents?.pendingProposals?.any { it.id == proposal.id } == true
        if (!pending) return
        _uiState.update {
            it.copy(
                approvalDrafts = it.approvalDrafts + (proposal.id to entry),
                rejectionDraftIds = it.rejectionDraftIds - proposal.id,
                notice = "Approval pooled. Commit when your review batch is ready.",
            )
        }
    }

    fun poolRejection(proposal: GlossaryProposal) {
        val pending = _uiState.value.snapshot?.documents?.pendingProposals?.any { it.id == proposal.id } == true
        if (!pending) return
        _uiState.update {
            it.copy(
                approvalDrafts = it.approvalDrafts - proposal.id,
                rejectionDraftIds = it.rejectionDraftIds + proposal.id,
                notice = "Rejection pooled. Commit when your review batch is ready.",
            )
        }
    }

    fun removePooledDecision(proposalId: String) {
        val state = _uiState.value
        if (proposalId !in state.approvalDrafts && proposalId !in state.rejectionDraftIds) return
        _uiState.update {
            it.copy(
                approvalDrafts = it.approvalDrafts - proposalId,
                rejectionDraftIds = it.rejectionDraftIds - proposalId,
                notice = "Decision removed from the commit pool.",
            )
        }
    }

    fun clearPooledDecisions() {
        val state = _uiState.value
        if (state.pooledDecisionCount == 0 || state.busy) return
        _uiState.update {
            it.copy(
                approvalDrafts = emptyMap(),
                rejectionDraftIds = emptySet(),
                notice = null,
            )
        }
    }

    fun commitPooled(settings: RepoSettings, token: String) {
        val approvals = _uiState.value.approvalDrafts
        val rejections = _uiState.value.rejectionDraftIds
        val decisionCount = approvals.size + rejections.size
        if (decisionCount == 0) return
        mutate(
            settings = settings,
            token = token,
            clearDecisionDrafts = true,
            successNotice = "$decisionCount pooled glossary decision(s) committed.",
        ) { repository, snapshot ->
            repository.commitDecisionDrafts(snapshot, approvals, rejections)
        }
    }

    fun approveAll(settings: RepoSettings, token: String) {
        val drafts = _uiState.value.approvalDrafts
        mutate(
            settings = settings,
            token = token,
            clearDecisionDrafts = true,
        ) { repository, snapshot ->
            repository.approveAllSuggestions(snapshot, drafts)
        }
    }

    fun saveProposalEdit(
        proposal: GlossaryProposal,
        edited: GlossaryEntry,
        approve: Boolean,
        settings: RepoSettings,
        token: String,
    ) {
        if (approve) {
            poolApproval(proposal, edited)
            return
        }
        if (proposal.id in _uiState.value.approvalDrafts) {
            _uiState.update { it.copy(approvalDrafts = it.approvalDrafts + (proposal.id to edited)) }
        }
        mutate(settings, token) { repository, snapshot ->
            val updatedProposal = proposal.copy(
                section = edited.section,
                sourceAliases = edited.sourceAliases,
                translatedName = edited.translatedName,
                gender = edited.gender,
                description = edited.description,
                recommendQaLock = edited.qaLock,
            )
            repository.saveProposal(snapshot, updatedProposal)
        }
    }

    fun saveApprovedEntry(entry: GlossaryEntry, allowNew: Boolean, settings: RepoSettings, token: String) {
        mutate(settings, token) { repository, snapshot ->
            repository.saveApprovedEntry(snapshot, entry, allowNew)
        }
    }

    private fun mutate(
        settings: RepoSettings,
        token: String,
        clearDecisionDrafts: Boolean = false,
        successNotice: String = "Glossary changes committed.",
        block: suspend (GlossaryRepository, GlossarySnapshot) -> Unit,
    ) {
        if (token.isBlank() || _uiState.value.busy) return
        val snapshot = _uiState.value.snapshot ?: return
        activeJob = viewModelScope.launch {
            val repository = repositoryFactory.create(settings, token)
            _uiState.update { it.copy(busy = true, notice = null) }
            try {
                block(repository, snapshot)
                val refreshed = repository.load()
                val pendingIds = refreshed.documents.pendingProposals.mapTo(mutableSetOf()) { it.id }
                _uiState.update { current ->
                    current.copy(
                        snapshot = refreshed,
                        busy = false,
                        notice = successNotice,
                        approvalDrafts = if (clearDecisionDrafts) {
                            emptyMap()
                        } else {
                            current.approvalDrafts.filterKeys { it in pendingIds }
                        },
                        rejectionDraftIds = if (clearDecisionDrafts) {
                            emptySet()
                        } else {
                            current.rejectionDraftIds.filterTo(mutableSetOf()) { it in pendingIds }
                        },
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        notice = t.message ?: "Glossary update failed.",
                    )
                }
            }
        }
    }
}
