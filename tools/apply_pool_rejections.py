from pathlib import Path

def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"{label}: expected source block not found")
    return text.replace(old, new, 1)

# GlossaryActions
p = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GlossaryActions.kt")
s = p.read_text()
marker = '''    fun approveAllPending(documents: GlossaryDocuments): GlossaryDocuments =
        approvePending(documents, approvalDrafts = emptyMap(), approveAll = true)
'''
replacement = '''    fun applyPendingDecisions(
        documents: GlossaryDocuments,
        approvalDrafts: Map<String, GlossaryEntry>,
        rejectionDraftIds: Set<String>,
    ): GlossaryDocuments {
        require(approvalDrafts.keys.intersect(rejectionDraftIds).isEmpty()) {
            "A glossary suggestion cannot be both approved and rejected in the same batch."
        }
        val pendingIds = documents.pendingProposals.mapTo(mutableSetOf()) { it.id }
        val decisionIds = approvalDrafts.keys + rejectionDraftIds
        require(decisionIds.isNotEmpty()) { "There are no pooled glossary decisions to commit." }
        require(decisionIds.all { it in pendingIds }) {
            "One or more pooled glossary decisions are no longer pending."
        }

        var result = if (approvalDrafts.isNotEmpty()) {
            approvePending(documents, approvalDrafts, approveAll = false)
        } else {
            documents
        }
        if (rejectionDraftIds.isNotEmpty()) {
            result = result.copy(
                proposals = result.proposals.map { proposal ->
                    if (proposal.id in rejectionDraftIds) proposal.copy(status = "rejected") else proposal
                },
            )
        }
        return result
    }

    fun approveAllPending(documents: GlossaryDocuments): GlossaryDocuments =
        approvePending(documents, approvalDrafts = emptyMap(), approveAll = true)
'''
p.write_text(replace_once(s, marker, replacement, "GlossaryActions"))

# Repository
p = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GlossaryRepository.kt")
s = p.read_text()
old = '''    suspend fun commitApprovalDrafts(state: GlossarySnapshot, approvalDrafts: Map<String, GlossaryEntry>)
    suspend fun approveAllSuggestions(state: GlossarySnapshot, approvalDrafts: Map<String, GlossaryEntry> = emptyMap())
'''
new = '''    suspend fun commitApprovalDrafts(state: GlossarySnapshot, approvalDrafts: Map<String, GlossaryEntry>)
    suspend fun commitDecisionDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
        rejectionDraftIds: Set<String>,
    ) {
        require(rejectionDraftIds.isEmpty()) { "This repository does not support pooled rejections." }
        commitApprovalDrafts(state, approvalDrafts)
    }
    suspend fun approveAllSuggestions(state: GlossarySnapshot, approvalDrafts: Map<String, GlossaryEntry> = emptyMap())
'''
s = replace_once(s, old, new, "GlossaryRepository interface")
old = '''    override suspend fun commitApprovalDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
    ) {
        require(approvalDrafts.isNotEmpty()) { "There are no pooled glossary approvals to commit." }
        commitApprovals(
            state = state,
            approved = GlossaryActions.approvePending(state.documents, approvalDrafts, approveAll = false),
            message = "glossary: approve ${approvalDrafts.size} pooled suggestions",
        )
    }
'''
new = '''    override suspend fun commitApprovalDrafts(
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
'''
p.write_text(replace_once(s, old, new, "GlossaryRepository implementation"))

