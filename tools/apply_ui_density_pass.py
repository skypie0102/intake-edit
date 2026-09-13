from pathlib import Path
import re

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    (ROOT / path).write_text(text)


def replace_between(text, start, end, replacement, label):
    i = text.find(start)
    if i < 0:
        raise AssertionError(f'{label}: start marker not found')
    j = text.find(end, i)
    if j < 0:
        raise AssertionError(f'{label}: end marker not found')
    return text[:i] + replacement + text[j:]


def add_imports(text, imports):
    lines = text.splitlines()
    existing = set(line for line in lines if line.startswith('import '))
    missing = [item for item in imports if item not in existing]
    if not missing:
        return text
    last = max(i for i, line in enumerate(lines) if line.startswith('import '))
    lines[last + 1:last + 1] = missing
    return '\n'.join(lines) + ('\n' if text.endswith('\n') else '')


# ---------------- MainActivity / chapter list ----------------
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt'
s = read(path)
s = add_imports(s, [
    'import androidx.compose.material.icons.filled.ChevronRight',
    'import androidx.compose.material.icons.filled.FilterList',
    'import androidx.compose.material3.Badge',
    'import androidx.compose.material3.BadgedBox',
    'import androidx.compose.material3.ModalBottomSheet',
])
chapter_screen = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListScreen(
    files: List<ChapterFile>,
    progressByPath: Map<String, ChapterProgress>,
    availableVolumes: List<Int>,
    selectedVolume: Int?,
    refreshing: Boolean,
    openingPath: String?,
    notice: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onSelectVolume: (Int) -> Unit,
    onOpen: (ChapterFile) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ChapterListFilter.ACTIVE) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val searched = files.filter {
        val q = search.trim()
        q.isBlank() || it.path.contains(q, true) || it.chapter.toString().contains(q)
    }
    val visible = searched.filter { file ->
        val progress = progressByPath[file.path]
        when (filter) {
            ChapterListFilter.ACTIVE -> progress?.complete != true
            ChapterListFilter.COMPLETED -> progress?.complete == true
            ChapterListFilter.ALL -> true
        }
    }
    val activeFilterCount = (if (search.isNotBlank()) 1 else 0) + (if (filter != ChapterListFilter.ACTIVE) 1 else 0)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(selectedVolume?.let { "Chapter Intake • V$it" } ?: "Chapter Intake") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = openingPath == null) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                BadgedBox(
                    badge = {
                        if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
                    },
                ) {
                    IconButton(onClick = { showFilters = true }, enabled = openingPath == null) {
                        Icon(Icons.Default.FilterList, "Chapter filters")
                    }
                }
                IconButton(onClick = onRefresh, enabled = !refreshing && openingPath == null) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
                IconButton(onClick = onSettings, enabled = openingPath == null) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (refreshing && files.isEmpty()) CircularProgressIndicator()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.path }) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                val progress = progressByPath[file.path]
                                Text("Volume ${file.volume} • Chapter ${file.chapter}", fontWeight = FontWeight.Bold)
                                Text(file.path, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                val status = if (progress == null) {
                                    "English loading"
                                } else {
                                    buildString {
                                        append("${progress.englishSupplied}/${progress.englishTotal}")
                                        append(if (progress.editorReviewComplete) " • Reviewed" else " • Review pending")
                                        if (progress.qaTotal > 0) append(" • QA ${progress.qaActive}/${progress.qaTotal}")
                                    }
                                }
                                Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            IconButton(
                                onClick = { onOpen(file) },
                                enabled = openingPath == null,
                            ) {
                                Icon(Icons.Default.ChevronRight, if (openingPath == file.path) "Opening" else "Open chapter")
                            }
                        }
                    }
                }
                if (visible.isEmpty() && !refreshing) item {
                    Text("No chapters match these filters.", modifier = Modifier.padding(12.dp))
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Chapter filters", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Chapter, path, or filename") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Status", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChapterListFilter.entries.forEach { item ->
                        FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                    }
                }
                if (availableVolumes.isNotEmpty()) {
                    Text("Volume", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        availableVolumes.forEach { volume ->
                            FilterChip(
                                selected = selectedVolume == volume,
                                onClick = { onSelectVolume(volume) },
                                label = { Text("Volume $volume") },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        search = ""
                        filter = ChapterListFilter.ACTIVE
                    },
                    enabled = activeFilterCount > 0,
                ) { Text("Reset filters") }
            }
        }
    }
}

