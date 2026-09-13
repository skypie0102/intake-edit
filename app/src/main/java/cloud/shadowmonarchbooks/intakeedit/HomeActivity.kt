package cloud.shadowmonarchbooks.intakeedit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val glossaryBasePaths = listOf(
    "glossary/full/characters.txt",
    "glossary/full/locations.txt",
    "glossary/full/nicknames.txt",
    "glossary/full/terms.txt",
    "glossary/full/honorifics.txt",
)
private const val additionsPath = "glossary/approved_additions.json"
private const val governancePath = "glossary/governance.json"
private const val proposalsPath = "glossary/proposals.json"
private const val glossaryExportName = "Pure_Love_x_Violation_glossary_updated-v5.csv"

private enum class GlossaryTab(val label: String) { SUGGESTIONS("Suggestions"), APPROVED("Approved") }
private enum class ApprovedGlossaryFilter(val label: String) { ALL("All"), LOCKED_QA("Locked QA") }

private data class GlossaryRemoteState(
    val base: List<FileSnapshot>,
    val additions: FileSnapshot,
    val governance: FileSnapshot,
    val proposals: FileSnapshot,
    val documents: GlossaryDocuments,
)

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AppHome(onOpenChapters = { startActivity(Intent(this, MainActivity::class.java)) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppHome(onOpenChapters: () -> Unit) {
    var showGlossary by rememberSaveable { mutableStateOf(false) }
    if (showGlossary) {
        GlossaryScreen(onBack = { showGlossary = false })
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Intake Edit") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Workspace", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chapter Intake", fontWeight = FontWeight.Bold)
                    Text("Import rough translations, edit authoritative English, review QA findings, and commit schema-v5 chapter files.")
                    Button(onClick = onOpenChapters) { Text("Open Chapter Intake") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Glossary", fontWeight = FontWeight.Bold)
                    Text("Review whole-volume suggestions, manage approved terminology and QA locks, and export the running glossary in the original file format.")
                    Button(onClick = { showGlossary = true }) { Text("Open Glossary") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlossaryScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val tokenStore = remember { SecureTokenStore(context) }
    val settings = remember { settingsStore.load() }
    val token = remember { tokenStore.load() }
    val api = remember(settings, token) { token.takeIf { it.isNotBlank() }?.let { GitHubApi(settings, it) } }
    var remote by remember { mutableStateOf<GlossaryRemoteState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(GlossaryTab.SUGGESTIONS) }
    var search by rememberSaveable { mutableStateOf("") }
    var approvedFilter by rememberSaveable { mutableStateOf(ApprovedGlossaryFilter.ALL) }
    var editingProposal by remember { mutableStateOf<GlossaryProposal?>(null) }
    var editingEntry by remember { mutableStateOf<GlossaryEntry?>(null) }
    var addingEntry by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                val state = remote ?: error("Glossary is not loaded.")
                val raw = GlossaryExport.serialize(state.documents.effectiveEntries)
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(raw.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the selected export destination.")
            }.onSuccess {
                notice = "Glossary exported in the original supplied format."
            }.onFailure {
                notice = it.message ?: "Glossary export failed."
            }
        }
    }

    suspend fun loadState(client: GitHubApi): GlossaryRemoteState {
        val base = glossaryBasePaths.map { client.getFile(it) }
        val additions = client.getFile(additionsPath)
        val governance = client.getFile(governancePath)
        val proposals = client.getFile(proposalsPath)
        val baseEntries = base.flatMap { GlossaryParser.parseBaseFile(it.content) }
        val documents = GlossaryDocuments(
            baseEntries = baseEntries,
            additions = GlossaryParser.parseAdditions(additions.content),
            governance = GlossaryParser.parseGovernance(governance.content),
            proposals = GlossaryParser.parseProposals(proposals.content),
        )
        return GlossaryRemoteState(base, additions, governance, proposals, documents)
    }

    fun refresh() {
        val client = api ?: return
        scope.launch {
            busy = true
            try {
                remote = loadState(client)
                notice = null
            } catch (t: Throwable) {
                notice = t.message ?: "Could not load glossary."
            } finally {
                busy = false
            }
        }
    }

    suspend fun saveApprovedEntry(client: GitHubApi, state: GlossaryRemoteState, entry: GlossaryEntry, allowNew: Boolean) {
        val additionIndex = state.documents.additions.indexOfFirst { it.id == entry.id }
        val baseExists = state.documents.baseEntries.any { it.id == entry.id }
        when {
            additionIndex >= 0 -> {
                val next = state.documents.additions.toMutableList().also { it[additionIndex] = entry.copy(origin = "editor-approved") }
                client.updateFile(additionsPath, state.additions.sha, GlossaryParser.serializeAdditions(next), "glossary: edit ${entry.id}")
            }
            baseExists -> {
                val next = state.documents.governance + (entry.id to GlossaryParser.fullOverride(entry))
                client.updateFile(governancePath, state.governance.sha, GlossaryParser.serializeGovernance(next), "glossary: govern ${entry.id}")
            }
            allowNew -> {
                GlossaryParser.ensureNewEntryAbsent(entry, state.documents.effectiveEntries)
                val next = state.documents.additions + entry.copy(origin = "editor-approved")
                client.updateFile(additionsPath, state.additions.sha, GlossaryParser.serializeAdditions(next), "glossary: add ${entry.id}")
            }
            else -> error("Glossary entry no longer exists: ${entry.id}")
        }
    }

    suspend fun saveProposal(client: GitHubApi, state: GlossaryRemoteState, proposal: GlossaryProposal) {
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

    fun proposalEntry(proposal: GlossaryProposal, documents: GlossaryDocuments): GlossaryEntry {
        val target = proposal.targetId?.let { id -> documents.effectiveEntries.firstOrNull { it.id == id } }
        return when (proposal.action) {
            "new_entry" -> {
                val section = proposal.section.orEmpty().ifBlank { "terms" }
                val aliases = proposal.sourceAliases
                GlossaryEntry(
                    id = GlossaryParser.entryId(section, aliases),
                    section = section,
                    sourceAliases = aliases,
                    translatedName = proposal.translatedName.orEmpty(),
                    gender = proposal.gender,
                    description = proposal.description,
                    qaLock = proposal.recommendQaLock,
                    origin = "editor-approved",
                )
            }
            "lock_recommendation" -> requireNotNull(target) { "Proposal target not found: ${proposal.targetId}" }.copy(qaLock = true)
            else -> {
                val current = requireNotNull(target) { "Proposal target not found: ${proposal.targetId}" }
                current.copy(
                    sourceAliases = proposal.sourceAliases.takeIf { it.isNotEmpty() } ?: current.sourceAliases,
                    translatedName = proposal.translatedName?.takeIf { it.isNotBlank() } ?: current.translatedName,
                    gender = proposal.gender ?: current.gender,
                    description = proposal.description.takeIf { it.isNotBlank() } ?: current.description,
                    qaLock = current.qaLock || proposal.recommendQaLock,
                )
            }
        }
    }

    fun runMutation(block: suspend (GitHubApi, GlossaryRemoteState) -> Unit) {
        val client = api ?: return
        val state = remote ?: return
        scope.launch {
            busy = true
            try {
                block(client, state)
                remote = loadState(client)
                notice = "Glossary changes committed."
            } catch (t: Throwable) {
                notice = t.message ?: "Glossary update failed."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(api) { if (api != null) refresh() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Glossary") },
            navigationIcon = { IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = { IconButton(onClick = ::refresh, enabled = !busy && api != null) { Icon(Icons.Default.Refresh, "Refresh") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (api == null) {
                Text("GitHub is not configured yet. Open Chapter Intake and save repository settings/token first.")
                Button(onClick = { context.startActivity(Intent(context, MainActivity::class.java)) }) { Text("Open Chapter Intake Settings") }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlossaryTab.entries.forEach { item ->
                    val count = when (item) {
                        GlossaryTab.SUGGESTIONS -> remote?.documents?.pendingProposals?.size ?: 0
                        GlossaryTab.APPROVED -> remote?.documents?.effectiveEntries?.size ?: 0
                    }
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text("${item.label} ($count)") })
                }
            }
            if (busy && remote == null) CircularProgressIndicator()
            val state = remote
            if (state != null) {
                when (tab) {
                    GlossaryTab.SUGGESTIONS -> {
                        val proposals = state.documents.pendingProposals
                        if (proposals.isNotEmpty()) {
                            Button(
                                onClick = { runMutation { client, current -> approveAllSuggestions(client, current) } },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Approve All (${proposals.size})") }
                        }
                        if (proposals.isEmpty()) {
                            Text("No pending glossary suggestions.", modifier = Modifier.padding(12.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(proposals, key = { it.id }) { proposal ->
                                    ProposalCard(
                                        proposal = proposal,
                                        onEdit = { editingProposal = proposal },
                                        onApprove = {
                                            runMutation { client, current ->
                                                val entry = proposalEntry(proposal, current.documents)
                                                saveApprovedEntry(client, current, entry, proposal.action == "new_entry")
                                                saveProposal(client, current, proposal.copy(status = "approved"))
                                            }
                                        },
                                        onReject = {
                                            runMutation { client, current -> saveProposal(client, current, proposal.copy(status = "rejected")) }
                                        },
                                        busy = busy,
                                    )
                                }
                            }
                        }
                    }
                    GlossaryTab.APPROVED -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = { Text("Search glossary") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextButton(onClick = { addingEntry = true }, enabled = !busy) { Text("Add") }
                            TextButton(onClick = { exportLauncher.launch(glossaryExportName) }, enabled = !busy) { Text("Export") }
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
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visible, key = { it.id }) { entry ->
                                ApprovedEntryCard(entry, onEdit = { editingEntry = entry }, busy = busy)
                            }
                        }
                    }
                }
            }
        }
    }

    editingProposal?.let { proposal ->
        val documents = remote?.documents ?: return@let
        val initial = runCatching { proposalEntry(proposal, documents) }.getOrElse {
            GlossaryEntry("proposal:${proposal.id}", proposal.section ?: "terms", proposal.sourceAliases, proposal.translatedName.orEmpty(), proposal.gender, proposal.description, proposal.recommendQaLock)
        }
        GlossaryEditDialog(
            title = "Edit suggestion",
            initial = initial,
            newEntry = proposal.action == "new_entry",
            confirmLabel = "Approve",
            secondaryLabel = "Save suggestion",
            onDismiss = { editingProposal = null },
            onConfirm = { edited ->
                editingProposal = null
                runMutation { client, current ->
                    saveApprovedEntry(client, current, edited, proposal.action == "new_entry")
                    val updatedProposal = proposal.copy(
                        status = "approved",
                        section = edited.section,
                        sourceAliases = edited.sourceAliases,
                        translatedName = edited.translatedName,
                        gender = edited.gender,
                        description = edited.description,
                        recommendQaLock = edited.qaLock,
                    )
                    saveProposal(client, current, updatedProposal)
                }
            },
            onSecondary = { edited ->
                editingProposal = null
                runMutation { client, current ->
                    val updatedProposal = proposal.copy(
                        section = edited.section,
                        sourceAliases = edited.sourceAliases,
                        translatedName = edited.translatedName,
                        gender = edited.gender,
                        description = edited.description,
                        recommendQaLock = edited.qaLock,
                    )
                    saveProposal(client, current, updatedProposal)
                }
            },
        )
    }

    editingEntry?.let { entry ->
        GlossaryEditDialog(
            title = "Edit approved entry",
            initial = entry,
            newEntry = false,
            confirmLabel = "Save",
            onDismiss = { editingEntry = null },
            onConfirm = { edited ->
                editingEntry = null
                runMutation { client, current -> saveApprovedEntry(client, current, edited, false) }
            },
        )
    }

    if (addingEntry) {
        GlossaryEditDialog(
            title = "Add glossary entry",
            initial = GlossaryEntry("", "terms", emptyList(), "", origin = "editor-approved"),
            newEntry = true,
            confirmLabel = "Add",
            onDismiss = { addingEntry = false },
            onConfirm = { edited ->
                addingEntry = false
                runMutation { client, current -> saveApprovedEntry(client, current, edited, true) }
            },
        )
    }
}

@Composable
private fun ProposalCard(
    proposal: GlossaryProposal,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    busy: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(proposal.action.replace('_', ' ').uppercase(), fontWeight = FontWeight.Bold)
            proposal.translatedName?.takeIf { it.isNotBlank() }?.let { Text("English: $it") }
            if (proposal.sourceAliases.isNotEmpty()) Text("Japanese: ${proposal.sourceAliases.joinToString(" / ")}")
            proposal.targetId?.let { Text("Target: $it", style = MaterialTheme.typography.bodySmall) }
            val source = if (proposal.sourceVolume != null && proposal.occurrenceCount != null) {
                buildString {
                    append("Volume ${proposal.sourceVolume}")
                    if (proposal.sourceChapters.isNotEmpty()) append(" • ${proposal.sourceChapters.size} chapters")
                    append(" • ${proposal.occurrenceCount} occurrences")
                    if (proposal.sourceChapter != null || proposal.sourceLocator != null) {
                        append(" • first ")
                        proposal.sourceChapter?.let { append("Ch $it ") }
                        proposal.sourceLocator?.let { append(it) }
                    }
                }
            } else {
                buildString {
                    proposal.sourceVolume?.let { append("V$it ") }
                    proposal.sourceChapter?.let { append("Ch $it ") }
                    proposal.sourceLocator?.let { append(it) }
                }.trim()
            }
            if (source.isNotBlank()) Text("Source: $source", style = MaterialTheme.typography.bodySmall)
            Text(proposal.reason)
            if (proposal.recommendQaLock) Text("Agent recommends QA lock", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
                TextButton(onClick = onReject, enabled = !busy) { Text("Reject") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onApprove, enabled = !busy) { Text("Approve") }
            }
        }
    }
}

@Composable
private fun ApprovedEntryCard(entry: GlossaryEntry, onEdit: () -> Unit, busy: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.translatedName, fontWeight = FontWeight.Bold)
                Text(entry.sourceAliases.joinToString(" / "))
                Text(entry.section.uppercase() + if (entry.qaLock) " • QA LOCK" else "", style = MaterialTheme.typography.labelSmall)
                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text(if (entry.qaLock) "Edit Lock" else "Edit") }
        }
    }
}

@Composable
private fun GlossaryEditDialog(
    title: String,
    initial: GlossaryEntry,
    newEntry: Boolean,
    confirmLabel: String,
    secondaryLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (GlossaryEntry) -> Unit,
    onSecondary: ((GlossaryEntry) -> Unit)? = null,
) {
    var section by remember(initial.id) { mutableStateOf(initial.section) }
    var aliases by remember(initial.id) { mutableStateOf(initial.sourceAliases.joinToString(" / ")) }
    var english by remember(initial.id) { mutableStateOf(initial.translatedName) }
    var gender by remember(initial.id) { mutableStateOf(initial.gender.orEmpty()) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var qaLock by remember(initial.id) { mutableStateOf(initial.qaLock) }
    var caseSensitive by remember(initial.id) { mutableStateOf(initial.caseSensitive) }
    val aliasList = aliases.split(" / ").map { it.trim() }.filter { it.isNotBlank() }
    val valid = section.isNotBlank() && aliasList.isNotEmpty() && english.isNotBlank()
    fun result(): GlossaryEntry {
        val id = if (newEntry) GlossaryParser.entryId(section, aliasList) else initial.id
        return initial.copy(
            id = id,
            section = section.trim().lowercase(),
            sourceAliases = aliasList,
            translatedName = english.trim(),
            gender = gender.trim().ifBlank { null },
            description = description.trim(),
            qaLock = qaLock,
            caseSensitive = caseSensitive,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(section, { section = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(aliases, { aliases = it }, label = { Text("Japanese aliases (use  /  between aliases)") })
                OutlinedTextField(english, { english = it }, label = { Text("Canonical English") })
                OutlinedTextField(gender, { gender = it }, label = { Text("Gender / metadata (optional)") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(qaLock, { qaLock = it }); Text("QA lock") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(caseSensitive, { caseSensitive = it }, enabled = qaLock); Text("Case-sensitive lock") }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(result()) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = {
            Row {
                secondaryLabel?.let { label ->
                    TextButton(onClick = { onSecondary?.invoke(result()) }, enabled = valid) { Text(label) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
