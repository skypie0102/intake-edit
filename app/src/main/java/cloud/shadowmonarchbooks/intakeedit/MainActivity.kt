package cloud.shadowmonarchbooks.intakeedit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IntakeEditTheme { IntakeApp() } }
    }
}

@Composable
private fun IntakeEditTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

private enum class ChapterListFilter(val label: String) { ACTIVE("Active"), COMPLETED("Completed"), ALL("All") }
private enum class EntryFilter(val label: String) {
    NEEDS_ATTENTION("Needs Attention"), ALL("All"), RESTRICTED("Restricted"), REVISED("Revised"), UNREVISED("Unrevised")
}

private data class ChapterProgress(
    val restrictedSupplied: Int,
    val restrictedTotal: Int,
    val safeRevised: Int,
    val editorReviewComplete: Boolean,
    val qaActive: Int = 0,
    val qaTotal: Int = 0,
) {
    val complete: Boolean get() = editorReviewComplete && restrictedSupplied == restrictedTotal
}

private data class OpenChapter(
    val file: ChapterFile,
    val remote: FileSnapshot,
    val raw: String,
    val document: EditorDocument,
    val qa: QaFindingsSnapshot?,
)

@Composable
private fun IntakeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val tokenStore = remember { SecureTokenStore(context) }
    val draftStore = remember { DraftStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var token by remember { mutableStateOf(tokenStore.load()) }
    var showSettings by remember { mutableStateOf(token.isBlank()) }
    var files by remember { mutableStateOf<List<ChapterFile>>(emptyList()) }
    var progressByPath by remember { mutableStateOf<Map<String, ChapterProgress>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<OpenChapter?>(null) }
    val api = remember(settings, token) { token.takeIf { it.isNotBlank() }?.let { GitHubApi(settings, it) } }

    suspend fun loadQa(client: GitHubApi, file: ChapterFile): QaFindingsSnapshot? {
        val path = QaFindingsParser.path(file.volume, file.chapter)
        val snapshot = client.getFileOrNull(path) ?: return null
        return QaFindingsSnapshot(path, snapshot.sha, snapshot.content, QaFindingsParser.parse(snapshot.content))
    }

    suspend fun loadChapterProgress(client: GitHubApi, file: ChapterFile): ChapterProgress {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val qa = runCatching { loadQa(client, file) }.getOrNull()
        val editorSha = QaFindingsParser.sha256(remote.content)
        return ChapterProgress(
            restrictedSupplied = document.restrictedSupplied,
            restrictedTotal = document.restrictedTotal,
            safeRevised = document.safeRevised,
            editorReviewComplete = document.editorReviewComplete,
            qaActive = qa?.document?.active(editorSha)?.size ?: 0,
            qaTotal = qa?.document?.findings?.size ?: 0,
        )
    }

    fun refresh() {
        val client = api ?: return
        scope.launch {
            busy = true
            try {
                val listed = client.listIntakeFiles()
                files = listed
                progressByPath = emptyMap()
                listed.forEach { file ->
                    launch {
                        runCatching { loadChapterProgress(client, file) }
                            .onSuccess { progress -> progressByPath = progressByPath + (file.path to progress) }
                    }
                }
                notice = null
            }
            catch (t: Throwable) { notice = t.message ?: "Could not refresh chapter list." }
            finally { busy = false }
        }
    }

    fun openFile(file: ChapterFile) {
        val client = api ?: return
        scope.launch {
            busy = true
            try {
                val remote = client.getFile(file.path)
                val draft = draftStore.load(file.path)
                val raw = if (draft?.baseSha == remote.sha) draft.raw else remote.content
                val document = IntakeParser.parse(raw)
                val qa = runCatching { loadQa(client, file) }.getOrNull()
                open = OpenChapter(file, remote, raw, document, qa)
                if (draft != null && draft.baseSha != remote.sha) notice = "A local draft exists for an older GitHub revision. The latest remote copy was opened to avoid an unsafe overwrite."
            } catch (t: Throwable) { notice = t.message ?: "Could not open ${file.path}." }
            finally { busy = false }
        }
    }

    LaunchedEffect(api, showSettings) { if (api != null && !showSettings) refresh() }

    if (showSettings) {
        SettingsScreen(
            currentSettings = settings, currentToken = token, busy = busy, canCancel = token.isNotBlank(),
            onCancel = { showSettings = false },
            onSave = { next, nextToken ->
                scope.launch {
                    busy = true
                    try {
                        val candidate = GitHubApi(next, nextToken)
                        val login = candidate.verifyUser()
                        settingsStore.save(next); tokenStore.save(nextToken)
                        settings = next; token = nextToken; showSettings = false; notice = "Connected as $login."
                    } catch (t: Throwable) { notice = t.message ?: "Could not verify GitHub connection." }
                    finally { busy = false }
                }
            },
        )
        return
    }

    val chapter = open
    if (chapter != null) {
        BackHandler(enabled = !busy) { open = null }
        EditorScreen(
            initial = chapter, busy = busy, notice = notice, onBack = { open = null },
            onDraft = { next -> open = next; draftStore.save(next.file.path, next.remote.sha, next.raw) },
            onCommit = { next, markReviewed ->
                val client = api ?: return@EditorScreen
                scope.launch {
                    busy = true
                    try {
                        val document = if (markReviewed) {
                            require(next.document.restrictedSupplied == next.document.restrictedTotal) { "Supply every restricted English field before marking editor review complete." }
                            next.document.copy(editorReviewComplete = true)
                        } else next.document
                        val raw = IntakeParser.patchDocument(next.raw, document)
                        IntakeParser.validate(raw).getOrThrow()
                        val newSha = client.updateFile(next.file.path, next.remote.sha, raw, "edit: revise ch_${next.file.chapter.toString().padStart(4, '0')} English")
                        draftStore.delete(next.file.path)
                        val remote = next.remote.copy(sha = newSha.ifBlank { next.remote.sha }, content = raw)
                        notice = "Committed to ${settings.owner}/${settings.repo}."
                        open = null
                        refresh()
                    } catch (t: Throwable) { notice = t.message ?: "Commit failed." }
                    finally { busy = false }
                }
            },
            onOverride = { next, findingId, reason ->
                val client = api ?: return@EditorScreen
                val snapshot = next.qa ?: return@EditorScreen
                scope.launch {
                    busy = true
                    try {
                        val override = QaOverride(reason, Instant.now().toString(), client.verifyUser(), QaFindingsParser.sha256(next.raw))
                        val document = snapshot.document.withOverride(findingId, override)
                        val raw = QaFindingsParser.serialize(document)
                        val sha = client.updateFile(snapshot.path, snapshot.sha, raw, "qa: override $findingId")
                        open = next.copy(qa = snapshot.copy(sha = sha.ifBlank { snapshot.sha }, raw = raw, document = document))
                        notice = "QA override committed."
                    } catch (t: Throwable) { notice = t.message ?: "Could not commit QA override." }
                    finally { busy = false }
                }
            },
        )
    } else {
        ChapterListScreen(files, progressByPath, busy, notice, ::refresh, { showSettings = true }, ::openFile)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListScreen(
    files: List<ChapterFile>, progressByPath: Map<String, ChapterProgress>, busy: Boolean, notice: String?, onRefresh: () -> Unit,
    onSettings: () -> Unit, onOpen: (ChapterFile) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ChapterListFilter.ACTIVE) }
    val searched = files.filter { val q = search.trim(); q.isBlank() || it.path.contains(q, true) || it.chapter.toString().contains(q) }
    val visible = searched.filter { file ->
        val progress = progressByPath[file.path]
        when (filter) {
            ChapterListFilter.ACTIVE -> progress?.complete != true
            ChapterListFilter.COMPLETED -> progress?.complete == true
            ChapterListFilter.ALL -> true
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Intake Edit") }, actions = {
            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Refresh, "Refresh") }
            IconButton(onClick = onSettings, enabled = !busy) { Icon(Icons.Default.Settings, "Settings") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(search, { search = it }, label = { Text("Chapter, path, or filename") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChapterListFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
            }
            if (busy && files.isEmpty()) CircularProgressIndicator()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.path }) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val progress = progressByPath[file.path]
                                Text("Volume ${file.volume} • Chapter ${file.chapter}", fontWeight = FontWeight.Bold)
                                Text(file.path, style = MaterialTheme.typography.bodySmall)
                                if (progress == null) {
                                    Text("English supplied: loading", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("English supplied: ${progress.restrictedSupplied}/${progress.restrictedTotal}", style = MaterialTheme.typography.bodySmall)
                                    Text("Safe revisions: ${progress.safeRevised} • Review: ${if (progress.editorReviewComplete) "complete" else "pending"}", style = MaterialTheme.typography.bodySmall)
                                    if (progress.qaTotal > 0) Text("QA: ${progress.qaActive} active / ${progress.qaTotal} total", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = { onOpen(file) }, enabled = !busy) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    initial: OpenChapter, busy: Boolean, notice: String?, onBack: () -> Unit,
    onDraft: (OpenChapter) -> Unit, onCommit: (OpenChapter, Boolean) -> Unit,
    onOverride: (OpenChapter, String, String) -> Unit,
) {
    var current by remember(initial.file.path, initial.remote.sha) { mutableStateOf(initial) }
    var filter by rememberSaveable { mutableStateOf(EntryFilter.NEEDS_ATTENTION) }
    var showQa by rememberSaveable { mutableStateOf(false) }
    var showWholeFile by rememberSaveable { mutableStateOf(false) }
    var showCommit by remember { mutableStateOf(false) }
    var editorNotice by remember { mutableStateOf<String?>(null) }
    var jumpLocator by remember { mutableStateOf<String?>(null) }
    var jumpRequestId by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    fun updateDocument(document: EditorDocument) {
        val raw = IntakeParser.patchDocument(current.raw, document)
        current = current.copy(raw = raw, document = document); onDraft(current)
    }
    val missingRestricted = current.document.entries.filter { it.isRestricted && !InlineMarkup.hasVisibleText(it.english) }
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
    if (showCommit) {
        AlertDialog(
            onDismissRequest = { showCommit = false }, title = { Text("Commit chapter changes?") },
            text = { Text("Mark reviewed only after checking the full chapter. All restricted English must be supplied first.") },
            confirmButton = { Button(onClick = { showCommit = false; onCommit(current, true) }, enabled = !busy) { Text("Mark reviewed & commit") } },
            dismissButton = { TextButton(onClick = { showCommit = false; onCommit(current, false) }, enabled = !busy) { Text("Commit without review") } },
        )
    }
}

private fun filteredEntries(document: EditorDocument, filter: EntryFilter): List<EditorEntry> = when (filter) {
    EntryFilter.NEEDS_ATTENTION -> {
        val missing = document.entries.filter { it.isRestricted && !InlineMarkup.hasVisibleText(it.english) }
        if (missing.isNotEmpty()) missing else if (!document.editorReviewComplete) document.entries else emptyList()
    }
    EntryFilter.ALL -> document.entries
    EntryFilter.RESTRICTED -> document.entries.filter { it.isRestricted }
    EntryFilter.REVISED -> document.entries.filter { it.isSafeRevised }
    EntryFilter.UNREVISED -> document.entries.filter { !it.isRestricted && !it.isSafeRevised }
}

@Composable
private fun EntryCard(entry: EditorEntry, onEnglishChange: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val focusRequester = remember(entry.locator) { FocusRequester() }
    var value by remember(entry.locator) { mutableStateOf(TextFieldValue(entry.english, selection = TextRange(entry.english.length))) }
    LaunchedEffect(entry.english) { if (entry.english != value.text) value = TextFieldValue(entry.english, selection = TextRange(entry.english.length)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(entry.locator, fontWeight = FontWeight.Bold); Text(if (entry.isRestricted) "RESTRICTED" else "SAFE", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = {
                    val referenceTitle = if (entry.isRestricted) "SANITIZED TRANSLATION" else "DRAFT TRANSLATION"
                    clipboard.setPrimaryClip(ClipData.newPlainText("raw + reference ${entry.locator}", "RAW:\n${entry.sourceJapanese}\n\n$referenceTitle:\n${entry.referenceTranslation}"))
                    value = value.copy(selection = TextRange(value.text.length)); focusRequester.requestFocus()
                }) { Icon(Icons.Default.ContentCopy, null); Text("Copy both") }
            }
            DisplayBlock("Raw", entry.sourceJapanese)
            DisplayBlock(if (entry.isRestricted) "Sanitized Translation" else "Draft Translation", entry.referenceTranslation)
            OutlinedTextField(value = value, onValueChange = { value = it; onEnglishChange(it.text) }, label = { Text(if (entry.isRestricted) "English Supplied" else "English Revision") }, minLines = 3, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { value = applyInlineFormat(value, "b"); onEnglishChange(value.text); focusRequester.requestFocus() }) { Text("B", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { value = applyInlineFormat(value, "i"); onEnglishChange(value.text); focusRequester.requestFocus() }) { Text("I", fontStyle = FontStyle.Italic) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isNotEmpty()) { value = TextFieldValue(text, selection = TextRange(text.length)); onEnglishChange(text); focusRequester.requestFocus() }
                }, enabled = clipboard.hasPrimaryClip()) { Icon(Icons.Default.ContentPaste, null); Text("Paste") }
            }
        }
    }
}

