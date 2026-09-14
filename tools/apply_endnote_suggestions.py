from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"marker not found: {label}")
    return text.replace(old, new, 1)

# OpenChapter carries the shared proposal inbox snapshot while a chapter is open.
path = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt")
s = path.read_text()
s = replace_once(
    s,
    '''internal data class OpenChapter(\n    val file: ChapterFile,\n    val remote: FileSnapshot,\n    val raw: String,\n    val document: EditorDocument,\n    val qa: QaFindingsSnapshot?,\n)''',
    '''internal data class OpenChapter(\n    val file: ChapterFile,\n    val remote: FileSnapshot,\n    val raw: String,\n    val document: EditorDocument,\n    val qa: QaFindingsSnapshot?,\n    val endnoteProposals: EndnoteProposalSnapshot? = null,\n)''',
    "OpenChapter proposal snapshot",
)
path.write_text(s)

# Repository: load proposal inbox and atomically commit staged proposal decisions with chapter changes.
path = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/ChapterRepository.kt")
s = path.read_text()
s = replace_once(
    s,
    '''internal data class QaProgressCounts(\n    val active: Int,\n    val total: Int,\n)\n''',
    '''internal data class QaProgressCounts(\n    val active: Int,\n    val total: Int,\n)\n\ninternal data class ChapterCommitResult(\n    val remote: FileSnapshot,\n    val endnoteProposals: EndnoteProposalSnapshot?,\n)\n''',
    "commit result",
)
s = s.replace(
    '    suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): FileSnapshot\n',
    '    suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): ChapterCommitResult\n',
    1,
)
s = replace_once(
    s,
    '''        val qa = runCatching { loadQa(file) }.getOrNull()\n        return OpenChapter(file, remote, remote.content, document, qa)\n''',
    '''        val qa = runCatching { loadQa(file) }.getOrNull()\n        val proposals = runCatching { loadEndnoteProposals() }.getOrNull()\n        return OpenChapter(file, remote, remote.content, document, qa, proposals)\n''',
    "load proposals",
)
old_commit_start = s.index('    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean):')
old_commit_end = s.index('    override suspend fun overrideQa', old_commit_start)
new_commit = '''    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): ChapterCommitResult {\n        val document = if (markReviewed) {\n            require(chapter.document.englishSupplied == chapter.document.englishTotal) {\n                "Supply English for every paragraph before marking editor review complete."\n            }\n            chapter.document.copy(editorReviewComplete = true)\n        } else chapter.document\n        val raw = IntakeParser.patchDocument(chapter.raw, document)\n        IntakeParser.validate(raw).getOrThrow()\n\n        val proposalSnapshot = chapter.endnoteProposals\n        val proposalChanged = proposalSnapshot?.changed == true\n        val chapterChanged = raw != chapter.remote.content\n        if (!chapterChanged && !proposalChanged) {\n            return ChapterCommitResult(chapter.remote, proposalSnapshot)\n        }\n\n        if (proposalChanged) {\n            val updates = buildList {\n                if (chapterChanged) {\n                    add(GitHubFileUpdate(chapter.file.path, chapter.remote.sha, raw))\n                }\n                add(\n                    GitHubFileUpdate(\n                        EndnoteProposalParser.PATH,\n                        proposalSnapshot!!.remote.sha,\n                        proposalSnapshot.raw,\n                    ),\n                )\n            }\n            client.updateFilesAtomically(\n                updates,\n                "edit: revise ch_${chapter.file.chapter.toString().padStart(4, '0')} English/endnotes",\n            )\n            val committedRemote = if (chapterChanged) client.getFile(chapter.file.path) else chapter.remote\n            val proposalRemote = client.getFile(EndnoteProposalParser.PATH)\n            val committedProposals = EndnoteProposalSnapshot(\n                path = EndnoteProposalParser.PATH,\n                remote = proposalRemote,\n                raw = proposalRemote.content,\n                document = EndnoteProposalParser.parse(proposalRemote.content),\n            )\n            return ChapterCommitResult(committedRemote, committedProposals)\n        }\n\n        val sha = client.updateFile(\n            chapter.file.path,\n            chapter.remote.sha,\n            raw,\n            "edit: revise ch_${chapter.file.chapter.toString().padStart(4, '0')} English",\n        )\n        return ChapterCommitResult(\n            FileSnapshot(chapter.file.path, sha.ifBlank { chapter.remote.sha }, raw),\n            proposalSnapshot,\n        )\n    }\n\n'''
s = s[:old_commit_start] + new_commit + s[old_commit_end:]
insert_marker = '''    private suspend fun loadQa(file: ChapterFile): QaFindingsSnapshot? {\n'''
insert = '''    private suspend fun loadEndnoteProposals(): EndnoteProposalSnapshot? {\n        val snapshot = client.getFileOrNull(EndnoteProposalParser.PATH) ?: return null\n        return EndnoteProposalSnapshot(\n            path = EndnoteProposalParser.PATH,\n            remote = snapshot,\n            raw = snapshot.content,\n            document = EndnoteProposalParser.parse(snapshot.content),\n        )\n    }\n\n'''
s = replace_once(s, insert_marker, insert + insert_marker, "proposal loader")
path.write_text(s)

