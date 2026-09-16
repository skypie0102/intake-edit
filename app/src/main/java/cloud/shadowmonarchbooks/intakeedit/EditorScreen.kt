package cloud.shadowmonarchbooks.intakeedit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EntryFilter(val label: String) {
    ALL("All"), NEEDS_ATTENTION("Attention"), SUPPLIED("Supplied"), UNSUPPLIED("Unsupplied")
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val focusManager = LocalFocusManager.current
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
    var showOverflow by remember { mutableStateOf(false) }
    var showCommit by remember { mutableStateOf(false) }
    var showRemoveImportConfirm by remember { mutableStateOf(false) }
    var showRemoveAllConfirm by remember { mutableStateOf(false) }
    var editorNotice by remember { mutableStateOf<String?>(null) }
    var jumpLocator by remember { mutableStateOf<String?>(null) }
    var jumpRequestId by remember { mutableStateOf(0) }
    var lastSuggestionLocator by remember(initial.file.path, initial.remote.sha) { mutableStateOf<String?>(null) }
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
        val document = EndnoteIntegrity.prune(
            current.document.copy(entries = entries, editorReviewComplete = false),
        )
        updateDocument(document)
    }

    fun saveEndnote(locator: String, english: String, note: EndnoteDefinition) {
        updateDocument(EndnoteIntegrity.upsert(current.document, locator, english, note))
        editorNotice = "Endnote saved."
    }

    fun removeEndnote(locator: String, english: String, noteId: String) {
        updateDocument(EndnoteIntegrity.remove(current.document, locator, english, noteId))
        editorNotice = "Endnote removed."
    }

    fun stageProposalDecision(proposalId: String, status: String) {
        val snapshot = current.endnoteProposals ?: return
        val staged = EndnoteProposalParser.stage(snapshot, proposalId, status)
        current = current.copy(endnoteProposals = staged)
        onDraft(current)
        editorNotice = when (status) {
            "accepted" -> "Endnote suggestion accepted. Its decision will commit with this chapter."
            "rejected" -> "Endnote suggestion dismissed. Its decision will commit with this chapter."
            else -> editorNotice
        }
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
        updateDocument(
            current.document.copy(
                entries = cleared,
                endnotes = emptyList(),
                editorReviewComplete = false,
            ),
        )
        editorNotice = "All English entries and their endnotes were cleared."
    }

    fun jumpToFirstMissing() {
        val target = current.document.entries.firstOrNull { !it.isSupplied } ?: return
        focusManager.clearFocus(force = true)
        showQa = false
        showWholeFile = false
        filter = EntryFilter.ALL
        jumpLocator = target.locator
        jumpRequestId += 1
    }

    fun cycleUnusedEndnoteSuggestions() {
        val targets = pendingSuggestionLocators(current)
        if (targets.isEmpty()) return
        val currentIndex = targets.indexOf(lastSuggestionLocator)
        val target = targets[if (currentIndex < 0 || currentIndex == targets.lastIndex) 0 else currentIndex + 1]
        focusManager.clearFocus(force = true)
        showQa = false
        showWholeFile = false
        filter = EntryFilter.ALL
        lastSuggestionLocator = target
        jumpLocator = target
        jumpRequestId += 1
    }

    val missingEnglish = current.document.entries.filterNot { it.isSupplied }
    val unusedSuggestionLocators = pendingSuggestionLocators(current)
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
    val hasLocalChanges = current.raw != current.remote.content || current.endnoteProposals?.changed == true

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
                        if (hasLocalChanges) Icons.Default.EditNote else Icons.Default.CloudDone,
                        if (hasLocalChanges) "Unsaved local changes" else "No changes",
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
                        onClick = { showOverflow = true },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Default.MoreVert, "Editor actions")
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (showWholeFile) "Cards mode" else "Whole file mode") },
                            onClick = {
                                showOverflow = false
                                focusManager.clearFocus(force = true)
                                showWholeFile = !showWholeFile
                                showQa = false
                            },
                            enabled = !busy,
                        )
                        imported?.let { overlay ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (fillableImportCount > 0) "Fill blanks ($fillableImportCount)"
                                        else "Remove all English",
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    focusManager.clearFocus(force = true)
                                    if (fillableImportCount > 0) fillBlanksFromImport(overlay)
                                    else showRemoveAllConfirm = true
                                },
                                enabled = !busy && !importBusy && !showWholeFile &&
                                    (fillableImportCount > 0 || suppliedEnglishCount > 0),
                            )
                        }
                    }
                },
            )
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
                                endnotes = current.document.endnotes.filter { it.locator == entry.locator },
                                proposals = current.endnoteProposals?.document
                                    ?.visibleFor(current.file.volume, current.file.chapter, entry.locator)
                                    .orEmpty(),
                                importedTranslation = imported?.translationFor(entry.locator),
                                nextEndnoteId = { EndnoteIntegrity.nextId(current.document, entry.locator) },
                                onUseImport = { text -> useImported(entry.locator, text) },
                                onEnglishChange = { english -> updateEnglish(entry.locator, english) },
                                onSaveEndnote = { english, note -> saveEndnote(entry.locator, english, note) },
                                onRemoveEndnote = { english, noteId -> removeEndnote(entry.locator, english, noteId) },
                                onProposalDecision = ::stageProposalDecision,
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
                BadgedBox(
                    badge = { Badge { Text(missingEnglish.size.toString()) } },
                ) {
                    IconButton(
                        onClick = { jumpToFirstMissing() },
                        enabled = !busy && missingEnglish.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.NavigateNext, "Go to first unsupplied English")
                    }
                }
                BadgedBox(
                    badge = { Badge { Text(unusedSuggestionLocators.size.toString()) } },
                ) {
                    IconButton(
                        onClick = { cycleUnusedEndnoteSuggestions() },
                        enabled = !busy && unusedSuggestionLocators.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PriorityHigh, "Cycle unused endnote suggestions")
                    }
                }
                if (imported == null) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            importLauncher.launch(arrayOf("application/xml", "text/xml", "application/octet-stream", "*/*"))
                        },
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
            text = {
                Text(
                    buildString {
                        append("Mark reviewed only after checking the full chapter. Every English field must be supplied first. Imported translations stay local until you use them; Fill blanks and Use Import copy them into authoritative English fields.")
                        if (current.endnoteProposals?.changed == true) {
                            append(" Staged endnote suggestion decisions will be committed atomically with this chapter.")
                        }
                    },
                )
            },
            confirmButton = { Button(onClick = { showCommit = false; onCommit(current, true) }, enabled = !busy && !importBusy) { Text("Mark reviewed & commit") } },
            dismissButton = { TextButton(onClick = { showCommit = false; onCommit(current, false) }, enabled = !busy && !importBusy) { Text("Commit without review") } },
        )
    }
}