'''
s = replace_between(
    s,
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun ChapterListScreen(',
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun SettingsScreen(',
    chapter_screen,
    'ChapterListScreen',
)
write(path, s)


# ---------------- Workspace home density ----------------
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/WorkspaceHomeScreen.kt'
s = read(path)
s = s.replace('Modifier.fillMaxSize().padding(padding).padding(16.dp),\n            verticalArrangement = Arrangement.spacedBy(12.dp),',
              'Modifier.fillMaxSize().padding(padding).padding(12.dp),\n            verticalArrangement = Arrangement.spacedBy(8.dp),')
s = s.replace('Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp))',
              'Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp))')
write(path, s)


# ---------------- Glossary ViewModel: clear pooled decisions ----------------
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GlossaryViewModel.kt'
s = read(path)
marker = '''    fun commitPooled(settings: RepoSettings, token: String) {\n'''
insert = '''    fun clearPooledDecisions() {
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

'''
if marker not in s:
    raise AssertionError('GlossaryViewModel commit marker not found')
s = s.replace(marker, insert + marker, 1)
write(path, s)


# ---------------- Glossary workspace ----------------
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GlossaryWorkspaceScreen.kt'
s = read(path)
s = add_imports(s, [
    'import androidx.compose.foundation.horizontalScroll',
    'import androidx.compose.foundation.rememberScrollState',
    'import androidx.compose.material.icons.filled.Add',
    'import androidx.compose.material.icons.filled.Edit',
    'import androidx.compose.material.icons.filled.ExpandLess',
    'import androidx.compose.material.icons.filled.ExpandMore',
    'import androidx.compose.material.icons.filled.FileDownload',
    'import androidx.compose.material.icons.filled.FilterList',
    'import androidx.compose.material.icons.filled.Undo',
    'import androidx.compose.material3.Badge',
    'import androidx.compose.material3.BadgedBox',
    'import androidx.compose.material3.BottomAppBar',
    'import androidx.compose.material3.ModalBottomSheet',
])

glossary_screen = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlossaryWorkspaceScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glossaryViewModel: GlossaryViewModel = viewModel()
    val state by glossaryViewModel.uiState.collectAsStateWithLifecycle()
    val settingsViewModel: RepoSettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val settings = settingsState.settings
    val token = settingsState.token
    var tab by rememberSaveable { mutableStateOf(GlossaryWorkspaceTab.SUGGESTIONS) }
    var search by rememberSaveable { mutableStateOf("") }
    var sectionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var editingProposal by remember { mutableStateOf<GlossaryProposal?>(null) }
    var editingEntry by remember { mutableStateOf<GlossaryEntry?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var localNotice by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val snapshot = state.snapshot
            scope.launch {
                runCatching {
                    val current = snapshot ?: error("Glossary is not loaded.")
                    withContext(Dispatchers.IO) {
                        val raw = GlossaryExport.serialize(current.documents.effectiveEntries)
                        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                            stream.write(raw.toByteArray(Charsets.UTF_8))
                        } ?: error("Could not open the selected export destination.")
                    }
                }.onSuccess {
                    localNotice = "Glossary exported in the original supplied format."
                }.onFailure {
                    localNotice = it.message ?: "Glossary export failed."
                }
            }
        }
    }

    LaunchedEffect(settings, token) {
        if (token.isNotBlank()) glossaryViewModel.refresh(settings, token)
    }

    val snapshot = state.snapshot
    val activeFilterCount = (if (search.isNotBlank()) 1 else 0) + (if (sectionFilter != null) 1 else 0)

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
                    if (tab != GlossaryWorkspaceTab.SUGGESTIONS && snapshot != null) {
                        IconButton(onClick = { addingEntry = true }, enabled = !state.busy) {
                            Icon(Icons.Default.Add, "Add glossary entry")
                        }
                        IconButton(
                            onClick = { exportLauncher.launch(GLOSSARY_EXPORT_NAME) },
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Default.FileDownload, "Export glossary")
                        }
                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
                            },
                        ) {
                            IconButton(onClick = { showFilters = true }, enabled = !state.busy) {
                                Icon(Icons.Default.FilterList, "Glossary filters")
                            }
                        }
                    }
                    IconButton(
                        onClick = { glossaryViewModel.refresh(settings, token) },
                        enabled = !state.busy && token.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            if (state.pooledDecisionCount > 0) {
                BottomAppBar {
                    Text(
                        "${state.pooledDecisionCount} decision${if (state.pooledDecisionCount == 1) "" else "s"}",
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { glossaryViewModel.clearPooledDecisions() }, enabled = !state.busy) {
                        Text("Clear")
                    }
                    Button(
                        onClick = { glossaryViewModel.commitPooled(settings, token) },
                        enabled = !state.busy && token.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Commit") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GlossaryWorkspaceTab.entries.forEach { item ->
                    val count = when (item) {
                        GlossaryWorkspaceTab.SUGGESTIONS -> snapshot?.documents?.pendingProposals?.size ?: 0
                        GlossaryWorkspaceTab.QA_LOCKED -> snapshot?.documents?.effectiveEntries?.count { it.qaLock } ?: 0
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { glossaryViewModel.approveAll(settings, token) },
                                    enabled = !state.busy,
                                ) { Text("Approve all (${proposals.size})") }
                            }
                        }
                        if (proposals.isEmpty()) {
                            Text("No pending glossary suggestions.", modifier = Modifier.padding(12.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(proposals, key = { it.id }) { proposal ->
                                    val resolvedEntry = glossaryViewModel.proposalEntry(proposal, settings, token)
                                    val pooledEntry = state.approvalDrafts[proposal.id]
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
                                }
                            }
                        }
                    }
                    GlossaryWorkspaceTab.QA_LOCKED,
                    GlossaryWorkspaceTab.APPROVED,
                    -> {
                        val q = search.trim()
                        val visible = snapshot.documents.effectiveEntries.filter { entry ->
                            val tabMatch = tab != GlossaryWorkspaceTab.QA_LOCKED || entry.qaLock
                            val sectionMatch = sectionFilter == null || entry.section == sectionFilter
                            tabMatch && sectionMatch && (
                                q.isBlank() ||
                                    entry.translatedName.contains(q, true) ||
                                    entry.sourceAliases.any { it.contains(q, true) } ||
                                    entry.section.contains(q, true)
                                )
                        }
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(visible, key = { it.id }) { entry ->
                                GlossaryApprovedEntryCard(
                                    entry = entry,
                                    busy = state.busy,
                                    onEdit = { editingEntry = entry },
                                )
                            }
                            if (visible.isEmpty()) item {
                                Text("No glossary entries match these filters.", modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters && snapshot != null) {
        val sections = snapshot.documents.effectiveEntries.map { it.section }.distinct().sorted()
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Glossary filters", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search English or Japanese") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = sectionFilter == null, onClick = { sectionFilter = null }, label = { Text("All") })
                    sections.forEach { section ->
                        FilterChip(
                            selected = sectionFilter == section,
                            onClick = { sectionFilter = section },
                            label = { Text(section) },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        search = ""
                        sectionFilter = null
                    },
                    enabled = activeFilterCount > 0,
                ) { Text("Reset filters") }
            }
        }
    }

    editingProposal?.let { proposal ->
        val initial = state.approvalDrafts[proposal.id]
            ?: glossaryViewModel.proposalEntry(proposal, settings, token)
            ?: GlossaryEntry(
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

'''
s = replace_between(
    s,
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun GlossaryWorkspaceScreen(',
    '@Composable\nprivate fun GlossaryProposalCard(',
    glossary_screen,
    'GlossaryWorkspaceScreen',
)

proposal_card = r'''@Composable
private fun GlossaryProposalCard(
    proposal: GlossaryProposal,
    resolvedEntry: GlossaryEntry?,
    approvalPooled: Boolean,
    rejectionPooled: Boolean,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
    onRemoveFromPool: () -> Unit,
    onReject: () -> Unit,
    busy: Boolean,
) {
    var detailsExpanded by rememberSaveable(proposal.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val english = proposal.translatedName?.takeIf { it.isNotBlank() }
                ?: resolvedEntry?.translatedName?.takeIf { it.isNotBlank() }
            val japanese = proposal.sourceAliases.takeIf { it.isNotEmpty() }
                ?: resolvedEntry?.sourceAliases.orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(english ?: "No English value", fontWeight = FontWeight.SemiBold)
                    if (japanese.isNotEmpty()) {
                        Text(japanese.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(proposal.action.replace('_', ' ').uppercase(), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Icon(
                        if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (detailsExpanded) "Hide details" else "Show details",
                    )
                }
            }

            if (detailsExpanded) {
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
                Text(proposal.reason, style = MaterialTheme.typography.bodySmall)
            }

            val pooled = approvalPooled || rejectionPooled
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, "Edit suggestion") }
                TextButton(onClick = onReject, enabled = !busy && !rejectionPooled) {
                    Text(if (rejectionPooled) "Rejected" else "Reject")
                }
                if (pooled) {
                    IconButton(onClick = onRemoveFromPool, enabled = !busy) {
                        Icon(Icons.Default.Undo, "Undo staged decision")
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onApprove,
                    enabled = !busy && !approvalPooled && resolvedEntry != null,
                ) { Text(if (approvalPooled) "Pooled" else "Approve") }
            }
        }
    }
}

'''
s = replace_between(
    s,
    '@Composable\nprivate fun GlossaryProposalCard(',
    '@Composable\nprivate fun GlossaryApprovedEntryCard(',
    proposal_card,
    'GlossaryProposalCard',
)

approved_card = r'''@Composable
private fun GlossaryApprovedEntryCard(entry: GlossaryEntry, onEdit: () -> Unit, busy: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(entry.translatedName, fontWeight = FontWeight.Bold)
                Text(entry.sourceAliases.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                Text(
                    entry.section.uppercase() + if (entry.qaLock) " • QA LOCK" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            IconButton(onClick = onEdit, enabled = !busy) {
                Icon(Icons.Default.Edit, if (entry.qaLock) "Edit QA lock" else "Edit glossary entry")
            }
        }
    }
}

'''
s = replace_between(
    s,
    '@Composable\nprivate fun GlossaryApprovedEntryCard(',
    '@Composable\nprivate fun GlossaryWorkspaceEditDialog(',
    approved_card,
    'GlossaryApprovedEntryCard',
)
write(path, s)


# ---------------- Editor screen ----------------
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt'
s = read(path)
s = add_imports(s, [
    'import androidx.compose.animation.AnimatedVisibility',
    'import androidx.compose.material.icons.filled.CheckCircle',
    'import androidx.compose.material.icons.filled.CloudDone',
    'import androidx.compose.material.icons.filled.DeleteOutline',
    'import androidx.compose.material.icons.filled.Description',
    'import androidx.compose.material.icons.filled.EditNote',
    'import androidx.compose.material.icons.filled.FilterList',
    'import androidx.compose.material.icons.filled.SkipNext',
    'import androidx.compose.material.icons.filled.UploadFile',
    'import androidx.compose.material.icons.filled.ViewAgenda',
    'import androidx.compose.material3.Badge',
    'import androidx.compose.material3.BadgedBox',
    'import androidx.compose.material3.SmallFloatingActionButton',
    'import androidx.compose.material3.ModalBottomSheet',
    'import androidx.compose.runtime.derivedStateOf',
])

editor_screen = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
    initial: OpenChapter,
    busy: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onDraft: (OpenChapter) -> Unit,
    onCommit: (OpenChapter, Boolean) -> Unit,
    onOverride: (OpenChapter, String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importStore = remember { TranslationImportStore(context) }
    var current by remember(initial.file.path, initial.remote.sha) { mutableStateOf(initial) }
    var imported by remember(initial.file.path, initial.document.sourceSha256) {
        mutableStateOf<ImportedTranslationOverlay?>(null)
    }
    var importBusy by remember(initial.file.path, initial.document.sourceSha256) { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(EntryFilter.ALL) }
    var showQa by rememberSaveable { mutableStateOf(false) }
    var showWholeFile by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showCommit by remember { mutableStateOf(false) }
    var showRemoveImportConfirm by remember { mutableStateOf(false) }
    var showRemoveAllConfirm by remember { mutableStateOf(false) }
    var editorNotice by remember { mutableStateOf<String?>(null) }
    var jumpLocator by remember { mutableStateOf<String?>(null) }
    var jumpRequestId by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(initial.file.path, initial.document.sourceSha256) {
        importBusy = true
        imported = withContext(Dispatchers.IO) {
            importStore.load(initial.file.path, initial.document.sourceSha256)
        }
        importBusy = false
    }

    LaunchedEffect(initial.qa?.sha) {
        if (
            initial.file.path == current.file.path &&
            initial.remote.sha == current.remote.sha &&
            initial.qa?.sha != current.qa?.sha
        ) {
            current = current.copy(qa = initial.qa)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val chapter = current
            scope.launch {
                importBusy = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Could not read the selected file.")
                        require(bytes.size <= 20 * 1024 * 1024) { "Translation file is too large to import safely (20 MB limit)." }
                        val fileName = selectedFileName(context, uri) ?: "Imported XLIFF"
                        val overlay = XliffTranslationImporter.parse(
                            raw = String(bytes, Charsets.UTF_8),
                            fileName = fileName,
                            chapterPath = chapter.file.path,
                            document = chapter.document,
                        )
                        require(importStore.save(overlay)) { "Could not persist the imported translation locally." }
                        overlay
                    }
                }.onSuccess { overlay ->
                    imported = overlay
                    editorNotice = "Imported: ${overlay.matchedCount}/${overlay.totalEntries} • ${overlay.alignment} • ${overlay.fileName}"
                }.onFailure {
                    editorNotice = it.message ?: "Could not import this translation file."
                }
                importBusy = false
            }
        }
    }

    fun updateDocument(document: EditorDocument) {
        val raw = IntakeParser.patchDocument(current.raw, document)
        current = current.copy(raw = raw, document = document)
        onDraft(current)
    }

    fun updateEnglish(locator: String, english: String) {
        val index = current.document.entries.indexOfFirst { it.locator == locator }
        require(index >= 0) { "Editor entry not found: $locator" }
        val entries = current.document.entries.toMutableList()
        entries[index] = entries[index].copy(english = english)
        val wasReviewed = current.document.editorReviewComplete
        val document = current.document.copy(entries = entries, editorReviewComplete = false)
        var raw = IntakeParser.patchEnglishAt(current.raw, index, english)
        if (wasReviewed) raw = IntakeParser.patchReviewComplete(raw, false)
        current = current.copy(raw = raw, document = document)
        onDraft(current)
    }

    fun useImported(locator: String, text: String) {
        updateEnglish(locator, text)
    }

    fun fillBlanksFromImport(overlay: ImportedTranslationOverlay) {
        var changed = 0
        val updated = current.document.entries.map { entry ->
            val importedText = overlay.translationFor(entry.locator)
            if (!entry.isSupplied && importedText != null) {
                changed += 1
                entry.copy(english = importedText)
            } else entry
        }
        if (changed > 0) {
            updateDocument(current.document.copy(entries = updated, editorReviewComplete = false))
            editorNotice = "Filled $changed blank English field${if (changed == 1) "" else "s"} from ${overlay.fileName}. Existing English was not overwritten."
        } else {
            editorNotice = "No blank English fields matched this import."
        }
    }

    fun removeAllEnglish() {
        val cleared = current.document.entries.map { it.copy(english = "") }
        updateDocument(current.document.copy(entries = cleared, editorReviewComplete = false))
        editorNotice = "All English entries were cleared."
    }

    fun jumpToFirstMissing() {
        val target = current.document.entries.firstOrNull { !it.isSupplied } ?: return
        showQa = false
        showWholeFile = false
        filter = EntryFilter.ALL
        jumpLocator = target.locator
        jumpRequestId += 1
    }

    val missingEnglish = current.document.entries.filterNot { it.isSupplied }
    val suppliedEnglishCount = current.document.entries.count { it.isSupplied }
    val fillableImportCount = imported?.let { overlay ->
        missingEnglish.count { overlay.translationFor(it.locator) != null }
    } ?: 0
    val filterCounts = EntryFilter.entries.associateWith { item -> filteredEntries(current.document, item).size }
    val entries = filteredEntries(current.document, filter)
    val headerExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48 }
    }
    val activeFilterCount = (if (showQa) 1 else 0) + (if (!showQa && filter != EntryFilter.ALL) 1 else 0)

    LaunchedEffect(jumpRequestId, filter, showQa, showWholeFile) {
        val locator = jumpLocator
        if (jumpRequestId > 0 && locator != null && !showQa && !showWholeFile) {
            val target = entries.indexOfFirst { it.locator == locator }
            if (target >= 0) listState.animateScrollToItem(target)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "V${current.file.volume} Ch ${current.file.chapter} • ${current.document.englishSupplied}/${current.document.englishTotal}",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    Icon(
                        if (current.raw == current.remote.content) Icons.Default.CloudDone else Icons.Default.EditNote,
                        if (current.raw == current.remote.content) "No changes" else "Unsaved local changes",
                    )
                    BadgedBox(
                        badge = { if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) } },
                    ) {
                        IconButton(
                            onClick = { showFilters = true },
                            enabled = !busy && !showWholeFile,
                        ) {
                            Icon(Icons.Default.FilterList, "Editor filters")
                        }
                    }
                    IconButton(
                        onClick = {
                            showWholeFile = !showWholeFile
                            showQa = false
                        },
                        enabled = !busy,
                    ) {
                        Icon(
                            if (showWholeFile) Icons.Default.ViewAgenda else Icons.Default.Description,
                            if (showWholeFile) "Show cards" else "Show whole file",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (missingEnglish.isNotEmpty() && !busy) {
                SmallFloatingActionButton(
                    onClick = { jumpToFirstMissing() },
                    modifier = Modifier.padding(bottom = 52.dp),
                ) {
                    BadgedBox(badge = { Badge { Text(missingEnglish.size.toString()) } }) {
                        Icon(Icons.Default.SkipNext, "Go to first unsupplied English field")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            editorNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            AnimatedVisibility(visible = showWholeFile || showQa || headerExpanded) {
                Text(
                    buildString {
                        append(current.document.englishTitle.ifBlank { "Untitled chapter" })
                        append(if (current.document.editorReviewComplete) " • Reviewed" else " • Review pending")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            }

            if (showWholeFile) {
                WholeFileEditor(
                    raw = current.raw,
                    onRawChange = { raw ->
                        val parsed = runCatching { IntakeParser.parse(raw) }.getOrNull()
                        current = current.copy(raw = raw, document = parsed ?: current.document)
                        onDraft(current)
                        editorNotice = null
                    },
                    onValidate = {
                        runCatching {
                            IntakeParser.validate(current.raw).getOrThrow()
                            val parsed = IntakeParser.parse(current.raw)
                            current = current.copy(document = parsed)
                            onDraft(current)
                        }.onSuccess {
                            editorNotice = "Whole file is valid and synced."
                        }.onFailure {
                            editorNotice = it.message ?: "Whole file is invalid."
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                imported?.let { overlay ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (fillableImportCount > 0) {
                            TextButton(
                                onClick = { fillBlanksFromImport(overlay) },
                                enabled = !busy && !importBusy,
                            ) { Text("Fill blanks ($fillableImportCount)") }
                        } else {
                            TextButton(
                                onClick = { showRemoveAllConfirm = true },
                                enabled = !busy && !importBusy && suppliedEnglishCount > 0,
                            ) { Text("Remove all") }
                        }
                    }
                }
                if (showQa) {
                    QaFindingsView(
                        snapshot = current.qa,
                        editorSha = QaFindingsParser.sha256(current.raw),
                        busy = busy,
                        onOverride = { id, reason -> onOverride(current, id, reason) },
                        onShowParagraph = { locator ->
                            showQa = false
                            showWholeFile = false
                            filter = EntryFilter.ALL
                            jumpLocator = locator
                            jumpRequestId += 1
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries, key = { it.locator }) { entry ->
                            EntryCard(
                                entry = entry,
                                importedTranslation = imported?.translationFor(entry.locator),
                                onUseImport = { text -> useImported(entry.locator, text) },
                                onEnglishChange = { english -> updateEnglish(entry.locator, english) },
                            )
                        }
                        if (entries.isEmpty()) item { Text("No entries in this filter.", modifier = Modifier.padding(12.dp)) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (imported == null) {
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("application/xml", "text/xml", "application/octet-stream", "*/*")) },
                        enabled = !busy && !importBusy,
                    ) {
                        Icon(Icons.Default.UploadFile, if (importBusy) "Loading import" else "Import translation")
                    }
                } else {
                    IconButton(onClick = { showRemoveImportConfirm = true }, enabled = !busy && !importBusy) {
                        Icon(Icons.Default.DeleteOutline, "Remove imported translation")
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { showCommit = true }, enabled = !busy && !importBusy) { Text("Review & commit") }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Editor filters", style = MaterialTheme.typography.titleMedium)
                Text("View", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !showQa,
                        onClick = { showQa = false },
                        label = { Text("Entries") },
                    )
                    FilterChip(
                        selected = showQa,
                        onClick = { showQa = true },
                        label = { Text("QA findings") },
                    )
                }
                if (!showQa) {
                    Text("Entries", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EntryFilter.entries.forEach { item ->
                            FilterChip(
                                selected = filter == item,
                                onClick = { filter = item },
                                label = { Text("${item.label} (${filterCounts.getValue(item)})") },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        showQa = false
                        filter = EntryFilter.ALL
                    },
                    enabled = activeFilterCount > 0,
                ) { Text("Reset filters") }
            }
        }
    }

    if (showRemoveAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAllConfirm = false },
            title = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", style = MaterialTheme.typography.displayLarge)
                    Text("Remove all English entries?")
                }
            },
            text = {
                Text("This will clear every English entry in this chapter and mark editor review as pending. The cleared chapter will be saved as your local draft.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveAllConfirm = false
                        removeAllEnglish()
                    },
                    enabled = !busy && !importBusy,
                ) { Text("Remove all") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAllConfirm = false }, enabled = !busy && !importBusy) { Text("Cancel") }
            },
        )
    }

    if (showRemoveImportConfirm) {
        AlertDialog(
            onDismissRequest = { if (!importBusy) showRemoveImportConfirm = false },
            title = { Text("Remove imported translation?") },
            text = { Text("Remove the local imported translation? English already copied into fields will not be changed.") },
            confirmButton = {
                Button(
                    onClick = {
                        val path = current.file.path
                        showRemoveImportConfirm = false
                        scope.launch {
                            importBusy = true
                            val removed = withContext(Dispatchers.IO) { importStore.delete(path) }
                            if (removed) {
                                imported = null
                                editorNotice = "Imported translation removed."
                            } else {
                                editorNotice = "Could not remove the imported translation."
                            }
                            importBusy = false
                        }
                    },
                    enabled = !busy && !importBusy,
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveImportConfirm = false }, enabled = !busy && !importBusy) { Text("Cancel") }
            },
        )
    }

    if (showCommit) {
        AlertDialog(
            onDismissRequest = { showCommit = false },
            title = { Text("Commit chapter changes?") },
            text = { Text("Mark reviewed only after checking the full chapter. Every English field must be supplied first. Imported translations stay local until you use them; Fill blanks and Use Import copy them into authoritative English fields.") },
            confirmButton = { Button(onClick = { showCommit = false; onCommit(current, true) }, enabled = !busy && !importBusy) { Text("Mark reviewed & commit") } },
            dismissButton = { TextButton(onClick = { showCommit = false; onCommit(current, false) }, enabled = !busy && !importBusy) { Text("Commit without review") } },
        )
    }
}

'''
s = replace_between(
    s,
    '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun EditorScreen(',
    'private fun selectedFileName(',
    editor_screen,
    'EditorScreen',
)

entry_card = r'''@Composable
private fun EntryCard(
    entry: EditorEntry,
    importedTranslation: String?,
    onUseImport: (String) -> Unit,
    onEnglishChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val focusRequester = remember(entry.locator) { FocusRequester() }
    var rich by remember(entry.locator) {
        mutableStateOf(
            runCatching { RichInlineState.fromMarkup(entry.english) }
                .getOrElse { RichInlineState.plain(InlineMarkup.visibleText(entry.english)) },
        )
    }
    LaunchedEffect(entry.english) {
        if (entry.english != rich.toMarkup()) {
            rich = runCatching { RichInlineState.fromMarkup(entry.english) }
                .getOrElse { RichInlineState.plain(InlineMarkup.visibleText(entry.english)) }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entry.locator} • ${if (entry.isSupplied) "Supplied" else "Unsupplied"}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val reference = importedTranslation?.let { "\n\nIMPORTED TRANSLATION:\n$it" }.orEmpty()
                    clipboard.setPrimaryClip(ClipData.newPlainText("raw ${entry.locator}", "RAW:\n${entry.sourceJapanese}$reference"))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    rich = rich.moveCaretToEnd()
                    focusRequester.requestFocus()
                }) { Icon(Icons.Default.ContentCopy, "Copy raw and imported text") }
            }
            DisplayBlock("Raw", entry.sourceJapanese)
            importedTranslation?.let { DisplayBlock("Imported", it) }
            OutlinedTextField(
                value = rich.asTextFieldValue(),
                onValueChange = { next ->
                    rich = rich.edited(next)
                    onEnglishChange(rich.toMarkup())
                },
                label = { Text("English") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        rich = rich.toggle(InlineStyle.BOLD)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    },
                    enabled = rich.hasSelection(),
                ) { Text("B", fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = {
                        rich = rich.toggle(InlineStyle.ITALIC)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    },
                    enabled = rich.hasSelection(),
                ) { Text("I", fontStyle = FontStyle.Italic) }
                Spacer(Modifier.weight(1f))
                importedTranslation?.let { importedText ->
                    TextButton(onClick = {
                        rich = RichInlineState.plain(importedText)
                        onUseImport(importedText)
                        focusRequester.requestFocus()
                    }) { Text("Use Import") }
                }
                IconButton(onClick = {
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isNotEmpty()) {
                        rich = RichInlineState.fromClipboard(text)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    }
                }, enabled = clipboard.hasPrimaryClip()) {
                    Icon(Icons.Default.ContentPaste, "Paste English")
                }
            }
        }
    }
}