# ViewModel consumes richer commit result so an in-place commit has fresh proposal SHA/state too.
path = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorViewModel.kt")
s = path.read_text()
s = replace_once(
    s,
    '''                val committedRemote = repository.commitChapter(next, markReviewed)\n                draftRepository.delete(next.file.path)\n''',
    '''                val commitResult = repository.commitChapter(next, markReviewed)\n                draftRepository.delete(next.file.path)\n''',
    "viewmodel commit result",
)
s = replace_once(
    s,
    '''                    val committed = next.copy(\n                        remote = committedRemote,\n                        raw = committedRemote.content,\n                    )\n''',
    '''                    val committed = next.copy(\n                        remote = commitResult.remote,\n                        raw = commitResult.remote.content,\n                        document = IntakeParser.parse(commitResult.remote.content),\n                        endnoteProposals = commitResult.endnoteProposals,\n                    )\n''',
    "viewmodel in-place refresh",
)
path.write_text(s)

# Editor UI: stage proposal decisions locally; show ! left of Copy; suggestion dialog can prefill Endnote flow.
path = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt")
s = path.read_text()
s = replace_once(
    s,
    '''    fun removeEndnote(locator: String, english: String, noteId: String) {\n        updateDocument(EndnoteIntegrity.remove(current.document, locator, english, noteId))\n        editorNotice = "Endnote removed."\n    }\n\n''',
    '''    fun removeEndnote(locator: String, english: String, noteId: String) {\n        updateDocument(EndnoteIntegrity.remove(current.document, locator, english, noteId))\n        editorNotice = "Endnote removed."\n    }\n\n    fun stageEndnoteProposal(proposalId: String, status: String) {\n        val snapshot = current.endnoteProposals ?: return\n        val staged = EndnoteProposalParser.stage(snapshot, proposalId, status)\n        current = current.copy(endnoteProposals = staged)\n        onDraft(current)\n        editorNotice = if (status == "accepted") {\n            "Endnote suggestion accepted; decision will be committed with the chapter."\n        } else {\n            "Endnote suggestion dismissed; decision will be committed with the chapter."\n        }\n    }\n\n''',
    "stage proposal helper",
)
s = replace_once(
    s,
    '''    val activeFilterCount = (if (showQa) 1 else 0) + (if (!showQa && filter != EntryFilter.ALL) 1 else 0)\n''',
    '''    val activeFilterCount = (if (showQa) 1 else 0) + (if (!showQa && filter != EntryFilter.ALL) 1 else 0)\n    val hasLocalChanges = current.raw != current.remote.content || current.endnoteProposals?.changed == true\n''',
    "local changes includes proposals",
)
s = replace_once(
    s,
    '''                    Icon(\n                        if (current.raw == current.remote.content) Icons.Default.CloudDone else Icons.Default.EditNote,\n                        if (current.raw == current.remote.content) "No changes" else "Unsaved local changes",\n                    )\n''',
    '''                    Icon(\n                        if (!hasLocalChanges) Icons.Default.CloudDone else Icons.Default.EditNote,\n                        if (!hasLocalChanges) "No changes" else "Unsaved local changes",\n                    )\n''',
    "toolbar change state",
)
s = replace_once(
    s,
    '''                                entry = entry,\n                                endnotes = current.document.endnotes.filter { it.locator == entry.locator },\n                                importedTranslation = imported?.translationFor(entry.locator),\n''',
    '''                                entry = entry,\n                                endnotes = current.document.endnotes.filter { it.locator == entry.locator },\n                                proposals = current.endnoteProposals?.document\n                                    ?.pendingFor(current.file.volume, current.file.chapter, entry.locator)\n                                    .orEmpty(),\n                                importedTranslation = imported?.translationFor(entry.locator),\n''',
    "pass proposals to card",
)
s = replace_once(
    s,
    '''                                onRemoveEndnote = { english, noteId -> removeEndnote(entry.locator, english, noteId) },\n''',
    '''                                onRemoveEndnote = { english, noteId -> removeEndnote(entry.locator, english, noteId) },\n                                onProposalDecision = { proposalId, status -> stageEndnoteProposal(proposalId, status) },\n''',
    "proposal callback",
)
s = replace_once(
    s,
    '''    endnotes: List<EndnoteDefinition>,\n    importedTranslation: String?,\n''',
    '''    endnotes: List<EndnoteDefinition>,\n    proposals: List<EndnoteProposal>,\n    importedTranslation: String?,\n''',
    "card proposal arg",
)
s = replace_once(
    s,
    '''    onSaveEndnote: (String, EndnoteDefinition) -> Unit,\n    onRemoveEndnote: (String, String) -> Unit,\n) {\n''',
    '''    onSaveEndnote: (String, EndnoteDefinition) -> Unit,\n    onRemoveEndnote: (String, String) -> Unit,\n    onProposalDecision: (String, String) -> Unit,\n) {\n''',
    "card proposal callback arg",
)
s = replace_once(
    s,
    '''    var endnoteAnchor by remember(entry.locator) { mutableStateOf("") }\n    var endnoteDraft by remember(entry.locator) { mutableStateOf("") }\n''',
    '''    var endnoteAnchor by remember(entry.locator) { mutableStateOf("") }\n    var endnoteDraft by remember(entry.locator) { mutableStateOf("") }\n    var showProposalDialog by remember(entry.locator) { mutableStateOf(false) }\n    var proposalIndex by remember(entry.locator) { mutableStateOf(0) }\n    var pendingSuggestion by remember(entry.locator) { mutableStateOf<EndnoteProposal?>(null) }\n''',
    "proposal states",
)
s = replace_once(
    s,
    '''        editingEndnoteId = id\n        pendingSelection = rich.rangeForEndnote(id)\n''',
    '''        editingEndnoteId = id\n        pendingSuggestion = null\n        pendingSelection = rich.rangeForEndnote(id)\n''',
    "existing note clears suggestion",
)
s = replace_once(
    s,
    '''                IconButton(onClick = {\n                    focusManager.clearFocus(force = true)\n''',
    '''                if (proposals.isNotEmpty()) {\n                    BadgedBox(\n                        badge = { if (proposals.size > 1) Badge { Text(proposals.size.toString()) } },\n                    ) {\n                        TextButton(onClick = { proposalIndex = 0; showProposalDialog = true }) {\n                            Text("!", fontWeight = FontWeight.Bold)\n                        }\n                    }\n                }\n                IconButton(onClick = {\n                    focusManager.clearFocus(force = true)\n''',
    "suggestion button before copy",
)
s = replace_once(
    s,
    '''                                endnoteAnchor = rich.selectedText()\n                                endnoteDraft = ""\n                                showEndnoteDialog = true\n''',
    '''                                endnoteAnchor = rich.selectedText()\n                                endnoteDraft = pendingSuggestion?.suggestedContent.orEmpty()\n                                showEndnoteDialog = true\n''',
    "prefill N from suggestion",
)
s = replace_once(
    s,
    '''                        showEndnoteDialog = false\n                    },\n                    enabled = endnoteDraft.isNotBlank(),\n''',
    '''                        pendingSuggestion?.let { proposal ->\n                            if (editingEndnoteId == null) onProposalDecision(proposal.id, "accepted")\n                        }\n                        pendingSuggestion = null\n                        showEndnoteDialog = false\n                    },\n                    enabled = endnoteDraft.isNotBlank(),\n''',
    "accept suggestion on save",
)
s = replace_once(
    s,
    '''                    TextButton(onClick = { showEndnoteDialog = false }) { Text("Cancel") }\n''',
    '''                    TextButton(onClick = { pendingSuggestion = null; showEndnoteDialog = false }) { Text("Cancel") }\n''',
    "cancel suggestion",
)
# Insert proposal dialog before Endnote dialog.
marker = '''    if (showEndnoteDialog) {\n'''
proposal_dialog = '''    if (showProposalDialog && proposals.isNotEmpty()) {\n        val safeIndex = proposalIndex.coerceIn(0, proposals.lastIndex)\n        val proposal = proposals[safeIndex]\n        AlertDialog(\n            onDismissRequest = { showProposalDialog = false },\n            title = { Text("Endnote suggestion${if (proposals.size > 1) " ${safeIndex + 1}/${proposals.size}" else ""}") },\n            text = {\n                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {\n                    DisplayBlock("Source anchor", proposal.sourceAnchor)\n                    DisplayBlock("Suggested English anchor", proposal.suggestedEnglishAnchor)\n                    DisplayBlock("Suggested Endnote", proposal.suggestedContent)\n                    DisplayBlock("Reason", proposal.reason)\n                }\n            },\n            confirmButton = {\n                Button(onClick = {\n                    pendingSuggestion = proposal\n                    val anchor = proposal.suggestedEnglishAnchor\n                    val start = if (anchor.isBlank()) -1 else rich.text.indexOf(anchor)\n                    val unique = start >= 0 && rich.text.indexOf(anchor, start + anchor.length) < 0\n                    showProposalDialog = false\n                    if (unique) {\n                        val selection = TextRange(start, start + anchor.length)\n                        rich = rich.copy(selection = selection)\n                        editingEndnoteId = null\n                        pendingSelection = selection\n                        endnoteAnchor = anchor\n                        endnoteDraft = proposal.suggestedContent\n                        showEndnoteDialog = true\n                    } else {\n                        Toast.makeText(\n                            context,\n                            "Select the intended English anchor, then tap N. The suggested Endnote will be prefilled.",\n                            Toast.LENGTH_LONG,\n                        ).show()\n                    }\n                }) { Text("Use suggestion") }\n            },\n            dismissButton = {\n                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {\n                    if (proposals.size > 1) {\n                        TextButton(onClick = { proposalIndex = (safeIndex + 1) % proposals.size }) { Text("Next") }\n                    }\n                    TextButton(onClick = {\n                        onProposalDecision(proposal.id, "rejected")\n                        showProposalDialog = false\n                    }) { Text("Dismiss") }\n                    TextButton(onClick = { showProposalDialog = false }) { Text("Close") }\n                }\n            },\n        )\n    }\n\n'''
s = replace_once(s, marker, proposal_dialog + marker, "proposal dialog")
path.write_text(s)

