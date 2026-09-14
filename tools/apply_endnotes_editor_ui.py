from pathlib import Path

path = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt")
s = path.read_text()

# Imports used by the endnote button/dialog.
if "import androidx.compose.ui.text.TextRange\n" not in s:
    s = s.replace(
        "import androidx.compose.ui.platform.LocalFocusManager\n",
        "import androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.text.TextRange\n",
        1,
    )
if "import androidx.compose.ui.text.style.TextDecoration\n" not in s:
    s = s.replace(
        "import androidx.compose.ui.text.font.FontWeight\n",
        "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextDecoration\n",
        1,
    )

old = '''    fun updateEnglish(locator: String, english: String) {\n        val index = current.document.entries.indexOfFirst { it.locator == locator }\n        require(index >= 0) { "Editor entry not found: $locator" }\n        val entries = current.document.entries.toMutableList()\n        entries[index] = entries[index].copy(english = english)\n        val wasReviewed = current.document.editorReviewComplete\n        val document = current.document.copy(entries = entries, editorReviewComplete = false)\n        var raw = IntakeParser.patchEnglishAt(current.raw, index, english)\n        if (wasReviewed) raw = IntakeParser.patchReviewComplete(raw, false)\n        current = current.copy(raw = raw, document = document)\n        onDraft(current)\n    }\n'''
new = '''    fun updateEnglish(locator: String, english: String) {\n        val index = current.document.entries.indexOfFirst { it.locator == locator }\n        require(index >= 0) { "Editor entry not found: $locator" }\n        val entries = current.document.entries.toMutableList()\n        entries[index] = entries[index].copy(english = english)\n        val document = EndnoteIntegrity.prune(\n            current.document.copy(entries = entries, editorReviewComplete = false),\n        )\n        updateDocument(document)\n    }\n\n    fun saveEndnote(locator: String, english: String, note: EndnoteDefinition) {\n        updateDocument(EndnoteIntegrity.upsert(current.document, locator, english, note))\n        editorNotice = "Endnote saved."\n    }\n\n    fun removeEndnote(locator: String, english: String, noteId: String) {\n        updateDocument(EndnoteIntegrity.remove(current.document, locator, english, noteId))\n        editorNotice = "Endnote removed."\n    }\n'''
if old not in s:
    raise AssertionError("updateEnglish marker not found")
s = s.replace(old, new, 1)

old = '''    fun removeAllEnglish() {\n        val cleared = current.document.entries.map { it.copy(english = "") }\n        updateDocument(current.document.copy(entries = cleared, editorReviewComplete = false))\n        editorNotice = "All English entries were cleared."\n    }\n'''
new = '''    fun removeAllEnglish() {\n        val cleared = current.document.entries.map { it.copy(english = "") }\n        updateDocument(\n            current.document.copy(\n                entries = cleared,\n                endnotes = emptyList(),\n                editorReviewComplete = false,\n            ),\n        )\n        editorNotice = "All English entries and their endnotes were cleared."\n    }\n'''
if old not in s:
    raise AssertionError("removeAllEnglish marker not found")
s = s.replace(old, new, 1)

old = '''                            EntryCard(\n                                entry = entry,\n                                importedTranslation = imported?.translationFor(entry.locator),\n                                onUseImport = { text -> useImported(entry.locator, text) },\n                                onEnglishChange = { english -> updateEnglish(entry.locator, english) },\n                            )\n'''
new = '''                            EntryCard(\n                                entry = entry,\n                                endnotes = current.document.endnotes.filter { it.locator == entry.locator },\n                                importedTranslation = imported?.translationFor(entry.locator),\n                                nextEndnoteId = { EndnoteIntegrity.nextId(current.document, entry.locator) },\n                                onUseImport = { text -> useImported(entry.locator, text) },\n                                onEnglishChange = { english -> updateEnglish(entry.locator, english) },\n                                onSaveEndnote = { english, note -> saveEndnote(entry.locator, english, note) },\n                                onRemoveEndnote = { english, noteId -> removeEndnote(entry.locator, english, noteId) },\n                            )\n'''
if old not in s:
    raise AssertionError("EntryCard invocation marker not found")
s = s.replace(old, new, 1)

start = s.index("@Composable\nprivate fun EntryCard(")
end = s.index("@Composable\nprivate fun WholeFileEditor", start)
entry_card = r'''@Composable
private fun EntryCard(
    entry: EditorEntry,
    endnotes: List<EndnoteDefinition>,
    importedTranslation: String?,
    nextEndnoteId: () -> String,
    onUseImport: (String) -> Unit,
    onEnglishChange: (String) -> Unit,
    onSaveEndnote: (String, EndnoteDefinition) -> Unit,
    onRemoveEndnote: (String, String) -> Unit,
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
    var editingEndnoteId by remember(entry.locator) { mutableStateOf<String?>(null) }
    var pendingSelection by remember(entry.locator) { mutableStateOf<TextRange?>(null) }
    var endnoteAnchor by remember(entry.locator) { mutableStateOf("") }
    var endnoteDraft by remember(entry.locator) { mutableStateOf("") }

    fun openExistingEndnote(id: String) {
        val definition = endnotes.firstOrNull { it.id == id } ?: return
        editingEndnoteId = id
        pendingSelection = rich.rangeForEndnote(id)
        endnoteAnchor = rich.anchorForEndnote(id)
        endnoteDraft = definition.content
        showEndnoteDialog = true
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

    if (showEndnoteDialog) {
        AlertDialog(
            onDismissRequest = { showEndnoteDialog = false },
            title = { Text(if (editingEndnoteId == null) "Add endnote" else "Edit endnote") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anchor", style = MaterialTheme.typography.labelLarge)
                    Text(endnoteAnchor.ifBlank { "—" })
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
                            onSaveEndnote(
                                rich.toMarkup(),
                                EndnoteDefinition(existingId, entry.locator, content),
                            )
                        } else {
                            val selection = pendingSelection
                            if (selection != null) {
                                val id = nextEndnoteId()
                                rich = rich.copy(selection = selection).applyEndnote(id)
                                onSaveEndnote(
                                    rich.toMarkup(),
                                    EndnoteDefinition(id, entry.locator, content),
                                )
                            }
                        }
                        showEndnoteDialog = false
                    },
                    enabled = endnoteDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    editingEndnoteId?.let { id ->
                        TextButton(
                            onClick = {
                                rich = rich.removeEndnote(id)
                                onRemoveEndnote(rich.toMarkup(), id)
                                showEndnoteDialog = false
                            },
                        ) { Text("Remove") }
                    }
                    TextButton(onClick = { showEndnoteDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
}

'''
s = s[:start] + entry_card + s[end:]
s = s.replace(
    'Text("Direct schema-v5 YAML editor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)',
    'Text("Direct schema-v5/v6 YAML editor", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)',
    1,
)

path.write_text(s)
