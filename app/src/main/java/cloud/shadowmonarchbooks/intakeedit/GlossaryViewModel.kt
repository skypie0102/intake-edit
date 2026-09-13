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
)

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
                _uiState.update { it.copy(snapshot = snapshot, busy = false) }
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

    fun approveAll(settings: RepoSettings, token: String) {
        mutate(settings, token) { repository, snapshot ->
            repository.approveAllSuggestions(snapshot)
        }
    }

    fun approveProposal(
        proposal: GlossaryProposal,
        entry: GlossaryEntry,
        settings: RepoSettings,
        token: String,
    ) {
        mutate(settings, token) { repository, snapshot ->
            repository.saveApprovedEntry(snapshot, entry, proposal.action == "new_entry")
            repository.saveProposal(snapshot, proposal.copy(status = "approved"))
        }
    }

    fun rejectProposal(proposal: GlossaryProposal, settings: RepoSettings, token: String) {
        mutate(settings, token) { repository, snapshot ->
            repository.saveProposal(snapshot, proposal.copy(status = "rejected"))
        }
    }

    fun saveProposalEdit(
        proposal: GlossaryProposal,
        edited: GlossaryEntry,
        approve: Boolean,
        settings: RepoSettings,
        token: String,
    ) {
        mutate(settings, token) { repository, snapshot ->
            if (approve) {
                repository.saveApprovedEntry(snapshot, edited, proposal.action == "new_entry")
            }
            val updatedProposal = proposal.copy(
                status = if (approve) "approved" else proposal.status,
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
                _uiState.update {
                    it.copy(
                        snapshot = refreshed,
                        busy = false,
                        notice = "Glossary changes committed.",
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