# Tests: v6 sample + richer commit result, plus proposal parser/staging coverage.
path = Path("app/src/test/java/cloud/shadowmonarchbooks/intakeedit/EditorViewModelTest.kt")
s = path.read_text()
s = s.replace(
    '    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): FileSnapshot {\n',
    '    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): ChapterCommitResult {\n',
    1,
)
s = s.replace(
    '        return chapter.remote.copy(sha = "sha-committed", content = chapter.raw)\n',
    '        return ChapterCommitResult(chapter.remote.copy(sha = "sha-committed", content = chapter.raw), chapter.endnoteProposals)\n',
    1,
)
s = s.replace('            schemaVersion = 5,', '            schemaVersion = 6,')
s = s.replace('            entries = listOf(EditorEntry("p-1", "raw", "")),', '            entries = listOf(EditorEntry("P1", "raw", "")),\n            endnotes = emptyList(),')
path.write_text(s)

Path("app/src/test/java/cloud/shadowmonarchbooks/intakeedit/EndnoteProposalsTest.kt").write_text('''package cloud.shadowmonarchbooks.intakeedit\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass EndnoteProposalsTest {\n    private val raw = """\n        {\n          "schema_version": 1,\n          "proposals": [\n            {\n              "id": "endnote-v01-c0001-p1-001",\n              "volume": 1,\n              "chapter": 1,\n              "locator": "P1",\n              "source_anchor": "文化",\n              "suggested_english_anchor": "culture",\n              "suggested_content": "Context for readers.",\n              "reason": "Cultural context.",\n              "status": "pending"\n            }\n          ]\n        }\n    """.trimIndent() + "\\n"\n\n    @Test\n    fun pendingSuggestionCanBeStagedWithoutMutatingRemoteSnapshot() {\n        val remote = FileSnapshot(EndnoteProposalParser.PATH, "sha-1", raw)\n        val snapshot = EndnoteProposalSnapshot(\n            EndnoteProposalParser.PATH, remote, raw, EndnoteProposalParser.parse(raw),\n        )\n        val staged = EndnoteProposalParser.stage(snapshot, "endnote-v01-c0001-p1-001", "rejected")\n\n        assertEquals(1, snapshot.document.pendingFor(1, 1, "P1").size)\n        assertTrue(staged.document.pendingFor(1, 1, "P1").isEmpty())\n        assertEquals("rejected", staged.document.proposals.single().status)\n        assertTrue(staged.changed)\n        assertFalse(snapshot.changed)\n        assertEquals("sha-1", staged.remote.sha)\n    }\n}\n''')
