from pathlib import Path

HOME = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/HomeActivity.kt")
BUILD = Path("app/build.gradle.kts")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


text = HOME.read_text(encoding="utf-8")
text = replace_once(
    text,
    'private enum class GlossaryTab(val label: String) { SUGGESTIONS("Suggestions"), APPROVED("Approved") }',
    'private enum class GlossaryTab(val label: String) { SUGGESTIONS("Suggestions"), APPROVED("Approved") }\nprivate enum class ApprovedGlossaryFilter(val label: String) { ALL("All"), LOCKED_QA("Locked QA") }',
    "filter enum",
)
text = replace_once(
    text,
    '    var search by rememberSaveable { mutableStateOf("") }\n    var editingProposal by remember { mutableStateOf<GlossaryProposal?>(null) }',
    '    var search by rememberSaveable { mutableStateOf("") }\n    var approvedFilter by rememberSaveable { mutableStateOf(ApprovedGlossaryFilter.ALL) }\n    var editingProposal by remember { mutableStateOf<GlossaryProposal?>(null) }',
    "filter state",
)
text = replace_once(
    text,
    '''    suspend fun saveProposal(client: GitHubApi, state: GlossaryRemoteState, proposal: GlossaryProposal) {
        val next = state.documents.proposals.map { if (it.id == proposal.id) proposal else it }
        client.updateFile(proposalsPath, state.proposals.sha, GlossaryParser.serializeProposals(next), "glossary: update proposal ${proposal.id}")
    }
''',
    '''    suspend fun saveProposal(client: GitHubApi, state: GlossaryRemoteState, proposal: GlossaryProposal) {
        val next = state.documents.proposals.map { if (it.id == proposal.id) proposal else it }
        client.updateFile(proposalsPath, state.proposals.sha, GlossaryParser.serializeProposals(next), "glossary: update proposal ${proposal.id}")
    }

    suspend fun approveAllSuggestions(client: GitHubApi, state: GlossaryRemoteState) {
        val pendingCount = state.documents.pendingProposals.size
        require(pendingCount > 0) { "There are no pending glossary suggestions." }
        val approved = GlossaryActions.approveAllPending(state.documents)
        val additions = GlossaryParser.serializeAdditions(approved.additions)
        val governance = GlossaryParser.serializeGovernance(approved.governance)
        val proposals = GlossaryParser.serializeProposals(approved.proposals)
        val updates = buildList {
            if (additions != state.additions.content) add(GitHubFileUpdate(additionsPath, state.additions.sha, additions))
            if (governance != state.governance.content) add(GitHubFileUpdate(governancePath, state.governance.sha, governance))
            if (proposals != state.proposals.content) add(GitHubFileUpdate(proposalsPath, state.proposals.sha, proposals))
        }
        require(updates.isNotEmpty()) { "No glossary files changed." }
        client.updateFilesAtomically(updates, "glossary: approve $pendingCount suggestions")
    }
''',
    "bulk approval helper",
)
text = replace_once(
    text,
    '''                        val proposals = state.documents.pendingProposals
                        if (proposals.isEmpty()) {
''',
    '''                        val proposals = state.documents.pendingProposals
                        if (proposals.isNotEmpty()) {
                            Button(
                                onClick = { runMutation { client, current -> approveAllSuggestions(client, current) } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Approve All (${proposals.size})") }
                        }
                        if (proposals.isEmpty()) {
''',
    "approve all button",
)
text = replace_once(
    text,
    '''                            TextButton(onClick = { addingEntry = true }, enabled = !busy) { Text("Add") }
                        }
                        val q = search.trim()
                        val visible = state.documents.effectiveEntries.filter { entry ->
                            q.isBlank() || entry.translatedName.contains(q, true) || entry.sourceAliases.any { it.contains(q, true) } || entry.section.contains(q, true)
                        }
''',
    '''                            TextButton(onClick = { addingEntry = true }, enabled = !busy) { Text("Add") }
                        }
                        val approvedEntries = state.documents.effectiveEntries
                        val lockedCount = approvedEntries.count { it.qaLock }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = approvedFilter == ApprovedGlossaryFilter.ALL,
                                onClick = { approvedFilter = ApprovedGlossaryFilter.ALL },
                                label = { Text("All (${approvedEntries.size})") },
                            )
                            FilterChip(
                                selected = approvedFilter == ApprovedGlossaryFilter.LOCKED_QA,
                                onClick = { approvedFilter = ApprovedGlossaryFilter.LOCKED_QA },
                                label = { Text("Locked QA ($lockedCount)") },
                            )
                        }
                        val q = search.trim()
                        val visible = approvedEntries.filter { entry ->
                            val filterMatch = approvedFilter == ApprovedGlossaryFilter.ALL || entry.qaLock
                            filterMatch && (q.isBlank() || entry.translatedName.contains(q, true) || entry.sourceAliases.any { it.contains(q, true) } || entry.section.contains(q, true))
                        }
''',
    "locked filter UI",
)
text = replace_once(
    text,
    '''                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
''',
    '''                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text(if (entry.qaLock) "Edit Lock" else "Edit") }
''',
    "locked edit label",
)
HOME.write_text(text, encoding="utf-8")

build = BUILD.read_text(encoding="utf-8")
build = replace_once(build, 'versionCode = 16', 'versionCode = 17', "version code")
build = replace_once(build, 'versionName = "0.6.0"', 'versionName = "0.6.1"', "version name")
BUILD.write_text(build, encoding="utf-8")
print("Applied glossary bulk approval / locked QA UI patch and bumped app to 0.6.1")