'''
s = replace_between(
    s,
    '@Composable\nprivate fun EntryCard(',
    '@Composable\nprivate fun WholeFileEditor(',
    entry_card,
    'EntryCard',
)

whole_file = r'''@Composable
private fun WholeFileEditor(raw: String, onRawChange: (String) -> Unit, onValidate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Direct schema-v5 YAML editor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onValidate) { Icon(Icons.Default.CheckCircle, "Validate whole file") }
        }
        Text("Exceptional edits only. Normal editing should change English fields in Cards view.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = raw, onValueChange = onRawChange, modifier = Modifier.fillMaxWidth().weight(1f), minLines = 12)
    }
}

'''
s = replace_between(
    s,
    '@Composable\nprivate fun WholeFileEditor(',
    '@Composable\nprivate fun DisplayBlock(',
    whole_file,
    'WholeFileEditor',
)
s = s.replace('Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {\n        Text(title, fontWeight = FontWeight.SemiBold)',
              'Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {\n        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)')
s = s.replace('Column(modifier.padding(12.dp)) { Text("No machine-readable QA findings have been recorded for this chapter yet.") }',
              'Column(modifier.padding(8.dp)) { Text("No machine-readable QA findings have been recorded for this chapter yet.") }')
s = s.replace('LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {',
              'LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {')
s = s.replace('Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {',
              'Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {')
write(path, s)


# ---------------- Glossary ViewModel test for local clear ----------------
path = 'app/src/test/java/cloud/shadowmonarchbooks/intakeedit/GlossaryViewModelTest.kt'
s = read(path)
anchor = '    @Test\n    fun commitPooledWritesBatchAndClearsPool() = runTest(dispatcher) {'
test = r'''    @Test
    fun clearPooledDecisionsStaysLocal() = runTest(dispatcher) {
        val snapshot = glossarySnapshot(proposalStatus = "pending")
        val repository = FakeGlossaryRepository(snapshot, snapshot)
        val viewModel = GlossaryViewModel(GlossaryRepositoryFactory { _, _ -> repository })
        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()
        val proposal = snapshot.documents.pendingProposals.single()

        viewModel.poolRejection(proposal)
        assertEquals(1, viewModel.uiState.value.pooledDecisionCount)
        viewModel.clearPooledDecisions()

        assertEquals(0, viewModel.uiState.value.pooledDecisionCount)
        assertFalse(repository.mutated)
    }

'''
if anchor not in s:
    raise AssertionError('GlossaryViewModelTest anchor not found')
s = s.replace(anchor, test + anchor, 1)
write(path, s)


# ---------------- Version bump ----------------
path = 'app/build.gradle.kts'
s = read(path)
if 'versionCode = 22' not in s or 'versionName = "0.7.3"' not in s:
    raise AssertionError('Expected v0.7.3 version markers not found')
s = s.replace('versionCode = 22', 'versionCode = 23', 1)
s = s.replace('versionName = "0.7.3"', 'versionName = "0.8.0"', 1)
write(path, s)

print('UI density pass applied')
