package cloud.shadowmonarchbooks.intakeedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val GLOSSARY_EXPORT_NAME = "Pure_Love_x_Violation_glossary_updated-v5.csv"

private enum class GlossaryWorkspaceTab(val label: String) { SUGGESTIONS("Suggestions"), APPROVED("Approved") }
private enum class GlossaryApprovedFilter { ALL, LOCKED_QA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlossaryWorkspaceScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val glossaryViewModel: GlossaryViewModel = viewModel()
    val state by glossaryViewModel.uiState.collectAsStateWithLifecycle()
    val settingsViewModel: RepoSettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val settings = settingsState.settings
    val token = settingsState.token
    var tab by rememberSaveable { mutableStateOf(GlossaryWorkspaceTab.SUGGESTIONS) }
    var search by rememberSaveable { mutableStateOf("") }
    var approvedFilter by rememberSaveable { mutableStateOf(GlossaryApprovedFilter.ALL) }
    var editingProposal by remember { mutableStateOf<GlossaryProposal?>(null) }
    var editingEntry by remember { mutableStateOf<GlossaryEntry?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var localNotice by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                val snapshot = state.snapshot ?: error("Glossary is not loaded.")
                val raw = GlossaryExport.serialize(snapshot.documents.effectiveEntries)
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(raw.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the selected export destination.")
            }.onSuccess {
                localNotice = "Glossary exported in the original supplied format."
            }.onFailure {
                localNotice = it.message ?: "Glossary export failed."
            }
        }
    }

    LaunchedEffect(settings, token) {
        if (token.isNotBlank()) glossaryViewModel.refresh(settings, token)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glossary") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { glossaryViewModel.refresh(settings, token) },
                        enabled = !state.busy && token.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (localNotice ?: state.notice)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (token.isBlank()) {
                Text("GitHub is not configured yet. Open Chapter Intake and save repository settings/token first.")
                Button(
                    onClick = {
                        settingsViewModel.openSettings()
                        onOpenSettings()
                    },
                ) { Text("Open Chapter Intake Settings") }
                return@Column
            }

            val snapshot = state.snapshot
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlossaryWorkspaceTab.entries.forEach { item ->
                    val count = when (item) {
                        GlossaryWorkspaceTab.SUGGESTIONS -> snapshot?.documents?.pendingProposals?.size ?: 0
                        GlossaryWorkspaceTab.APPROVED -> snapshot?.documents?.effectiveEntries?.size ?: 0
                    }
                    FilterChip(
                        selected = tab == item,
                        onClick = { tab = item },
                        label = { Text("${item.label} ($count)") },
                    )
                }
            }

            if (state.busy && snapshot == null) CircularProgressIndicator()
            if (snapshot != null) {
                when (tab) {
                    GlossaryWorkspaceTab.SUGGESTIONS -> {
                        val proposals = snapshot.documents.pendingProposals
                        if (proposals.isNotEmpty()) {
                            Button(
                                onClick = { glossaryViewModel.approveAll(settings, token) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Approve All (${proposals.size})") }
                        }
                        if (proposals.isEmpty()) {
                            Text("No pending glossary suggestions.", modifier = Modifier.padding(12.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(proposals, key = { it.id }) { proposal ->
                                    GlossaryProposalCard(
                                        proposal = proposal,
                                        busy = state.busy,
                                        onEdit = { editingProposal = proposal },
                                        onApprove = {
                                            glossaryViewModel.proposalEntry(proposal, settings, token)?.let { entry ->
                                                glossaryViewModel.approveProposal(proposal, entry, settings, token)
                                            }
                                        },
                                        onReject = {
                                            glossaryViewModel.rejectProposal(proposal, settings, token)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    GlossaryWorkspaceTab.APPROVED -> {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = { Text("Search glossary") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextButton(onClick = { addingEntry = true }, enabled = !state.busy) { Text("Add") }
                            TextButton(
                                onClick = { exportLauncher.launch(GLOSSARY_EXPORT_NAME) },
                                enabled = !state.busy,
                            ) { Text("Export") }
                        }
                        val approvedEntries = snapshot.documents.effectiveEntries
                        val lockedCount = approvedEntries.count { it.qaLock }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = approvedFilter == GlossaryApprovedFilter.ALL,
                                onClick = { approvedFilter = GlossaryApprovedFilter.ALL },
                                label = { Text("All (${approvedEntries.size})") },
                            )
                            FilterChip(
                                selected = approvedFilter == GlossaryApprovedFilter.LOCKED_QA,
                                onClick = { approvedFilter = GlossaryApprovedFilter.LOCKED_QA },
                                label = { Text("Locked QA ($lockedCount)") },
                            )
                        }
                        val q = search.trim()
                        val visible = approvedEntries.filter { entry ->
                            val filterMatch = approvedFilter == GlossaryApprovedFilter.ALL || entry.qaLock
                            filterMatch && (
                                q.isBlank() ||
                                    entry.translatedName.contains(q, true) ||
                                    entry.sourceAliases.any { it.contains(q, true) } ||
                                    entry.section.contains(q, true)
                                )
                        }
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visible, key = { it.id }) { entry ->
                                GlossaryApprovedEntryCard(
                                    entry = entry,
                                    busy = state.busy,
                                    onEdit = { editingEntry = entry },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingProposal?.let { proposal ->
        val initial = glossaryViewModel.proposalEntry(proposal, settings, token) ?: GlossaryEntry(
            id = "proposal:${proposal.id}",
            section = proposal.section ?: "terms",
            sourceAliases = proposal.sourceAliases,
            translatedName = proposal.translatedName.orEmpty(),
            gender = proposal.gender,
            description = proposal.description,
            qaLock = proposal.recommendQaLock,
        )
        GlossaryWorkspaceEditDialog(
            title = "Edit suggestion",
            initial = initial,
            newEntry = proposal.action == "new_entry",
            confirmLabel = "Approve",
            secondaryLabel = "Save suggestion",
            onDismiss = { editingProposal = null },
            onConfirm = { edited ->
                editingProposal = null
                glossaryViewModel.saveProposalEdit(
                    proposal = proposal,
                    edited = edited,
                    approve = true,
                    settings = settings,
                    token = token,
                )
            },
            onSecondary = { edited ->
                editingProposal = null
                glossaryViewModel.saveProposalEdit(
                    proposal = proposal,
                    edited = edited,
                    approve = false,
                    settings = settings,
                    token = token,
                )
            },
        )
    }

    editingEntry?.let { entry ->
        GlossaryWorkspaceEditDialog(
            title = "Edit approved entry",
            initial = entry,
            newEntry = false,
            confirmLabel = "Save",
            onDismiss = { editingEntry = null },
            onConfirm = { edited ->
                editingEntry = null
                glossaryViewModel.saveApprovedEntry(edited, false, settings, token)
            },
        )
    }

    if (addingEntry) {
        GlossaryWorkspaceEditDialog(
            title = "Add glossary entry",
            initial = GlossaryEntry("", "terms", emptyList(), "", origin = "editor-approved"),
            newEntry = true,
            confirmLabel = "Add",
            onDismiss = { addingEntry = false },
            onConfirm = { edited ->
                addingEntry = false
                glossaryViewModel.saveApprovedEntry(edited, true, settings, token)
            },
        )
    }
}

@Composable
private fun GlossaryProposalCard(
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
private fun GlossaryApprovedEntryCard(entry: GlossaryEntry, onEdit: () -> Unit, busy: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.translatedName, fontWeight = FontWeight.Bold)
                Text(entry.sourceAliases.joinToString(" / "))
                Text(
                    entry.section.uppercase() + if (entry.qaLock) " • QA LOCK" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text(if (entry.qaLock) "Edit Lock" else "Edit") }
        }
    }
}

@Composable
private fun GlossaryWorkspaceEditDialog(
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
                OutlinedTextField(
                    gender,
                    { gender = it },
                    label = { Text("Gender / metadata (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(qaLock, { qaLock = it })
                    Text("QA lock")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(caseSensitive, { caseSensitive = it }, enabled = qaLock)
                    Text("Case-sensitive lock")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(result()) }, enabled = valid) { Text(confirmLabel) }
        },
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
