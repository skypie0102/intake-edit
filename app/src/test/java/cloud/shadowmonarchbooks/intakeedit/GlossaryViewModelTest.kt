package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlossaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshPublishesRepositorySnapshot() = runTest(dispatcher) {
        val snapshot = glossarySnapshot(proposalStatus = "pending")
        val repository = FakeGlossaryRepository(snapshot, snapshot)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        assertEquals(snapshot, viewModel.uiState.value.snapshot)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun singleApprovalIsPooledWithoutRepositoryMutation() = runTest(dispatcher) {
        val snapshot = glossarySnapshot(proposalStatus = "pending")
        val repository = FakeGlossaryRepository(snapshot, snapshot)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })
        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()
        val proposal = snapshot.documents.pendingProposals.single()
        val entry = repository.proposalEntry(proposal, snapshot.documents)

        viewModel.poolApproval(proposal, entry)

        assertEquals(1, viewModel.uiState.value.pooledApprovalCount)
        assertFalse(repository.mutated)
        assertFalse(repository.commitDraftsCalled)
    }

    @Test
    fun commitPooledWritesBatchAndClearsPool() = runTest(dispatcher) {
        val before = glossarySnapshot(proposalStatus = "pending")
        val after = glossarySnapshot(proposalStatus = "approved")
        val repository = FakeGlossaryRepository(before, after)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })
        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()
        val proposal = before.documents.pendingProposals.single()
        val entry = repository.proposalEntry(proposal, before.documents)
        viewModel.poolApproval(proposal, entry)

        viewModel.commitPooled(RepoSettings(), "token")
        advanceUntilIdle()

        assertTrue(repository.commitDraftsCalled)
        assertEquals(setOf(proposal.id), repository.lastApprovalDrafts.keys)
        assertEquals(after, viewModel.uiState.value.snapshot)
        assertEquals(0, viewModel.uiState.value.pooledApprovalCount)
        assertEquals("1 pooled glossary approval(s) committed.", viewModel.uiState.value.notice)
    }

    @Test
    fun approveAllReloadsAuthoritativeGlossaryState() = runTest(dispatcher) {
        val before = glossarySnapshot(proposalStatus = "pending")
        val after = glossarySnapshot(proposalStatus = "approved")
        val repository = FakeGlossaryRepository(before, after)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()
        viewModel.approveAll(RepoSettings(), "token")
        advanceUntilIdle()

        assertTrue(repository.approveAllCalled)
        assertEquals(after, viewModel.uiState.value.snapshot)
        assertEquals("Glossary changes committed.", viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.busy)
    }
}

private fun glossarySnapshot(proposalStatus: String): GlossarySnapshot {
    val proposal = GlossaryProposal(
        id = "proposal-1",
        status = proposalStatus,
        action = "new_entry",
        reason = "test",
        section = "terms",
        sourceAliases = listOf("用語"),
        translatedName = "Term",
    )
    val documents = GlossaryDocuments(
        baseEntries = emptyList(),
        additions = emptyList(),
        governance = emptyMap(),
        proposals = listOf(proposal),
    )
    return GlossarySnapshot(
        base = emptyList(),
        additions = FileSnapshot("additions", "a", ""),
        governance = FileSnapshot("governance", "g", ""),
        proposals = FileSnapshot("proposals", "p", ""),
        documents = documents,
    )
}

private class FakeGlossaryRepository(
    private val initial: GlossarySnapshot,
    private val afterMutation: GlossarySnapshot,
) : GlossaryRepository {
    var approveAllCalled = false
    var commitDraftsCalled = false
    var mutated = false
    var lastApprovalDrafts: Map<String, GlossaryEntry> = emptyMap()

    override suspend fun load(): GlossarySnapshot = if (mutated) afterMutation else initial

    override fun proposalEntry(proposal: GlossaryProposal, documents: GlossaryDocuments): GlossaryEntry =
        GlossaryEntry(
            id = "terms:${proposal.sourceAliases.joinToString(" / ")}",
            section = proposal.section ?: "terms",
            sourceAliases = proposal.sourceAliases,
            translatedName = proposal.translatedName.orEmpty(),
        )

    override suspend fun saveApprovedEntry(state: GlossarySnapshot, entry: GlossaryEntry, allowNew: Boolean) {
        mutated = true
    }

    override suspend fun saveProposal(state: GlossarySnapshot, proposal: GlossaryProposal) {
        mutated = true
    }

    override suspend fun commitApprovalDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
    ) {
        commitDraftsCalled = true
        lastApprovalDrafts = approvalDrafts
        mutated = true
    }

    override suspend fun approveAllSuggestions(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
    ) {
        approveAllCalled = true
        lastApprovalDrafts = approvalDrafts
        mutated = true
    }
}