private fun applyInlineFormat(value: TextFieldValue, tag: String): TextFieldValue {
    val open = "[$tag]"; val close = "[/$tag]"
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val updated = buildString(value.text.length + open.length + close.length) { append(value.text, 0, start); append(open); append(value.text, start, end); append(close); append(value.text, end, value.text.length) }
    val selection = if (start == end) TextRange(start + open.length) else TextRange(start + open.length, end + open.length)
    return TextFieldValue(updated, selection = selection)
}

@Composable
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

@Composable
private fun DisplayBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(if (value.isBlank()) "—" else value) }
}

@Composable
private fun QaFindingsView(
    snapshot: QaFindingsSnapshot?,
    editorSha: String,
    busy: Boolean,
    onOverride: (String, String) -> Unit,
    onShowParagraph: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot == null) { Column(modifier.padding(12.dp)) { Text("No machine-readable QA findings have been recorded for this chapter yet.") }; return }
    var pending by remember { mutableStateOf<QaFinding?>(null) }; var reason by remember { mutableStateOf("") }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(snapshot.document.findings, key = { it.id }) { finding ->
            val overridden = finding.override?.editorContentSha256 == editorSha
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${finding.severity.uppercase()} • ${finding.category}", fontWeight = FontWeight.Bold)
                    if (finding.locator.isNotBlank()) Text(finding.locator)
                    Text(finding.message)
                    Text(if (overridden) "OVERRIDDEN" else if (finding.override != null) "ACTIVE • old override stale" else "ACTIVE")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (finding.locator.isNotBlank()) TextButton(onClick = { onShowParagraph(finding.locator) }, enabled = !busy) { Text("Show paragraph") }
                        if (finding.overridable) TextButton(onClick = { pending = finding }, enabled = !busy && !overridden) { Text("Override finding") }
                    }
                }
            }
        }
    }
    pending?.let { finding ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Override QA finding?") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Override reason") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { pending = null; onOverride(finding.id, reason.trim()); reason = "" }, enabled = reason.isNotBlank() && !busy) { Text("Confirm override") } },
            dismissButton = { TextButton(onClick = { pending = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(currentSettings: RepoSettings, currentToken: String, busy: Boolean, canCancel: Boolean, onCancel: () -> Unit, onSave: (RepoSettings, String) -> Unit) {
    var owner by rememberSaveable { mutableStateOf(currentSettings.owner) }; var repo by rememberSaveable { mutableStateOf(currentSettings.repo) }
    var branch by rememberSaveable { mutableStateOf(currentSettings.branch) }; var root by rememberSaveable { mutableStateOf(currentSettings.intakeRoot) }; var token by rememberSaveable { mutableStateOf(currentToken) }
    Scaffold(topBar = { TopAppBar(title = { Text("Repository settings") }, navigationIcon = { if (canCancel) IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connect directly to GitHub. The token is encrypted with a key held by Android Keystore.")
            OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(root, { root = it }, label = { Text("Editor root") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text("Fine-grained GitHub token") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Text("Token permissions: Metadata read + Contents read/write for the translation repository.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onSave(RepoSettings(owner.trim(), repo.trim(), branch.trim(), root.trim().trim('/')), token.trim()) }, enabled = !busy && owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank() && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text("Test connection & save")
            }
        }
    }
}
