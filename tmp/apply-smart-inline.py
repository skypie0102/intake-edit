from pathlib import Path

path = Path('app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt')
text = path.read_text(encoding='utf-8')

start = text.index('@Composable\nprivate fun EntryCard(')
end = text.index('@Composable\nprivate fun WholeFileEditor', start)
replacement = '''@Composable
private fun EntryCard(entry: EditorEntry, onEnglishChange: (String) -> Unit) {
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.locator, fontWeight = FontWeight.Bold)
                    Text(if (entry.isRestricted) "RESTRICTED" else "SAFE", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                    val referenceTitle = if (entry.isRestricted) "SANITIZED TRANSLATION" else "DRAFT TRANSLATION"
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "raw + reference ${entry.locator}",
                            "RAW:\n${entry.sourceJapanese}\n\n$referenceTitle:\n${entry.referenceTranslation}",
                        ),
                    )
                    rich = rich.moveCaretToEnd()
                    focusRequester.requestFocus()
                }) { Icon(Icons.Default.ContentCopy, null); Text("Copy both") }
            }
            DisplayBlock("Raw", entry.sourceJapanese)
            DisplayBlock(if (entry.isRestricted) "Sanitized Translation" else "Draft Translation", entry.referenceTranslation)
            OutlinedTextField(
                value = rich.asTextFieldValue(),
                onValueChange = { next ->
                    rich = rich.edited(next)
                    onEnglishChange(rich.toMarkup())
                },
                label = { Text(if (entry.isRestricted) "English Supplied" else "English Revision") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                TextButton(onClick = {
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (text.isNotEmpty()) {
                        rich = RichInlineState.fromClipboard(text)
                        onEnglishChange(rich.toMarkup())
                        focusRequester.requestFocus()
                    }
                }, enabled = clipboard.hasPrimaryClip()) { Icon(Icons.Default.ContentPaste, null); Text("Paste") }
            }
        }
    }
}

'''
text = text[:start] + replacement + text[end:]
path.write_text(text, encoding='utf-8')
print('Applied smart visual inline editor patch')
