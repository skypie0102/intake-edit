package cloud.shadowmonarchbooks.intakeedit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import android.os.SystemClock
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Undo
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
    onResolve: (OpenChapter, String) -> Unit,
    onApprove: (OpenChapter) -> Unit,
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
    var showApprove by remember { mutableStateOf(false) }
    var showRemoveImportConfirm by remember { mutableStateOf(false) }
    var showRemoveAllConfirm by remember { mutableStateOf(false) }
    var editorNotice by remember { mutableStateOf<String?>(null) }
    var jumpLocator by remember { mutableStateOf<String?>(null) }
    var jumpRequestId by remember { mutableStateOf(0) }
    var lastAttentionLocator by remember(initial.file.path, initial.remote.sha) { mutableStateOf<String?>(null) }
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
        current = current.copy(raw = raw, document = document, approved = false)
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

    fun restoreEntryEditState(
    locator: String,
    english: String,
    notes: List<EndnoteDefinition>,
    proposalStatuses: Map<String, String>,
) {
    val document = EndnoteIntegrity.restoreEntry(current.document, locator, english, notes)
    var stagedProposals = current.endnoteProposals
    proposalStatuses.forEach { (proposalId, status) ->
        stagedProposals = stagedProposals?.let { EndnoteProposalParser.stage(it, proposalId, status) }
    }
    val raw = IntakeParser.patchDocument(current.raw, document)
    current = current.copy(raw = raw, document = document, endnoteProposals = stagedProposals, approved = false)
    onDraft(current)
    editorNotice = "Edit history restored."
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

    fun cycleAttention() {
        val editorSha = QaFindingsParser.editorContentSha256(current.raw)
        val attention = buildAttentionState(
            document = current.document,
            qa = current.qa?.document,
            proposals = current.endnoteProposals?.document,
            volume = current.file.volume,
            chapter = current.file.chapter,
            editorContentSha256 = editorSha,
        )
        val targets = attention.unresolvedLocators
        if (targets.isEmpty()) {
            if (attention.hasUnlocatedActiveQa) {
                focusManager.clearFocus(force = true)
                showWholeFile = false
                showQa = true
                filter = EntryFilter.ALL
            }
            return
        }
        val currentIndex = targets.indexOf(lastAttentionLocator)
        val target = targets[if (currentIndex < 0 || currentIndex == targets.lastIndex) 0 else currentIndex + 1]
        focusManager.clearFocus(force = true)
        showQa = false
        showWholeFile = false
        filter = EntryFilter.ALL
        lastAttentionLocator = target
        jumpLocator = target
        jumpRequestId += 1
    }

    val missingEnglish = current.document.entries.filterNot { it.isSupplied }
    val editorContentSha = QaFindingsParser.editorContentSha256(current.raw)
    val attentionState = buildAttentionState(
        document = current.document,
        qa = current.qa?.document,
        proposals = current.endnoteProposals?.document,
        volume = current.file.volume,
        chapter = current.file.chapter,
        editorContentSha256 = editorContentSha,
    )
    val allQaByLocator = attentionState.allQaByLocator
    val qaDispositionById = attentionState.qaDispositionById
    val qaCanResolveById = current.qa?.document?.findings.orEmpty().associate { finding ->
        finding.id to (current.qa?.document?.canResolve(finding, current.document) == true)
    }
    val unresolvedAttentionCount = attentionState.unresolvedCount
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
    val qaPassReusable = current.qa?.document?.qaPassReusable(current.document) == true
    val qaPassStale = current.qa?.document?.qaPass != null && !qaPassReusable
    val closedReusableQaPass = qaPassReusable &&
        current.qa?.document?.active(current.document, editorContentSha).orEmpty().isEmpty()
    val readyForApproval = !current.approved &&
        !hasLocalChanges &&
        current.document.editorReviewComplete &&
        current.document.englishSupplied == current.document.englishTotal &&
        closedReusableQaPass
    val primaryCommitLabel = when {
        readyForApproval -> "Approve Chapter"
        closedReusableQaPass -> "Commit for Approval"
        else -> "Tag for QA & Commit"
    }

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
            if (qaPassStale) {
                Text("Previous QA pass is stale because protected/unflagged content changed. Fresh semantic QA is required.", style = MaterialTheme.typography.bodySmall)
            }
            AnimatedVisibility(visible = showWholeFile || showQa || headerExpanded) {
                Text(
                    buildString {
                        append(current.document.displayTitle)
                        append(
                            when {
                                current.approved -> " • Approved"
                                readyForApproval -> " • Ready for Approval"
                                current.document.editorReviewComplete -> " • Pending QA"
                                else -> " • Pending Review"
                            },
                        )
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
                        current = current.copy(raw = raw, document = parsed ?: current.document, approved = false)
                        onDraft(current)
                        editorNotice = null
                    },
                    onValidate = {
                        runCatching {
                            IntakeParser.validate(current.raw).getOrThrow()
                            val parsed = IntakeParser.parse(current.raw)
                            current = current.copy(document = parsed, approved = false)
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
                        editor = current.document,
                        editorSha = QaFindingsParser.editorContentSha256(current.raw),
                        busy = busy,
                        onOverride = { id, reason -> onOverride(current, id, reason) },
                        onResolve = { id -> onResolve(current, id) },
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
                                qaFindings = allQaByLocator[entry.locator].orEmpty(),
                                qaDispositionById = qaDispositionById,
                                qaCanResolveById = qaCanResolveById,
                                importedTranslation = imported?.translationFor(entry.locator),
                                nextEndnoteId = { EndnoteIntegrity.nextId(current.document, entry.locator) },
                                onUseImport = { text -> useImported(entry.locator, text) },
                                onEnglishChange = { english -> updateEnglish(entry.locator, english) },
                                onSaveEndnote = { english, note -> saveEndnote(entry.locator, english, note) },
                                onRemoveEndnote = { english, noteId -> removeEndnote(entry.locator, english, noteId) },
                                onProposalDecision = ::stageProposalDecision,
                                onOverrideFinding = { id, reason -> onOverride(current, id, reason) },
                                onResolveFinding = { id -> onResolve(current, id) },
                        onRestoreEditState = { english, notes, proposalStatuses ->
                            restoreEntryEditState(entry.locator, english, notes, proposalStatuses)
                        },
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
                    badge = { if (missingEnglish.isNotEmpty()) Badge { Text(missingEnglish.size.toString()) } },
                ) {
                    IconButton(
                        onClick = { jumpToFirstMissing() },
                        enabled = !busy && missingEnglish.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.NavigateNext, "Go to first unsupplied English")
                    }
                }
                BadgedBox(
                    badge = { if (unresolvedAttentionCount > 0) Badge { Text(unresolvedAttentionCount.toString()) } },
                ) {
                    IconButton(
                        onClick = { cycleAttention() },
                        enabled = !busy && unresolvedAttentionCount > 0,
                    ) {
                        Icon(Icons.Default.PriorityHigh, "Cycle cards needing attention")
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
                Button(
                    onClick = { if (readyForApproval) showApprove = true else showCommit = true },
                    enabled = !busy && !importBusy && !current.approved,
                ) { Text(primaryCommitLabel) }
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
                Text("This will clear every English entry in this chapter and return it to Pending Review. The cleared chapter will be saved as your local draft.")
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
                        if (closedReusableQaPass) {
                            append("All findings from the recorded semantic QA pass are closed. Commit the corrected/accepted chapter as Ready for Approval without running semantic QA again. Reader XHTML is not created until you explicitly approve the committed chapter.")
                        } else {
                            append("Tag for QA only after checking the full chapter. Tagging moves the chapter to Pending QA for one full semantic QA pass. Every English field must be supplied first. Any QA finding returns it to Pending Review for Resolve or Override.")
                        }
                        if (current.endnoteProposals?.changed == true) {
                            append(" Staged endnote suggestion decisions will be committed atomically with this chapter.")
                        }
                    },
                )
            },
            confirmButton = { Button(onClick = { showCommit = false; onCommit(current, true) }, enabled = !busy && !importBusy && missingEnglish.isEmpty()) { Text(primaryCommitLabel) } },
            dismissButton = { TextButton(onClick = { showCommit = false; onCommit(current, false) }, enabled = !busy && !importBusy) { Text("Commit as Pending Review") } },
        )
    }

    if (showApprove) {
        AlertDialog(
            onDismissRequest = { showApprove = false },
            title = { Text("Approve this chapter?") },
            text = {
                Text(
                    "This authorizes the exact committed chapter you reviewed. GitHub Actions will re-check the editor-content hash and QA pass, then deterministically materialize the reader XHTML/endnotes and create the approval artifact. If the chapter changed, approval will be refused.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showApprove = false; onApprove(current) },
                    enabled = !busy && !importBusy && readyForApproval,
                ) { Text("Approve Chapter") }
            },
            dismissButton = {
                TextButton(onClick = { showApprove = false }, enabled = !busy && !importBusy) { Text("Cancel") }
            },
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
    qaFindings: List<QaFinding>,
    qaDispositionById: Map<String, QaFindingDisposition>,
    qaCanResolveById: Map<String, Boolean>,
    importedTranslation: String?,
    nextEndnoteId: () -> String,
    onUseImport: (String) -> Unit,
    onEnglishChange: (String) -> Unit,
    onSaveEndnote: (String, EndnoteDefinition) -> Unit,
    onRemoveEndnote: (String, String) -> Unit,
    onProposalDecision: (String, String) -> Unit,
    onOverrideFinding: (String, String) -> Unit,
    onResolveFinding: (String) -> Unit,
    onRestoreEditState: (String, List<EndnoteDefinition>, Map<String, String>) -> Unit,
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
    var localEndnotes by remember(entry.locator) { mutableStateOf(endnotes) }
    var localProposalStatuses by remember(entry.locator) {
        mutableStateOf(proposals.associate { it.id to it.status })
    }
    var history by remember(entry.locator) { mutableStateOf(EntryEditHistory()) }
    var showEndnoteDialog by remember(entry.locator) { mutableStateOf(false) }
    var showAttentionSheet by remember(entry.locator) { mutableStateOf(false) }
    var editingEndnoteId by remember(entry.locator) { mutableStateOf<String?>(null) }
    var proposalForEndnote by remember(entry.locator) { mutableStateOf<String?>(null) }
    var pendingSelection by remember(entry.locator) { mutableStateOf<TextRange?>(null) }
    var endnoteAnchor by remember(entry.locator) { mutableStateOf("") }
    var endnoteDraft by remember(entry.locator) { mutableStateOf("") }
    var pendingQaOverride by remember(entry.locator) { mutableStateOf<QaFinding?>(null) }
    var qaOverrideReason by remember(entry.locator) { mutableStateOf("") }

    fun currentSnapshot(): EntryEditSnapshot = EntryEditSnapshot(
    english = rich.toMarkup(),
    selectionStart = rich.selection.start,
    selectionEnd = rich.selection.end,
    endnotes = localEndnotes,
    proposalStatuses = localProposalStatuses,
)

fun recordHistory(kind: EntryEditKind) {
    history = history.recordBefore(currentSnapshot(), kind, SystemClock.uptimeMillis())
}

fun restoreSnapshot(snapshot: EntryEditSnapshot) {
    rich = runCatching {
        RichInlineState.fromMarkup(
            snapshot.english,
            TextRange(snapshot.selectionStart, snapshot.selectionEnd),
        )
    }.getOrElse { RichInlineState.plain(InlineMarkup.visibleText(snapshot.english)) }
    localEndnotes = snapshot.endnotes
    localProposalStatuses = snapshot.proposalStatuses
    onRestoreEditState(snapshot.english, snapshot.endnotes, snapshot.proposalStatuses)
    focusRequester.requestFocus()
}

fun undoEdit() {
    history.undo(currentSnapshot())?.let { step ->
        history = step.history
        restoreSnapshot(step.snapshot)
    }
}

fun syncLocalEndnotesTo(state: RichInlineState) {
    val referenced = InlineMarkup.referencedEndnoteIds(state.toMarkup())
    localEndnotes = localEndnotes.filter { it.id in referenced }
}

fun openExistingEndnote(id: String) {
    val definition = localEndnotes.firstOrNull { it.id == id } ?: return
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
        showAttentionSheet = false
        showEndnoteDialog = true
    }

    LaunchedEffect(entry.english, endnotes) {
    var externalChange = false
    if (entry.english != rich.toMarkup()) {
        rich = runCatching { RichInlineState.fromMarkup(entry.english) }
            .getOrElse { RichInlineState.plain(InlineMarkup.visibleText(entry.english)) }
        externalChange = true
    }
    if (endnotes != localEndnotes) {
        localEndnotes = endnotes
        externalChange = true
    }
    if (externalChange) history = EntryEditHistory()
}

LaunchedEffect(proposals) {
    val incoming = proposals.associate { it.id to it.status }
    if (incoming.any { (id, status) -> localProposalStatuses[id] != status }) {
        localProposalStatuses = localProposalStatuses + incoming
        history = EntryEditHistory()
    }
    if (proposals.isEmpty() && qaFindings.isEmpty()) showAttentionSheet = false
}

LaunchedEffect(qaFindings) {
    if (proposals.isEmpty() && qaFindings.isEmpty()) showAttentionSheet = false
}

    val activeAttentionCount =
        qaFindings.count { qaDispositionById[it.id] == QaFindingDisposition.ACTIVE } +
            proposals.count { (localProposalStatuses[it.id] ?: it.status) == "pending" }

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
                if (proposals.isNotEmpty() || qaFindings.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            if (activeAttentionCount > 0) Badge { Text(activeAttentionCount.toString()) }
                        },
                    ) {
                        IconButton(onClick = { showAttentionSheet = true }) {
                            Icon(Icons.Default.PriorityHigh, "Attention")
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
                    val afterMarkup = updated.toMarkup()
                    if (afterMarkup != beforeMarkup) {
                        recordHistory(EntryEditKind.INPUT)
                        rich = updated
                        syncLocalEndnotesTo(updated)
                        onEnglishChange(afterMarkup)
                    } else {
                        rich = updated
                    }
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
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        recordHistory(EntryEditKind.COMMAND)
                        rich = rich.toggle(InlineStyle.BOLD)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    },
                    modifier = Modifier.size(40.dp),
                    enabled = rich.hasSelection(),
                ) { Text("B", fontWeight = FontWeight.Bold) }
                IconButton(
                    onClick = {
                        recordHistory(EntryEditKind.COMMAND)
                        rich = rich.toggle(InlineStyle.ITALIC)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    },
                    modifier = Modifier.size(40.dp),
                    enabled = rich.hasSelection(),
                ) { Text("I", fontStyle = FontStyle.Italic) }
                IconButton(
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
                    modifier = Modifier.size(40.dp),
                    enabled = rich.hasSelection() && entry.locator.startsWith("P"),
                ) { Text("N", textDecoration = TextDecoration.Underline) }
                IconButton(onClick = ::undoEdit, enabled = history.canUndo) {
                    Icon(Icons.Default.Undo, "Undo English edit")
                }
                Spacer(Modifier.weight(1f))
                importedTranslation?.let { importedText ->
                    TextButton(onClick = {
                        focusManager.clearFocus(force = true)
                        recordHistory(EntryEditKind.COMMAND)
                        rich = RichInlineState.plain(importedText)
                        localEndnotes = emptyList()
                        onUseImport(importedText)
                    }) { Text("Use Import") }
                }
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (GitHubCredentialGuard.containsCredential(text)) {
                            Toast.makeText(
                                context,
                                "Paste blocked: clipboard looks like a GitHub credential.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else if (text.isNotEmpty()) {
                            recordHistory(EntryEditKind.COMMAND)
                            rich = RichInlineState.fromClipboard(text)
                            syncLocalEndnotesTo(rich)
                            onEnglishChange(rich.toMarkup())
                        }
                    },
                    enabled = clipboard.hasPrimaryClip(),
                ) {
                    Icon(Icons.Default.ContentPaste, "Paste English")
                }
            }
        }
    }

    if (showAttentionSheet) {
        ModalBottomSheet(onDismissRequest = { showAttentionSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Attention", style = MaterialTheme.typography.titleMedium)
                if (qaFindings.isNotEmpty()) {
                    Text("QA findings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    qaFindings.forEach { finding ->
                        val disposition = qaDispositionById[finding.id] ?: QaFindingDisposition.ACTIVE
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${finding.severity.uppercase()} • ${finding.category}", fontWeight = FontWeight.Bold)
                                Text(finding.message)
                                Text(
                                    when (disposition) {
                                        QaFindingDisposition.RESOLVED -> "RESOLVED"
                                        QaFindingDisposition.OVERRIDDEN -> "OVERRIDDEN"
                                        QaFindingDisposition.ACTIVE -> if (finding.resolution != null || finding.override != null) "ACTIVE • previous closure stale" else "ACTIVE"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                if (disposition == QaFindingDisposition.ACTIVE) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { onResolveFinding(finding.id) },
                                            enabled = qaCanResolveById[finding.id] == true,
                                        ) { Text("Resolve") }
                                        if (finding.overridable) {
                                            TextButton(onClick = {
                                                pendingQaOverride = finding
                                                qaOverrideReason = ""
                                            }) { Text("Override") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (proposals.isNotEmpty()) {
                    Text("Endnote suggestions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    proposals.forEach { proposal ->
                        val proposalStatus = localProposalStatuses[proposal.id] ?: proposal.status
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (proposalStatus) {
                                    "accepted" -> Text("Used suggestion", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    "rejected" -> Text("Dismissed suggestion", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                                        Text(if (proposalStatus == "accepted") "Use again" else "Use suggestion")
                                    }
                                    TextButton(
                                        onClick = {
                                            recordHistory(EntryEditKind.COMMAND)
                                            localProposalStatuses = localProposalStatuses + (proposal.id to "rejected")
                                            onProposalDecision(proposal.id, "rejected")
                                            if (proposals.size == 1 && qaFindings.isEmpty()) showAttentionSheet = false
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
    }

    pendingQaOverride?.let { finding ->
        AlertDialog(
            onDismissRequest = { pendingQaOverride = null },
            title = { Text("Override QA finding?") },
            text = {
                OutlinedTextField(
                    qaOverrideReason,
                    { qaOverrideReason = it },
                    label = { Text("Override reason") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingQaOverride = null
                        onOverrideFinding(finding.id, qaOverrideReason.trim())
                        qaOverrideReason = ""
                    },
                    enabled = qaOverrideReason.isNotBlank(),
                ) { Text("Override") }
            },
            dismissButton = {
                TextButton(onClick = { pendingQaOverride = null }) { Text("Cancel") }
            },
        )
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
                        recordHistory(EntryEditKind.COMMAND)
                        if (existingId != null) {
                            rich.rangeForEndnote(existingId)?.let { range ->
                                rich = rich.copy(selection = range).replaceSelection(endnoteAnchor)
                            }
                            val definition = EndnoteDefinition(existingId, entry.locator, content)

                            localEndnotes = localEndnotes.filterNot { it.id == existingId } + definition

                            onSaveEndnote(rich.toMarkup(), definition)
                        } else {
                            val selection = pendingSelection
                            if (selection != null) {
                                val id = nextEndnoteId()
                                rich = rich.copy(selection = selection).replaceSelection(endnoteAnchor).applyEndnote(id)
                                val definition = EndnoteDefinition(id, entry.locator, content)

                                localEndnotes = localEndnotes.filterNot { it.id == id } + definition

                                onSaveEndnote(rich.toMarkup(), definition)

                                proposalForEndnote?.let { proposalId ->

                                    localProposalStatuses = localProposalStatuses + (proposalId to "accepted")

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
                                recordHistory(EntryEditKind.COMMAND)
                                rich = rich.removeEndnote(id)
                                localEndnotes = localEndnotes.filterNot { it.id == id }
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
    editor: EditorDocument,
    editorSha: String,
    busy: Boolean,
    onOverride: (String, String) -> Unit,
    onResolve: (String) -> Unit,
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
            val disposition = snapshot.document.disposition(finding, editor, editorSha)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${finding.severity.uppercase()} • ${finding.category}", fontWeight = FontWeight.Bold)
                    if (finding.locator.isNotBlank()) Text(finding.locator)
                    Text(finding.message)
                    Text(
                        when (disposition) {
                            QaFindingDisposition.RESOLVED -> "RESOLVED"
                            QaFindingDisposition.OVERRIDDEN -> "OVERRIDDEN"
                            QaFindingDisposition.ACTIVE -> if (finding.resolution != null || finding.override != null) "ACTIVE • previous closure stale" else "ACTIVE"
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (finding.locator.isNotBlank()) TextButton(onClick = { onShowParagraph(finding.locator) }, enabled = !busy) { Text("Show paragraph") }
                        if (disposition == QaFindingDisposition.ACTIVE) {
                            TextButton(
                                onClick = { onResolve(finding.id) },
                                enabled = !busy && snapshot.document.canResolve(finding, editor),
                            ) { Text("Resolve") }
                            if (finding.overridable) TextButton(onClick = { pending = finding }, enabled = !busy) { Text("Override") }
                        }
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
                ) { Text("Override") }
            },
            dismissButton = { TextButton(onClick = { pending = null }, enabled = !busy) { Text("Cancel") } },
        )
    }
}