private fun selectedFileName(context: Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }
}.getOrNull()

private fun filteredEntries(document: EditorDocument, filter: EntryFilter): List<EditorEntry> = when (filter) {
    EntryFilter.NEEDS_ATTENTION -> {
        val missing = document.entries.filterNot { it.isSupplied }
        if (missing.isNotEmpty()) missing else if (!document.editorReviewComplete) document.entries else emptyList()
    }
    EntryFilter.ALL -> document.entries
    EntryFilter.SUPPLIED -> document.entries.filter { it.isSupplied }
    EntryFilter.UNSUPPLIED -> document.entries.filterNot { it.isSupplied }
}

private fun pendingSuggestionLocators(chapter: OpenChapter): List<String> {
    val pending = chapter.endnoteProposals?.document?.proposals.orEmpty()
        .asSequence()
        .filter {
            it.status == "pending" &&
                it.volume == chapter.file.volume &&
                it.chapter == chapter.file.chapter
        }
        .map { it.locator }
        .toSet()
    return chapter.document.entries.map { it.locator }.filter { it in pending }
}

private fun suggestedAnchorSelection(text: String, anchor: String): TextRange? {
    val needle = anchor.trim()
    if (needle.isEmpty()) return null
    val first = text.indexOf(needle)
    if (first < 0) return null
    val next = text.indexOf(needle, first + needle.length)
    if (next >= 0) return null
    return TextRange(first, first + needle.length)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryCard(
    entry: EditorEntry,
    endnotes: List<EndnoteDefinition>,
    proposals: List<EndnoteProposal>,
    importedTranslation: String?,
    nextEndnoteId: () -> String,
    onUseImport: (String) -> Unit,
    onEnglishChange: (String) -> Unit,
    onSaveEndnote: (String, EndnoteDefinition) -> Unit,
    onRemoveEndnote: (String, String) -> Unit,
    onProposalDecision: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val focusManager = LocalFocusManager.current
    val focusRequester = remember(entry.locator) { FocusRequester() }
    var rich by remember(entry.locator) {
        mutableStateOf(
            runCatching { RichInlineState.fromMarkup(entry.english) }
                .getOrElse { RichInlineState.plain(InlineMarkup.visibleText(entry.english)) },
        )
    }
    var showEndnoteDialog by remember(entry.locator) { mutableStateOf(false) }
    var showProposalSheet by remember(entry.locator) { mutableStateOf(false) }
    var editingEndnoteId by remember(entry.locator) { mutableStateOf<String?>(null) }
    var proposalForEndnote by remember(entry.locator) { mutableStateOf<String?>(null) }
    var pendingSelection by remember(entry.locator) { mutableStateOf<TextRange?>(null) }
    var endnoteAnchor by remember(entry.locator) { mutableStateOf("") }
    var endnoteDraft by remember(entry.locator) { mutableStateOf("") }

    fun openExistingEndnote(id: String) {
        val definition = endnotes.firstOrNull { it.id == id } ?: return
        editingEndnoteId = id
        proposalForEndnote = null
        pendingSelection = rich.rangeForEndnote(id)
        endnoteAnchor = rich.anchorForEndnote(id)
        endnoteDraft = definition.content
        showEndnoteDialog = true
    }

    fun openSuggestion(proposal: EndnoteProposal) {
        val selected = when {
            rich.hasSelection() && !rich.selectionOverlapsEndnote() -> rich.selection
            else -> suggestedAnchorSelection(rich.text, proposal.suggestedEnglishAnchor)?.takeIf { candidate ->
                !rich.copy(selection = candidate).selectionOverlapsEndnote()
            }
        }
        if (selected == null) {
            Toast.makeText(
                context,
                "Select the English anchor in this card, then choose Use suggestion again.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        rich = rich.copy(selection = selected)
        editingEndnoteId = null
        proposalForEndnote = proposal.id
        pendingSelection = selected
        endnoteAnchor = rich.selectedText()
        endnoteDraft = proposal.suggestedContent
        showProposalSheet = false
        showEndnoteDialog = true
    }

    LaunchedEffect(entry.english) {
        if (entry.english != rich.toMarkup()) {
            rich = runCatching { RichInlineState.fromMarkup(entry.english) }
                .getOrElse { RichInlineState.plain(InlineMarkup.visibleText(entry.english)) }
        }
    }

    LaunchedEffect(proposals.size) {
        if (proposals.isEmpty()) showProposalSheet = false
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
                if (proposals.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            if (proposals.size > 1) Badge { Text(proposals.size.toString()) }
                        },
                    ) {
                        IconButton(onClick = { showProposalSheet = true }) {
                            Icon(Icons.Default.PriorityHigh, "Endnote suggestions")
                        }
                    }
                }
                IconButton(onClick = {
                    focusManager.clearFocus(force = true)
                    val reference = importedTranslation?.let { "\n\nIMPORTED TRANSLATION:\n$it" }.orEmpty()
                    clipboard.setPrimaryClip(ClipData.newPlainText("raw ${entry.locator}", "RAW:\n${entry.sourceJapanese}$reference"))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, "Copy raw and imported text") }
            }
            DisplayBlock("Raw", entry.sourceJapanese)
            importedTranslation?.let { DisplayBlock("Imported", it) }
            OutlinedTextField(
                value = rich.asTextFieldValue(),
                onValueChange = { next ->
                    val before = rich
                    val beforeMarkup = before.toMarkup()
                    val updated = before.edited(next)
                    rich = updated
                    val afterMarkup = updated.toMarkup()
                    if (afterMarkup != beforeMarkup) onEnglishChange(afterMarkup)
                    if (
                        next.text == before.text &&
                        next.selection.start == next.selection.end &&
                        next.selection != before.selection
                    ) {
                        updated.endnoteIdAtCaret()?.let(::openExistingEndnote)
                    }
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
                TextButton(
                    onClick = {
                        val ids = rich.endnoteIdsInSelection()
                        when {
                            ids.isEmpty() && !rich.selectionOverlapsEndnote() -> {
                                editingEndnoteId = null
                                proposalForEndnote = null
                                pendingSelection = rich.selection
                                endnoteAnchor = rich.selectedText()
                                endnoteDraft = ""
                                showEndnoteDialog = true
                            }
                            ids.size == 1 && rich.selectionIsEntirelyEndnote(ids.single()) -> {
                                openExistingEndnote(ids.single())
                            }
                            else -> Toast.makeText(
                                context,
                                "Selection overlaps an existing endnote. Edit or remove that endnote first.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    enabled = rich.hasSelection(),
                ) { Text("N", textDecoration = TextDecoration.Underline) }
                Spacer(Modifier.weight(1f))
                importedTranslation?.let { importedText ->
                    TextButton(onClick = {
                        focusManager.clearFocus(force = true)
                        rich = RichInlineState.plain(importedText)
                        onUseImport(importedText)
                    }) { Text("Use Import") }
                }
                IconButton(onClick = {
                    focusManager.clearFocus(force = true)
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (GitHubCredentialGuard.containsCredential(text)) {
                        Toast.makeText(
                            context,
                            "Paste blocked: clipboard looks like a GitHub credential.",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else if (text.isNotEmpty()) {
                        rich = RichInlineState.fromClipboard(text)
                        onEnglishChange(rich.toMarkup())
                    }
                }, enabled = clipboard.hasPrimaryClip()) {
                    Icon(Icons.Default.ContentPaste, "Paste English")
                }
            }
        }
    }

    if (showProposalSheet) {
        ModalBottomSheet(onDismissRequest = { showProposalSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Endnote suggestions", style = MaterialTheme.typography.titleMedium)
                proposals.forEach { proposal ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (proposal.status == "accepted") {
                                Text("Used suggestion", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            DisplayBlock("Source anchor", proposal.sourceAnchor)
                            if (proposal.suggestedEnglishAnchor.isNotBlank()) {
                                DisplayBlock("Suggested English anchor", proposal.suggestedEnglishAnchor)
                            }
                            DisplayBlock("Suggested note", proposal.suggestedContent)
                            DisplayBlock("Reason", proposal.reason)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(onClick = { openSuggestion(proposal) }) {
                                    Text(if (proposal.status == "accepted") "Use again" else "Use suggestion")
                                }
                                TextButton(
                                    onClick = {
                                        onProposalDecision(proposal.id, "rejected")
                                        if (proposals.size == 1) showProposalSheet = false
                                    },
                                ) { Text("Dismiss") }
                                IconButton(
                                    onClick = {
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Suggested endnote", proposal.suggestedContent),
                                        )
                                        Toast.makeText(context, "Suggestion note copied.", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = proposal.suggestedContent.isNotBlank(),
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy suggested note")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEndnoteDialog) {
        AlertDialog(
            onDismissRequest = {
                proposalForEndnote = null
                showEndnoteDialog = false
            },
            title = { Text(if (editingEndnoteId == null) "Add endnote" else "Edit endnote") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = endnoteAnchor,
                        onValueChange = { endnoteAnchor = it },
                        label = { Text("Anchor") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = endnoteDraft,
                        onValueChange = { endnoteDraft = it },
                        label = { Text("Endnote") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val content = endnoteDraft.trim()
                        val existingId = editingEndnoteId
                        if (existingId != null) {
                            rich.rangeForEndnote(existingId)?.let { range ->
                                rich = rich.copy(selection = range).replaceSelection(endnoteAnchor)
                            }
                            onSaveEndnote(
                                rich.toMarkup(),
                                EndnoteDefinition(existingId, entry.locator, content),
                            )
                        } else {
                            val selection = pendingSelection
                            if (selection != null) {
                                val id = nextEndnoteId()
                                rich = rich.copy(selection = selection).replaceSelection(endnoteAnchor).applyEndnote(id)
                                onSaveEndnote(
                                    rich.toMarkup(),
                                    EndnoteDefinition(id, entry.locator, content),
                                )
                                proposalForEndnote?.let { proposalId ->
                                    onProposalDecision(proposalId, "accepted")
                                }
                            }
                        }
                        proposalForEndnote = null
                        showEndnoteDialog = false
                    },
                    enabled = endnoteAnchor.isNotBlank() && endnoteDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    editingEndnoteId?.let { id ->
                        TextButton(
                            onClick = {
                                rich = rich.removeEndnote(id)
                                onRemoveEndnote(rich.toMarkup(), id)
                                proposalForEndnote = null
                                showEndnoteDialog = false
                            },
                        ) { Text("Remove") }
                    }
                    TextButton(onClick = {
                        proposalForEndnote = null
                        showEndnoteDialog = false
                    }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun WholeFileEditor(raw: String, onRawChange: (String) -> Unit, onValidate: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Direct schema-v6 YAML editor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onValidate) { Icon(Icons.Default.CheckCircle, "Validate whole file") }
        }
        Text("Exceptional edits only. Normal editing should change English fields in Cards view.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = raw, onValueChange = onRawChange, modifier = Modifier.fillMaxWidth().weight(1f), minLines = 12)
    }
}

@Composable
private fun DisplayBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        Text(if (value.isBlank()) "—" else value)
    }
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
    if (snapshot == null) {
        Column(modifier.padding(8.dp)) { Text("No machine-readable QA findings have been recorded for this chapter yet.") }
        return
    }
    var pending by remember { mutableStateOf<QaFinding?>(null) }
    var reason by remember { mutableStateOf("") }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(snapshot.document.findings, key = { it.id }) { finding ->
            val overridden = finding.override?.editorContentSha256 == editorSha
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Override QA finding?") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Override reason") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(
                    onClick = { pending = null; onOverride(finding.id, reason.trim()); reason = "" },
                    enabled = reason.isNotBlank() && !busy,
                ) { Text("Confirm override") }
            },
            dismissButton = { TextButton(onClick = { pending = null }, enabled = !busy) { Text("Cancel") } },
        )
    }
}
