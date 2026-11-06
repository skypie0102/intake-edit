package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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

/** Load one coherent branch snapshot, with bounded independent requests and off-main parsing. */
internal suspend fun loadGlossarySnapshot(
    readHead: suspend () -> String,
    readFile: suspend (String, String) -> FileSnapshot,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
): GlossarySnapshot = coroutineScope {
    val ref = readHead()
    val paths = GLOSSARY_BASE_PATHS + listOf(
        GLOSSARY_ADDITIONS_PATH, GLOSSARY_GOVERNANCE_PATH, GLOSSARY_PROPOSALS_PATH,
    )
    val permits = Semaphore(4)
    val files = paths.map { path -> async { permits.withPermit { readFile(path, ref) } } }.awaitAll()
    withContext(parseDispatcher) {
        val base = files.take(GLOSSARY_BASE_PATHS.size)
        val additions = files[GLOSSARY_BASE_PATHS.size]
        val governance = files[GLOSSARY_BASE_PATHS.size + 1]
        val proposals = files[GLOSSARY_BASE_PATHS.size + 2]
        val documents = GlossaryDocuments(
            baseEntries = base.flatMap { GlossaryParser.parseBaseFile(it.content) },
            additions = GlossaryParser.parseAdditions(additions.content),
            governance = GlossaryParser.parseGovernance(governance.content),
            proposals = GlossaryParser.parseProposals(proposals.content),
        )
        // Build expensive derived data here instead of during the first Compose frame.
        documents.effectiveEntries
        documents.pendingProposals
        GlossarySnapshot(base, additions, governance, proposals, documents)
    }
}

internal class GitHubGlossaryRepository(
    settings: RepoSettings,
    token: String,
) : GlossaryRepository {
    private val client = GitHubApi(settings, token)

    override suspend fun load(): GlossarySnapshot = loadGlossarySnapshot(
        readHead = { client.getBranchHead().sha },
        readFile = { path, ref -> client.getFileAtRef(path, ref) },
    )

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