# ViewModel
p = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GlossaryViewModel.kt")
s = p.read_text()
old = '''    val approvalDrafts: Map<String, GlossaryEntry> = emptyMap(),
) {
    val pooledApprovalCount: Int get() = approvalDrafts.size
}
'''
new = '''    val approvalDrafts: Map<String, GlossaryEntry> = emptyMap(),
    val rejectionDraftIds: Set<String> = emptySet(),
) {
    val pooledApprovalCount: Int get() = approvalDrafts.size
    val pooledDecisionCount: Int get() = approvalDrafts.size + rejectionDraftIds.size
}
'''
s = replace_once(s, old, new, "GlossaryUiState")
old = '''                        approvalDrafts = current.approvalDrafts.filterKeys { it in pendingIds },
'''
new = '''                        approvalDrafts = current.approvalDrafts.filterKeys { it in pendingIds },
                        rejectionDraftIds = current.rejectionDraftIds.filterTo(mutableSetOf()) { it in pendingIds },
'''
s = replace_once(s, old, new, "refresh draft filtering")
old = '''        _uiState.update {
            it.copy(
                approvalDrafts = it.approvalDrafts + (proposal.id to entry),
                notice = "Approval pooled. Commit when your review batch is ready.",
            )
        }
    }

    fun removePooledApproval(proposalId: String) {
        if (proposalId !in _uiState.value.approvalDrafts) return
        _uiState.update {
            it.copy(
                approvalDrafts = it.approvalDrafts - proposalId,
                notice = "Approval removed from the commit pool.",
            )
        }
    }

    fun commitPooled(settings: RepoSettings, token: String) {
        val drafts = _uiState.value.approvalDrafts
        if (drafts.isEmpty()) return
        mutate(
            settings = settings,
            token = token,
            clearApprovalDrafts = true,
            successNotice = "${drafts.size} pooled glossary approval(s) committed.",
        ) { repository, snapshot ->
            repository.commitApprovalDrafts(snapshot, drafts)
        }
    }
'''
new = '''        _uiState.update {
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
'''
s = replace_once(s, old, new, "pooling methods")
s = replace_once(s, '''            clearApprovalDrafts = true,
''', '''            clearDecisionDrafts = true,
''', "approveAll clear flag")
old = '''    fun rejectProposal(proposal: GlossaryProposal, settings: RepoSettings, token: String) {
        mutate(settings, token) { repository, snapshot ->
            repository.saveProposal(snapshot, proposal.copy(status = "rejected"))
        }
    }

'''
s = replace_once(s, old, "", "remove immediate reject")
s = replace_once(s, '''        clearApprovalDrafts: Boolean = false,
''', '''        clearDecisionDrafts: Boolean = false,
''', "mutate param")
old = '''                        approvalDrafts = if (clearApprovalDrafts) {
                            emptyMap()
                        } else {
                            current.approvalDrafts.filterKeys { it in pendingIds }
                        },
'''
new = '''                        approvalDrafts = if (clearDecisionDrafts) {
                            emptyMap()
                        } else {
                            current.approvalDrafts.filterKeys { it in pendingIds }
                        },
                        rejectionDraftIds = if (clearDecisionDrafts) {
                            emptySet()
                        } else {
                            current.rejectionDraftIds.filterTo(mutableSetOf()) { it in pendingIds }
                        },
'''
s = replace_once(s, old, new, "mutate draft cleanup")
p.write_text(s)

# Screen
p = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GlossaryWorkspaceScreen.kt")
s = p.read_text()
s = replace_once(s, "state.pooledApprovalCount > 0", "state.pooledDecisionCount > 0", "commit enabled")
s = replace_once(s, 'Text("Commit (${state.pooledApprovalCount})")', 'Text("Commit (${state.pooledDecisionCount})")', "commit label")
old = '''                                    val pooledEntry = state.approvalDrafts[proposal.id]
                                    GlossaryProposalCard(
                                        proposal = proposal,
                                        resolvedEntry = pooledEntry ?: resolvedEntry,
                                        pooled = pooledEntry != null,
                                        busy = state.busy,
                                        onEdit = { editingProposal = proposal },
                                        onApprove = {
                                            resolvedEntry?.let { entry -> glossaryViewModel.poolApproval(proposal, entry) }
                                        },
                                        onRemoveFromPool = { glossaryViewModel.removePooledApproval(proposal.id) },
                                        onReject = {
                                            glossaryViewModel.rejectProposal(proposal, settings, token)
                                        },
                                    )
'''
new = '''                                    val pooledEntry = state.approvalDrafts[proposal.id]
                                    val rejectionPooled = proposal.id in state.rejectionDraftIds
                                    GlossaryProposalCard(
                                        proposal = proposal,
                                        resolvedEntry = pooledEntry ?: resolvedEntry,
                                        approvalPooled = pooledEntry != null,
                                        rejectionPooled = rejectionPooled,
                                        busy = state.busy,
                                        onEdit = { editingProposal = proposal },
                                        onApprove = {
                                            resolvedEntry?.let { entry -> glossaryViewModel.poolApproval(proposal, entry) }
                                        },
                                        onRemoveFromPool = { glossaryViewModel.removePooledDecision(proposal.id) },
                                        onReject = { glossaryViewModel.poolRejection(proposal) },
                                    )
'''
s = replace_once(s, old, new, "card call")
s = replace_once(s, '''    pooled: Boolean,
''', '''    approvalPooled: Boolean,
    rejectionPooled: Boolean,
''', "card params")
old = '''            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
                TextButton(onClick = onReject, enabled = !busy) { Text("Reject") }
                if (pooled) TextButton(onClick = onRemoveFromPool, enabled = !busy) { Text("Undo") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onApprove,
                    enabled = !busy && !pooled && resolvedEntry != null,
                ) { Text(if (pooled) "Pooled" else "Approve") }
            }
'''
new = '''            val pooled = approvalPooled || rejectionPooled
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
                TextButton(onClick = onReject, enabled = !busy && !rejectionPooled) {
                    Text(if (rejectionPooled) "Rejected" else "Reject")
                }
                if (pooled) TextButton(onClick = onRemoveFromPool, enabled = !busy) { Text("Undo") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onApprove,
                    enabled = !busy && !approvalPooled && resolvedEntry != null,
                ) { Text(if (approvalPooled) "Pooled" else "Approve") }
            }
'''
s = replace_once(s, old, new, "card buttons")
p.write_text(s)

