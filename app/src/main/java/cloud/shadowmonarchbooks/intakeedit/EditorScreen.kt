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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    recoveryDrafts: List<LocalDraft>,
    onDismissSavedDraft: (LocalDraft) -> Unit,
    onBack: () -> Unit,
    onDraft: (OpenChapter) -> Unit,
    onCommit: (OpenChapter, Boolean) -> Unit,
    onOverride: (OpenChapter, String, String) -> Unit,
    onResolve: (OpenChapter, String) -> Unit,
    onApprove: (OpenChapter) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val importStore = remember { TranslationImportStore(context) }
    val onlineSettings = remember { OnlineTranslationSettings(context) }
    val project = remember { SettingsStore(context).load() }
    val languages = remember { TranslationLanguages(source = "auto", target = "en") }
    val machineKey = remember(project) { machineCacheKey(project, languages) }
    val importProjectKey = remember(project) { draftScopeKey(project, "") }
    var current by remember(initial.file.path, initial.remote.sha) { mutableStateOf(initial) }
    var importedFile by remember(initial.file.path, initial.document.sourceSha256) {
        mutableStateOf<ImportedTranslationOverlay?>(null)
    }
    var legacyImportedFile by remember(initial.file.path, initial.document.sourceSha256) { mutableStateOf<ImportedTranslationOverlay?>(null) }
    var machineTranslation by remember(initial.file.path, initial.document.sourceSha256) {
        mutableStateOf<ImportedTranslationOverlay?>(null)
    }
    var useMachine by rememberSaveable(initial.file.path, initial.document.sourceSha256) { mutableStateOf(false) }
    val copyPreferences = remember(context) { context.getSharedPreferences("editor_copy", Context.MODE_PRIVATE) }
    var includeReferenceInCopy by rememberSaveable {
        mutableStateOf(copyPreferences.getBoolean("include_reference", false))
    }
    val imported = if (useMachine) machineTranslation else importedFile
    var importBusy by remember(initial.file.path, initial.document.sourceSha256) { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(EntryFilter.ALL) }
    var showQa by rememberSaveable { mutableStateOf(false) }
    var showWholeFile by rememberSaveable(initial.file.path, initial.remote.sha) { mutableStateOf(initial.rawNeedsRepair) }
    var wholeFilePending by remember(initial.file.path, initial.remote.sha) { mutableStateOf(false) }
    var wholeFileValidating by remember(initial.file.path, initial.remote.sha) { mutableStateOf(false) }
    var wholeFileInvalid by remember(initial.file.path, initial.remote.sha) { mutableStateOf(initial.rawNeedsRepair) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showPrimaryConfirmation by remember { mutableStateOf(false) }
    var showPendingReviewConfirmation by remember { mutableStateOf(false) }
    var showSavedDrafts by remember { mutableStateOf(false) }
    var pendingSavedDraftDismissal by remember { mutableStateOf<LocalDraft?>(null) }

    LaunchedEffect(recoveryDrafts.isEmpty()) {
        if (recoveryDrafts.isEmpty()) {
            showSavedDrafts = false
            pendingSavedDraftDismissal = null
        }
    }
    var selectedSavedDraft by remember { mutableStateOf<LocalDraft?>(null) }
    var draftForExport by remember { mutableStateOf<LocalDraft?>(null) }
    var showApprove by remember { mutableStateOf(false) }
    var showRemoveImportConfirm by remember { mutableStateOf(false) }
    var showTranslationChoices by remember { mutableStateOf(false) }
    var showRemoveAllConfirm by remember { mutableStateOf(false) }
    var editorNotice by remember { mutableStateOf<String?>(null) }
    var jumpLocator by remember { mutableStateOf<String?>(null) }
    var jumpRequestId by remember { mutableStateOf(0) }
    var lastAttentionLocator by remember(initial.file.path, initial.remote.sha) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(showWholeFile, current.raw, wholeFilePending) {
        if (showWholeFile && wholeFilePending) {
            val raw = current.raw
            val parsed = parseWholeFileDraft(raw)
            if (current.raw == raw) {
                if (parsed != null) {
                    current = current.copy(document = parsed, rawNeedsRepair = false)
                    onDraft(current)
                }
                wholeFileInvalid = parsed == null
                wholeFilePending = false
            }
        }
    }

    LaunchedEffect(initial.file.path, initial.document.sourceSha256) {
        importBusy = true
        val saved = withContext(Dispatchers.IO) {
            val machine = importStore.load(initial.file.path, initial.document.sourceSha256, machine = true, machineKey = machineKey)
                ?: if (project.owner.equals("skypie0102", true) && project.repo.equals("purelove", true) && project.branch == "main")
                    importStore.load(initial.file.path, initial.document.sourceSha256, machine = true) else null
            Triple(importStore.load(initial.file.path, initial.document.sourceSha256, projectKey = importProjectKey), machine,
                importStore.load(initial.file.path, initial.document.sourceSha256))
        }
        importedFile = saved.first
        machineTranslation = saved.second
        legacyImportedFile = saved.third
        useMachine = machineTranslation != null && onlineSettings.visible(machineKey, initial.file.path, initial.document.sourceSha256)
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

    val draftExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val draft = draftForExport
        draftForExport = null
        if (uri != null && draft != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(draft.raw.toByteArray(Charsets.UTF_8)) }
                            ?: error("Could not open the selected destination.")
                    }
                    editorNotice = "Saved draft exported."
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    editorNotice = "Draft export failed: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val chapter = current
            scope.launch {
                importBusy = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { readTranslationFile(it) }
                            ?: error("Could not read the selected file.")
                        val fileName = selectedFileName(context, uri) ?: "Imported translation"
                        val overlay = TranslationReferenceImporter.parse(
                            raw = String(bytes, Charsets.UTF_8),
                            fileName = fileName,
                            chapterPath = chapter.file.path,
                            document = chapter.document,
                        )
                        require(importStore.save(overlay, projectKey = importProjectKey)) { "Could not persist the imported translation locally." }
                        overlay
                    }
                }.onSuccess { overlay ->
                    importedFile = overlay
                    useMachine = false
                    onlineSettings.setVisible(machineKey, chapter.file.path, chapter.document.sourceSha256, false)
                    editorNotice = "Imported: ${overlay.matchedCount}/${overlay.totalEntries} • ${overlay.alignment} • ${overlay.fileName}"
                }.onFailure {
                    editorNotice = it.message ?: "Could not import this translation file."
                }
                importBusy = false
            }
        }
    }

    fun beginMachineTranslation() {
        val chapter = current
        val partialKey = "$machineKey|incomplete"
        scope.launch {
            importBusy = true
            editorNotice = "Translating with Google…"
            try {
                val partial = withContext(Dispatchers.IO) {
                    importStore.load(chapter.file.path, chapter.document.sourceSha256, machine = true, machineKey = partialKey)
                }
                val overlay = translateWithGoogle(chapter.file.path, chapter.document, languages,
                    alreadyTranslated = partial?.translations.orEmpty(),
                    onCheckpoint = { entries ->
                        val saved = importStore.save(translationOverlay(chapter.file.path, chapter.document, entries), machine = true, machineKey = partialKey)
                        check(saved) { "Could not save translation progress locally." }
                    },
                    onRetry = { wait -> withContext(Dispatchers.Main) {
                        editorNotice = "Google is limiting requests; retrying in ${wait / 1000} seconds…"
                    } },
                    onProgress = { done, total -> withContext(Dispatchers.Main) {
                        editorNotice = "Machine translation: $done/$total entries"
                    } },
                )
                val saved = withContext(Dispatchers.IO) {
                    val success = importStore.save(overlay, machine = true, machineKey = machineKey)
                    if (success) importStore.delete(chapter.file.path, machine = true, machineKey = partialKey)
                    success
                }
                check(saved) { "Could not save the machine translation locally." }
                machineTranslation = overlay
                useMachine = true
                onlineSettings.setVisible(machineKey, chapter.file.path, chapter.document.sourceSha256, true)
                editorNotice = "Machine Translation ready: ${overlay.matchedCount}/${overlay.totalEntries} entries."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                editorNotice = error.message ?: "Machine translation failed. Progress is saved; try again later."
            } finally {
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
        val changed = current.document.copy(entries = entries, editorReviewComplete = false)
        val document = if (changed.endnotes.isEmpty()) changed else EndnoteIntegrity.prune(changed)
        val raw = IntakeParser.patchDocumentEntry(
            current.raw, document, index, endnotesChanged = document.endnotes != current.document.endnotes,
        )
        current = current.copy(raw = raw, document = document, approved = false)
        onDraft(current)
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

    val (suppliedEntries, missingEnglish) = remember(current.document.entries) {
        current.document.entries.partition { it.isSupplied }
    }
    val editorContentSha = remember(current.raw) {
        runCatching { QaFindingsParser.editorContentSha256(current.raw) }.getOrDefault("")
    }
    val qaScopeHashes = remember(current.document) { QaScopeHashes(current.document) }
    val attentionState = remember(current.document, current.qa?.document, current.endnoteProposals?.document, current.file, editorContentSha) { buildAttentionState(
        document = current.document,
        qa = current.qa?.document,
        proposals = current.endnoteProposals?.document,
        volume = current.file.volume,
        chapter = current.file.chapter,
        editorContentSha256 = editorContentSha,
        scopeHashes = qaScopeHashes,
    ) }
    val allQaByLocator = attentionState.allQaByLocator
    val qaDispositionById = attentionState.qaDispositionById
    val qaPassReusable = remember(current.qa?.document, current.document) {
        current.qa?.document?.qaPassReusable(current.document) == true
    }
    val qaCanResolveById = remember(current.qa?.document, current.document, qaPassReusable) {
        current.qa?.document?.findings.orEmpty().associate { finding ->
            finding.id to (current.qa?.document?.canResolve(finding, qaPassReusable) { qaScopeHashes.get(finding.locator) } == true)
        }
    }
    val unresolvedAttentionCount = attentionState.unresolvedCount
    val suppliedEnglishCount = suppliedEntries.size
    val fillableImportCount = imported?.let { overlay ->
        missingEnglish.count { overlay.translationFor(it.locator) != null }
    } ?: 0
    val attentionEntries = if (missingEnglish.isNotEmpty()) missingEnglish
        else if (!current.document.editorReviewComplete) current.document.entries else emptyList()
    val filterEntries = mapOf(
        EntryFilter.ALL to current.document.entries,
        EntryFilter.NEEDS_ATTENTION to attentionEntries,
        EntryFilter.SUPPLIED to suppliedEntries,
        EntryFilter.UNSUPPLIED to missingEnglish,
    )
    val filterCounts = filterEntries.mapValues { it.value.size }
    val entries = filterEntries.getValue(filter)
    val headerExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48 }
    }
    val activeFilterCount = (if (showQa) 1 else 0) + (if (!showQa && filter != EntryFilter.ALL) 1 else 0)
    val hasLocalChanges = current.raw != current.remote.content || current.endnoteProposals?.changed == true
    val qaPassStale = current.qa?.document?.qaPass != null && !qaPassReusable
    val activeQaFindings = if (qaPassReusable) {
        current.qa?.document?.active(current.document, editorContentSha).orEmpty()
    } else emptyList()
    val primaryAction = resolveChapterPrimaryAction(
        document = current.document,
        approved = current.approved,
        hasLocalChanges = hasLocalChanges,
        qaPassReusable = qaPassReusable,
        activeQaFindings = activeQaFindings.size,
        hasRecordedQaPass = current.qa?.document?.qaPass != null,
        hasStagedEndnoteDecisions = current.endnoteProposals?.changed == true,
    )
    val readyForApproval = primaryAction.action == ChapterPrimaryAction.APPROVE_CHAPTER

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
                        "V${current.file.volume} Ch ${current.file.chapter} • ${suppliedEnglishCount}/${current.document.englishTotal}",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                            enabled = !busy && !wholeFilePending && !wholeFileValidating && !wholeFileInvalid,
                        )
                        if (primaryAction.canCommitAsPendingReview(hasLocalChanges)) {
                            DropdownMenuItem(
                                text = { Text("Commit as Pending Review") },
                                onClick = {
                                    showOverflow = false
                                    focusManager.clearFocus(force = true)
                                    showPendingReviewConfirmation = true
                                },
                                enabled = !busy && !importBusy && !wholeFilePending &&
                                    !wholeFileValidating && !wholeFileInvalid,
                            )
                        }
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
            TransientNotice(notice)
            if (recoveryDrafts.isNotEmpty()) {
                TextButton(onClick = { showSavedDrafts = true }) { Text("Saved drafts (${recoveryDrafts.size})") }
            }
            TransientNotice(editorNotice, onTimeout = { editorNotice = null })
            if (qaPassStale) {
                Text(
                    "Previous QA pass is stale because protected/unflagged content changed. This chapter cannot be committed for approval; commit it for Fresh QA instead.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
                        current = current.copy(raw = raw, approved = false)
                        wholeFilePending = true
                        wholeFileInvalid = false
                        onDraft(current)
                        editorNotice = null
                    },
                    onValidate = {
                        val raw = current.raw
                        wholeFileValidating = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    IntakeParser.validate(raw).getOrThrow()
                                    IntakeParser.parse(raw)
                                }
                            }
                            if (current.raw == raw) {
                                result.onSuccess { parsed ->
                                    current = current.copy(document = parsed, approved = false, rawNeedsRepair = false)
                                    wholeFilePending = false
                                    wholeFileInvalid = false
                                    onDraft(current)
                                    editorNotice = "Whole file is valid and synced."
                                }.onFailure {
                                    editorNotice = it.message ?: "Whole file is invalid."
                                }
                            }
                            wholeFileValidating = false
                        }
                    },
                    validating = wholeFileValidating,
                    pending = wholeFilePending,
                    invalid = wholeFileInvalid,
                    modifier = Modifier.weight(1f),
                )
            } else {
                if (showQa) {
                    QaFindingsView(
                        snapshot = current.qa,
                        editor = current.document,
                        editorSha = editorContentSha,
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
                                machineReference = useMachine,
                                includeReferenceInCopy = includeReferenceInCopy,
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Allow the secondary actions to scroll without moving the primary action.
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgedBox(
                        badge = { if (missingEnglish.isNotEmpty()) Badge { Text(missingEnglish.size.toString()) } },
                    ) {
                        IconButton(
                            onClick = { jumpToFirstMissing() },
                            enabled = !busy && !wholeFilePending && !wholeFileValidating && !wholeFileInvalid && missingEnglish.isNotEmpty(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, "Go to first unsupplied English")
                        }
                    }
                    BadgedBox(
                        badge = { if (unresolvedAttentionCount > 0) Badge { Text(unresolvedAttentionCount.toString()) } },
                    ) {
                        IconButton(
                            onClick = { cycleAttention() },
                            enabled = !busy && !wholeFilePending && !wholeFileValidating && !wholeFileInvalid && unresolvedAttentionCount > 0,
                        ) {
                            Icon(Icons.Default.PriorityHigh, "Cycle cards needing attention")
                        }
                    }
                    IconButton(onClick = { showTranslationChoices = true }, enabled = !busy && !importBusy) {
                        Icon(Icons.Default.Translate, "Translation reference")
                    }
                    Switch(
                        checked = includeReferenceInCopy,
                        onCheckedChange = { enabled ->
                            includeReferenceInCopy = enabled
                            copyPreferences.edit().putBoolean("include_reference", enabled).apply()
                            Toast.makeText(
                                context,
                                if (enabled) "Copy: RAW + translation" else "Copy: RAW only",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.semantics { contentDescription = "Include translation in copied text" },
                    )
                }
                Button(
                    onClick = {
                        when (primaryAction.action) {
                            ChapterPrimaryAction.APPROVE_CHAPTER -> showApprove = true
                            ChapterPrimaryAction.REVIEW_QA_FINDINGS -> {
                                showWholeFile = false
                                showQa = true
                            }
                            else -> if (primaryAction.action.tagForQa != null) showPrimaryConfirmation = true
                        }
                    },
                    enabled = primaryAction.enabled && !busy && !importBusy &&
                        !wholeFilePending && !wholeFileValidating && !wholeFileInvalid,
                ) { Text(primaryAction.label, maxLines = 1) }
            }
        }
    }

    if (showSavedDrafts && pendingSavedDraftDismissal == null) {
        AlertDialog(
            onDismissRequest = { showSavedDrafts = false },
            title = { Text("Saved older drafts") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Open a draft to inspect or copy it. Older-format drafts have no recorded project, so check them before restoring.")
                    recoveryDrafts.forEach { draft ->
                        val saved = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(draft.savedAtMillis))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(
                                onClick = { selectedSavedDraft = draft; showSavedDrafts = false },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("$saved • ${draft.baseSha.take(8)}${if (draft.legacyUnassigned) " • project unknown" else ""}")
                            }
                            IconButton(
                                onClick = { pendingSavedDraftDismissal = draft },
                                enabled = !busy,
                            ) {
                                Icon(Icons.Default.Close, "Dismiss saved draft from $saved")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSavedDrafts = false }) { Text("Close") } },
        )
    }
    pendingSavedDraftDismissal?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingSavedDraftDismissal = null },
            title = { Text("Dismiss saved draft?") },
            text = { Text("This permanently removes the selected older draft from local recovery. The current chapter, other saved drafts, and GitHub files will not be changed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSavedDraftDismissal = null
                    onDismissSavedDraft(draft)
                }, enabled = !busy) { Text("Dismiss draft") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSavedDraftDismissal = null }) { Text("Cancel") }
            },
        )
    }
    selectedSavedDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { selectedSavedDraft = null },
            title = { Text("Older draft") },
            text = { OutlinedTextField(value = draft.raw, onValueChange = {}, label = { Text("Saved draft contents") }, readOnly = true, minLines = 8, maxLines = 16, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(draft.raw))
                    selectedSavedDraft = null
                    editorNotice = "Older draft copied."
                }) { Text("Copy draft") }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        draftForExport = draft
                        draftExportLauncher.launch("chapter-${current.file.chapter}-draft.yml")
                    }) { Text("Export") }
                    if (draft.baseSha == current.remote.sha && current.raw == current.remote.content && current.endnoteProposals?.changed != true) {
                        TextButton(onClick = {
                            current = current.copy(raw = draft.raw, approved = false, rawNeedsRepair = true)
                            showWholeFile = true
                            showQa = false
                            wholeFileInvalid = false
                            wholeFilePending = true
                            onDraft(current)
                            selectedSavedDraft = null
                            editorNotice = "Draft restored for review. Its original copy remains saved."
                        }, enabled = !busy) { Text("Restore matching draft") }
                    }
                    TextButton(onClick = { selectedSavedDraft = null }) { Text("Close") }
                }
            },
        )
    }

    if (showTranslationChoices) {
        AlertDialog(
            onDismissRequest = { showTranslationChoices = false },
            title = { Text("Translation reference") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import Translation", fontWeight = FontWeight.Bold)
                    Text(if (importedFile == null) "Select a translated XHTML or SDLXLIFF/XLIFF file." else importedFile!!.fileName, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showTranslationChoices = false
                            if (importedFile != null) {
                                useMachine = false
                                onlineSettings.setVisible(machineKey, current.file.path, current.document.sourceSha256, false)
                            } else {
                                focusManager.clearFocus(force = true)
                                importLauncher.launch(arrayOf("application/xhtml+xml", "application/xml", "text/xml", "application/octet-stream", "*/*"))
                            }
                        }) { Text(if (importedFile == null) "Choose file" else if (!useMachine) "Showing" else "Show import") }
                        if (importedFile != null) TextButton(onClick = {
                            showTranslationChoices = false
                            focusManager.clearFocus(force = true)
                            importLauncher.launch(arrayOf("application/xhtml+xml", "application/xml", "text/xml", "application/octet-stream", "*/*"))
                        }) { Text("Replace file") }
                        if (importedFile != null) TextButton(onClick = {
                            showTranslationChoices = false; showRemoveImportConfirm = true
                        }) { Text("Remove") }
                        if (importedFile == null && legacyImportedFile != null) TextButton(onClick = {
                            val legacy = legacyImportedFile ?: return@TextButton
                            scope.launch {
                                importBusy = true
                                val saved = withContext(Dispatchers.IO) { importStore.save(legacy, projectKey = importProjectKey) }
                                if (saved) {
                                    importedFile = legacy
                                    useMachine = false
                                    editorNotice = "Older reference copied to this project. Check its contents before using it."
                                } else editorNotice = "Could not save the older reference for this project."
                                importBusy = false
                            }
                            showTranslationChoices = false
                        }) { Text("Use older reference (project unknown)") }
                    }
                    Text("Machine Translation", fontWeight = FontWeight.Bold)
                    Text(if (machineTranslation == null) "Translate online with Google." else "A saved translation is available.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        showTranslationChoices = false
                        when (nextMachineAction(useMachine, machineTranslation != null)) {
                            MachineToggle.HIDE -> {
                                useMachine = false
                                onlineSettings.setVisible(machineKey, current.file.path, current.document.sourceSha256, false)
                                editorNotice = "Machine translation hidden. Show it again without another request."
                            }
                            MachineToggle.RESTORE -> {
                                useMachine = true
                                onlineSettings.setVisible(machineKey, current.file.path, current.document.sourceSha256, true)
                                editorNotice = "Restored the saved machine translation."
                            }
                            MachineToggle.TRANSLATE -> beginMachineTranslation()
                        }
                    }) { Text(if (useMachine) "Hide" else if (machineTranslation != null) "Show saved translation" else "Translate with Google") }
                }
            },
            confirmButton = { TextButton(onClick = { showTranslationChoices = false }) { Text("Close") } },
        )
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
            text = { Text("Remove this local reference? English already copied into fields will not be changed.") },
            confirmButton = {
                Button(
                    onClick = {
                        val path = current.file.path
                        showRemoveImportConfirm = false
                        scope.launch {
                            importBusy = true
                            val removed = withContext(Dispatchers.IO) { importStore.delete(path, projectKey = importProjectKey) }
                            if (removed) {
                                importedFile = null
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

    if (showPrimaryConfirmation && primaryAction.action.tagForQa != null) {
        AlertDialog(
            onDismissRequest = { showPrimaryConfirmation = false },
            title = { Text(primaryAction.confirmationTitle ?: primaryAction.label) },
            text = { Text(primaryAction.confirmationMessage.orEmpty()) },
            confirmButton = {
                Button(
                    onClick = {
                        val tagForQa = primaryAction.action.tagForQa ?: return@Button
                        showPrimaryConfirmation = false
                        onCommit(current, tagForQa)
                    },
                    enabled = primaryAction.enabled && !busy && !importBusy &&
                        !wholeFilePending && !wholeFileValidating && !wholeFileInvalid,
                ) { Text(primaryAction.label) }
            },
            dismissButton = {
                TextButton(onClick = { showPrimaryConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    if (showPendingReviewConfirmation) {
        AlertDialog(
            onDismissRequest = { showPendingReviewConfirmation = false },
            title = { Text("Commit as Pending Review?") },
            text = {
                Text(
                    "All English entries are supplied, but this commits only your current progress. " +
                        "The chapter will remain in Pending Review rather than proceeding to QA. " +
                        "A previous QA-ready status may be reset; you can tag the chapter for QA later." +
                        if (current.endnoteProposals?.changed == true) {
                            " Staged endnote suggestion decisions will be committed with the chapter."
                        } else "",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPendingReviewConfirmation = false
                        onCommit(current, false)
                    },
                    enabled = primaryAction.canCommitAsPendingReview(hasLocalChanges) && !busy && !importBusy &&
                        !wholeFilePending && !wholeFileValidating && !wholeFileInvalid,
                ) { Text("Commit as Pending Review") }
            },
            dismissButton = {
                TextButton(onClick = { showPendingReviewConfirmation = false }) { Text("Cancel") }
            },
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
    machineReference: Boolean,
    includeReferenceInCopy: Boolean,
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
                            Icon(Icons.Default.PriorityHigh, "Attention for ${entry.locator}")
                        }
                    }
                }
                IconButton(onClick = {
                    focusManager.clearFocus(force = true)
                    val copiedText = rawCopyText(entry.sourceJapanese, importedTranslation, includeReferenceInCopy, machineReference)
                    clipboard.setPrimaryClip(ClipData.newPlainText("raw ${entry.locator}", copiedText))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, if (includeReferenceInCopy && importedTranslation != null) "Copy raw and translation for ${entry.locator}" else "Copy raw text for ${entry.locator}") }
            }
            DisplayBlock("Raw", entry.sourceJapanese)
            importedTranslation?.let { DisplayBlock(if (machineReference) "Machine Translation · Google Translate" else "Imported", it) }
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
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .semantics { contentDescription = "English for ${entry.locator}" },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Bold selection" },
                    enabled = rich.hasSelection(),
                ) { Text("B", fontWeight = FontWeight.Bold) }
                IconButton(
                    onClick = {
                        recordHistory(EntryEditKind.COMMAND)
                        rich = rich.toggle(InlineStyle.ITALIC)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Italicize selection" },
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
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Edit endnote for selection" },
                    enabled = rich.hasSelection() && entry.locator.startsWith("P"),
                ) { Text("N", textDecoration = TextDecoration.Underline) }
                IconButton(onClick = ::undoEdit, enabled = history.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Undo English edit")
                }
                importedTranslation?.let { importedText ->
                    TextButton(onClick = {
                        focusManager.clearFocus(force = true)
                        recordHistory(EntryEditKind.COMMAND)
                        rich = RichInlineState.plain(importedText)
                        localEndnotes = emptyList()
                        onUseImport(importedText)
                    }) { Text(if (machineReference) "Use Translation" else "Use Import") }
                }
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
                                        if (finding.canOverride) {
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
            title = {
                Text(
                    if (finding.requiresOverrideWarning) {
                        "Override BLOCKING QA finding?"
                    } else {
                        "Override QA finding?"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (finding.requiresOverrideWarning) {
                        Text(
                            "WARNING: This finding is marked BLOCKING because QA considers the issue severe or systemic. Overriding it will allow this exact flagged content to proceed toward approval without correcting the finding.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Confirm only when you intentionally accept this blocking issue. The reason is retained in the QA audit record.")
                    }
                    OutlinedTextField(
                        qaOverrideReason,
                        { qaOverrideReason = it },
                        label = { Text("Override reason") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingQaOverride = null
                        onOverrideFinding(finding.id, qaOverrideReason.trim())
                        qaOverrideReason = ""
                    },
                    enabled = qaOverrideReason.isNotBlank(),
                ) { Text(if (finding.requiresOverrideWarning) "Override blocking finding" else "Override") }
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
private fun WholeFileEditor(raw: String, onRawChange: (String) -> Unit, onValidate: () -> Unit, validating: Boolean, pending: Boolean, invalid: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Direct schema-v6 YAML editor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onValidate, enabled = !validating) { Icon(Icons.Default.CheckCircle, "Validate whole file") }
        }
        Text(when {
            pending -> "Syncing edits…"
            invalid -> "This draft could not be parsed. Fix it or validate for details before switching views."
            else -> "Exceptional edits only. Normal editing should change English fields in Cards view."
        }, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = raw, onValueChange = onRawChange, label = { Text("Whole file YAML") }, modifier = Modifier.fillMaxWidth().weight(1f), minLines = 12)
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
                            if (finding.canOverride) TextButton(onClick = { pending = finding }, enabled = !busy) { Text("Override") }
                        }
                    }
                }
            }
        }
    }
    pending?.let { finding ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = {
                Text(
                    if (finding.requiresOverrideWarning) {
                        "Override BLOCKING QA finding?"
                    } else {
                        "Override QA finding?"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (finding.requiresOverrideWarning) {
                        Text(
                            "WARNING: This finding is marked BLOCKING because QA considers the issue severe or systemic. Overriding it will allow this exact flagged content to proceed toward approval without correcting the finding.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Confirm only when you intentionally accept this blocking issue. The reason is retained in the QA audit record.")
                    }
                    OutlinedTextField(
                        reason,
                        { reason = it },
                        label = { Text("Override reason") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { pending = null; onOverride(finding.id, reason.trim()); reason = "" },
                    enabled = reason.isNotBlank() && !busy,
                ) { Text(if (finding.requiresOverrideWarning) "Override blocking finding" else "Override") }
            },
            dismissButton = { TextButton(onClick = { pending = null }, enabled = !busy) { Text("Cancel") } },
        )
    }
}

