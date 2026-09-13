package cloud.shadowmonarchbooks.intakeedit

private val GLOSSARY_BASE_PATHS = listOf(
    "glossary/full/characters.txt",
    "glossary/full/locations.txt",
    "glossary/full/nicknames.txt",
    "glossary/full/terms.txt",
    "glossary/full/honorifics.txt",
)
private const val GLOSSARY_ADDITIONS_PATH = "glossary/approved_additions.json"
private const val GLOSSARY_GOVERNANCE_PATH = "glossary/governance.json"
private const val GLOSSARY_PROPOSALS_PATH = "glossary/proposals.json"

internal data class GlossarySnapshot(
    val base: List<FileSnapshot>,
    val additions: FileSnapshot,
    val governance: FileSnapshot,
    val proposals: FileSnapshot,
    val documents: GlossaryDocuments,
)

internal interface GlossaryRepository {
    suspend fun load(): GlossarySnapshot
    fun proposalEntry(proposal: GlossaryProposal, documents: GlossaryDocuments): GlossaryEntry
    suspend fun saveApprovedEntry(state: GlossarySnapshot, entry: GlossaryEntry, allowNew: Boolean)
    suspend fun saveProposal(state: GlossarySnapshot, proposal: GlossaryProposal)
    suspend fun commitApprovalDrafts(state: GlossarySnapshot, approvalDrafts: Map<String, GlossaryEntry>)
    suspend fun commitDecisionDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
        rejectionDraftIds: Set<String>,
    ) {
        require(rejectionDraftIds.isEmpty()) { "This repository does not support pooled rejections." }
        commitApprovalDrafts(state, approvalDrafts)
    }
    suspend fun approveAllSuggestions(state: GlossarySnapshot, approvalDrafts: Map<String, GlossaryEntry> = emptyMap())
}

internal fun interface GlossaryRepositoryFactory {
    fun create(settings: RepoSettings, token: String): GlossaryRepository
}

internal object DefaultGlossaryRepositoryFactory : GlossaryRepositoryFactory {
    override fun create(settings: RepoSettings, token: String): GlossaryRepository =
        GitHubGlossaryRepository(settings, token)
}

internal class GitHubGlossaryRepository(
    settings: RepoSettings,
    token: String,
) : GlossaryRepository {
    private val client = GitHubApi(settings, token)

    override suspend fun load(): GlossarySnapshot {
        val base = GLOSSARY_BASE_PATHS.map { client.getFile(it) }
        val additions = client.getFile(GLOSSARY_ADDITIONS_PATH)
        val governance = client.getFile(GLOSSARY_GOVERNANCE_PATH)
        val proposals = client.getFile(GLOSSARY_PROPOSALS_PATH)
        val baseEntries = base.flatMap { GlossaryParser.parseBaseFile(it.content) }
        val documents = GlossaryDocuments(
            baseEntries = baseEntries,
            additions = GlossaryParser.parseAdditions(additions.content),
            governance = GlossaryParser.parseGovernance(governance.content),
            proposals = GlossaryParser.parseProposals(proposals.content),
        )
        return GlossarySnapshot(base, additions, governance, proposals, documents)
    }

    override suspend fun saveApprovedEntry(state: GlossarySnapshot, entry: GlossaryEntry, allowNew: Boolean) {
        val additionIndex = state.documents.additions.indexOfFirst { it.id == entry.id }
        val baseExists = state.documents.baseEntries.any { it.id == entry.id }
        when {
            additionIndex >= 0 -> {
                val next = state.documents.additions.toMutableList().also {
                    it[additionIndex] = entry.copy(origin = "editor-approved")
                }
                client.updateFile(
                    GLOSSARY_ADDITIONS_PATH,
                    state.additions.sha,
                    GlossaryParser.serializeAdditions(next),
                    "glossary: edit ${entry.id}",
                )
            }
            baseExists -> {
                val next = state.documents.governance + (entry.id to GlossaryParser.fullOverride(entry))
                client.updateFile(
                    GLOSSARY_GOVERNANCE_PATH,
                    state.governance.sha,
                    GlossaryParser.serializeGovernance(next),
                    "glossary: govern ${entry.id}",
                )
            }
            allowNew -> {
                GlossaryParser.ensureNewEntryAbsent(entry, state.documents.effectiveEntries)
                val next = state.documents.additions + entry.copy(origin = "editor-approved")
                client.updateFile(
                    GLOSSARY_ADDITIONS_PATH,
                    state.additions.sha,
                    GlossaryParser.serializeAdditions(next),
                    "glossary: add ${entry.id}",
                )
            }
            else -> error("Glossary entry no longer exists: ${entry.id}")
        }
    }

    override suspend fun saveProposal(state: GlossarySnapshot, proposal: GlossaryProposal) {
        val next = state.documents.proposals.map { if (it.id == proposal.id) proposal else it }
        client.updateFile(
            GLOSSARY_PROPOSALS_PATH,
            state.proposals.sha,
            GlossaryParser.serializeProposals(next),
            "glossary: update proposal ${proposal.id}",
        )
    }

    override suspend fun commitApprovalDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
    ) = commitDecisionDrafts(state, approvalDrafts, emptySet())

    override suspend fun commitDecisionDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
        rejectionDraftIds: Set<String>,
    ) {
        val decisionCount = approvalDrafts.size + rejectionDraftIds.size
        require(decisionCount > 0) { "There are no pooled glossary decisions to commit." }
        commitApprovals(
            state = state,
            approved = GlossaryActions.applyPendingDecisions(state.documents, approvalDrafts, rejectionDraftIds),
            message = "glossary: commit $decisionCount pooled suggestion decisions",
        )
    }

    override suspend fun approveAllSuggestions(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
    ) {
        val pendingCount = state.documents.pendingProposals.size
        require(pendingCount > 0) { "There are no pending glossary suggestions." }
        commitApprovals(
            state = state,
            approved = GlossaryActions.approvePending(state.documents, approvalDrafts, approveAll = true),
            message = "glossary: approve $pendingCount suggestions",
        )
    }

    private suspend fun commitApprovals(
        state: GlossarySnapshot,
        approved: GlossaryDocuments,
        message: String,
    ) {
        val additions = GlossaryParser.serializeAdditions(approved.additions)
        val governance = GlossaryParser.serializeGovernance(approved.governance)
        val proposals = GlossaryParser.serializeProposals(approved.proposals)
        val updates = buildList {
            if (additions != state.additions.content) {
                add(GitHubFileUpdate(GLOSSARY_ADDITIONS_PATH, state.additions.sha, additions))
            }
            if (governance != state.governance.content) {
                add(GitHubFileUpdate(GLOSSARY_GOVERNANCE_PATH, state.governance.sha, governance))
            }
            if (proposals != state.proposals.content) {
                add(GitHubFileUpdate(GLOSSARY_PROPOSALS_PATH, state.proposals.sha, proposals))
            }
        }
        require(updates.isNotEmpty()) { "No glossary files changed." }
        client.updateFilesAtomically(updates, message)
    }

    override fun proposalEntry(proposal: GlossaryProposal, documents: GlossaryDocuments): GlossaryEntry =
        GlossaryActions.proposalEntry(proposal, documents)
}