# ViewModel tests
p = Path("app/src/test/java/cloud/shadowmonarchbooks/intakeedit/GlossaryViewModelTest.kt")
s = p.read_text()
insertion = '''
    @Test
    fun singleRejectionIsPooledWithoutRepositoryMutation() = runTest(dispatcher) {
        val snapshot = glossarySnapshot(proposalStatus = "pending")
        val repository = FakeGlossaryRepository(snapshot, snapshot)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })
        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()
        val proposal = snapshot.documents.pendingProposals.single()

        viewModel.poolRejection(proposal)

        assertEquals(1, viewModel.uiState.value.pooledDecisionCount)
        assertTrue(proposal.id in viewModel.uiState.value.rejectionDraftIds)
        assertFalse(repository.mutated)
    }

    @Test
    fun pooledRejectionCommitsOnlyWhenCommitButtonFlowRuns() = runTest(dispatcher) {
        val before = glossarySnapshot(proposalStatus = "pending")
        val after = glossarySnapshot(proposalStatus = "rejected")
        val repository = FakeGlossaryRepository(before, after)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })
        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()
        val proposal = before.documents.pendingProposals.single()
        viewModel.poolRejection(proposal)

        viewModel.commitPooled(RepoSettings(), "token")
        advanceUntilIdle()

        assertTrue(repository.commitDecisionsCalled)
        assertEquals(setOf(proposal.id), repository.lastRejectionDraftIds)
        assertEquals(after, viewModel.uiState.value.snapshot)
        assertEquals(0, viewModel.uiState.value.pooledDecisionCount)
        assertEquals("1 pooled glossary decision(s) committed.", viewModel.uiState.value.notice)
    }

'''
anchor = '''    @Test
    fun approveAllReloadsAuthoritativeGlossaryState()'''
s = replace_once(s, anchor, insertion + anchor, "insert VM tests")
s = replace_once(s, '''    var commitDraftsCalled = false
''', '''    var commitDraftsCalled = false
    var commitDecisionsCalled = false
''', "fake flag")
s = replace_once(s, '''    var lastApprovalDrafts: Map<String, GlossaryEntry> = emptyMap()
''', '''    var lastApprovalDrafts: Map<String, GlossaryEntry> = emptyMap()
    var lastRejectionDraftIds: Set<String> = emptySet()
''', "fake rejection ids")
anchor = '''    override suspend fun approveAllSuggestions(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
    ) {
'''
method = '''    override suspend fun commitDecisionDrafts(
        state: GlossarySnapshot,
        approvalDrafts: Map<String, GlossaryEntry>,
        rejectionDraftIds: Set<String>,
    ) {
        commitDecisionsCalled = true
        lastApprovalDrafts = approvalDrafts
        lastRejectionDraftIds = rejectionDraftIds
        mutated = true
    }

'''
s = replace_once(s, anchor, method + anchor, "fake mixed commit")
p.write_text(s)

# Action test
p = Path("app/src/test/java/cloud/shadowmonarchbooks/intakeedit/GlossaryActionsTest.kt")
s = p.read_text()
insertion = '''
    @Test
    fun mixedApprovalAndRejectionAreAppliedTogether() {
        val base = GlossaryEntry(
            id = "terms:既存",
            section = "terms",
            sourceAliases = listOf("既存"),
            translatedName = "Existing",
        )
        val approve = GlossaryProposal(
            id = "approve-lock",
            action = "lock_recommendation",
            reason = "lock",
            targetId = base.id,
            recommendQaLock = true,
        )
        val reject = GlossaryProposal(
            id = "reject-new",
            action = "new_entry",
            reason = "reject",
            section = "terms",
            sourceAliases = listOf("不要"),
            translatedName = "Unneeded",
        )
        val documents = GlossaryDocuments(
            baseEntries = listOf(base),
            additions = emptyList(),
            governance = emptyMap(),
            proposals = listOf(approve, reject),
        )
        val approvedEntry = GlossaryActions.proposalEntry(approve, documents)

        val result = GlossaryActions.applyPendingDecisions(
            documents,
            approvalDrafts = mapOf(approve.id to approvedEntry),
            rejectionDraftIds = setOf(reject.id),
        )

        assertTrue(result.governance.getValue(base.id).qaLock == true)
        assertEquals("approved", result.proposals.first { it.id == approve.id }.status)
        assertEquals("rejected", result.proposals.first { it.id == reject.id }.status)
    }
'''
pos = s.rfind("\n}")
if pos < 0:
    raise AssertionError("Actions test class close not found")
p.write_text(s[:pos] + insertion + s[pos:])

# Release
p = Path("app/build.gradle.kts")
s = p.read_text()
if 'versionCode = 21' not in s or 'versionName = "0.7.2"' not in s:
    raise AssertionError("Expected v0.7.2 metadata")
p.write_text(s.replace("versionCode = 21", "versionCode = 22", 1).replace('versionName = "0.7.2"', 'versionName = "0.7.3"', 1))
