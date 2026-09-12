from pathlib import Path

path = Path('app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt')
text = path.read_text(encoding='utf-8')

text = text.replace(
    'import androidx.compose.foundation.lazy.items\n',
    'import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.rememberLazyListState\n',
    1,
)

needle = '''    var filter by rememberSaveable { mutableStateOf(EntryFilter.NEEDS_ATTENTION) }
    var showQa by rememberSaveable { mutableStateOf(false) }
    var showCommit by remember { mutableStateOf(false) }
'''
replacement = '''    var filter by rememberSaveable { mutableStateOf(EntryFilter.NEEDS_ATTENTION) }
    var showQa by rememberSaveable { mutableStateOf(false) }
    var showWholeFile by rememberSaveable { mutableStateOf(false) }
    var showCommit by remember { mutableStateOf(false) }
    var editorNotice by remember { mutableStateOf<String?>(null) }
    var jumpLocator by remember { mutableStateOf<String?>(null) }
    var jumpRequestId by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''    Scaffold(topBar = {
        TopAppBar(title = { Text("V${current.file.volume} Ch ${current.file.chapter}") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Default.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(current.document.englishTitle, style = MaterialTheme.typography.titleMedium)
            Text("Restricted supplied: ${current.document.restrictedSupplied}/${current.document.restrictedTotal} • Safe revisions: ${current.document.safeRevised}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !showQa, onClick = { showQa = false }, label = { Text("Entries") })
                FilterChip(selected = showQa, onClick = { showQa = true }, label = { Text("QA Findings") })
            }
            if (showQa) {
                QaFindingsView(current.qa, QaFindingsParser.sha256(current.raw), busy, { id, reason -> onOverride(current, id, reason) }, Modifier.weight(1f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EntryFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
                }
                val entries = filteredEntries(current.document, filter)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries, key = { it.locator }) { entry ->
                        EntryCard(entry) { english ->
                            val updated = current.document.entries.map { if (it.locator == entry.locator) it.copy(english = english) else it }
                            updateDocument(current.document.copy(entries = updated, editorReviewComplete = false))
                        }
                    }
                    if (entries.isEmpty()) item { Text("No entries in this filter.", modifier = Modifier.padding(16.dp)) }
                }
            }
            Button(onClick = { showCommit = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Review & commit") }
        }
    }
'''
replacement = '''    val missingRestricted = current.document.entries.filter { it.isRestricted && !InlineMarkup.hasVisibleText(it.english) }
    val entries = filteredEntries(current.document, filter)
    LaunchedEffect(jumpRequestId, filter, showQa, showWholeFile) {
        val locator = jumpLocator
        if (jumpRequestId > 0 && locator != null && !showQa && !showWholeFile) {
            val target = entries.indexOfFirst { it.locator == locator }
            if (target >= 0) listState.animateScrollToItem(target)
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("V${current.file.volume} Ch ${current.file.chapter}") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Default.ArrowBack, "Back") }
            },
            actions = {
                Text(if (current.raw == current.remote.content) "No changes" else "Changes", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { showWholeFile = !showWholeFile; showQa = false }, enabled = !busy) {
                    Text(if (showWholeFile) "Cards" else "Whole")
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            editorNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(current.document.englishTitle, style = MaterialTheme.typography.titleMedium)
            Text("Restricted supplied: ${current.document.restrictedSupplied}/${current.document.restrictedTotal} • Safe revisions: ${current.document.safeRevised}")
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !showQa, onClick = { showQa = false }, label = { Text("Entries") })
                    FilterChip(selected = showQa, onClick = { showQa = true }, label = { Text("QA Findings") })
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EntryFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(entries, key = { it.locator }) { entry ->
                            EntryCard(entry) { english ->
                                val updated = current.document.entries.map { if (it.locator == entry.locator) it.copy(english = english) else it }
                                updateDocument(current.document.copy(entries = updated, editorReviewComplete = false))
                            }
                        }
                        if (entries.isEmpty()) item { Text("No entries in this filter.", modifier = Modifier.padding(16.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val target = missingRestricted.firstOrNull() ?: return@TextButton
                        showQa = false
                        showWholeFile = false
                        filter = EntryFilter.ALL
                        jumpLocator = target.locator
                        jumpRequestId += 1
                    },
                    enabled = !busy && missingRestricted.isNotEmpty(),
                ) { Text("Next (${missingRestricted.size.toString().padStart(2, '0')})") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { showCommit = true }, enabled = !busy) { Text("Review & commit") }
            }
        }
    }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''@Composable
private fun DisplayBlock(title: String, value: String) {
'''
whole_file = '''@Composable
private fun WholeFileEditor(raw: String, onRawChange: (String) -> Unit, onValidate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Direct JSON-compatible YAML editor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onValidate) { Text("Validate") }
        }
        Text("Exceptional edits only. Normal editing should change English fields in Cards view.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = raw,
            onValueChange = onRawChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            minLines = 12,
        )
    }
}

'''
assert needle in text
text = text.replace(needle, whole_file + needle, 1)

needle = '''private fun QaFindingsView(snapshot: QaFindingsSnapshot?, editorSha: String, busy: Boolean, onOverride: (String, String) -> Unit, modifier: Modifier = Modifier) {
'''
replacement = '''private fun QaFindingsView(
    snapshot: QaFindingsSnapshot?,
    editorSha: String,
    busy: Boolean,
    onOverride: (String, String) -> Unit,
    onShowParagraph: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''                    Text(if (overridden) "OVERRIDDEN" else if (finding.override != null) "ACTIVE • old override stale" else "ACTIVE")
                    if (finding.overridable) TextButton(onClick = { pending = finding }, enabled = !busy && !overridden) { Text("Override finding") }
'''
replacement = '''                    Text(if (overridden) "OVERRIDDEN" else if (finding.override != null) "ACTIVE • old override stale" else "ACTIVE")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (finding.locator.isNotBlank()) TextButton(onClick = { onShowParagraph(finding.locator) }, enabled = !busy) { Text("Show paragraph") }
                        if (finding.overridable) TextButton(onClick = { pending = finding }, enabled = !busy && !overridden) { Text("Override finding") }
                    }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

path.write_text(text, encoding='utf-8')
print('Applied editor navigation / whole-file parity patch')
