/* RECOVERY STATUS: PARTIAL FILE FRAGMENT. */
package cloud.shadowmonarchbooks.intakeedit

// Exact recovered fragment of the v0.5.1 EntryCard area. The complete MainActivity.kt
// should be reconstructed from the surviving APK before this is treated as compilable source.

@Composable
private fun EntryCard(entry: EditorEntry, context: Context, onEnglishChange: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val focusRequester = remember(entry.locator) { FocusRequester() }
    var fieldValue by remember(entry.locator) { mutableStateOf(TextFieldValue(entry.english, selection = TextRange(entry.english.length))) }
    LaunchedEffect(entry.english) {
        if (entry.english != fieldValue.text) fieldValue = TextFieldValue(entry.english, selection = TextRange(entry.english.length))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = {
                val referenceTitle = if (entry.isRestricted) "SANITIZED TRANSLATION" else "DRAFT TRANSLATION"
                copyText(context, "raw + reference ${entry.locator}", "RAW:\n${entry.sourceJapanese}\n\n$referenceTitle:\n${entry.referenceTranslation}")
                fieldValue = fieldValue.copy(selection = TextRange(fieldValue.text.length))
                focusRequester.requestFocus()
            }) { Text("Copy both") }
            OutlinedTextField(value = fieldValue, onValueChange = { fieldValue = it; onEnglishChange(it.text) }, minLines = 3, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester))
            Row {
                TextButton(onClick = { fieldValue = applyInlineFormat(fieldValue, "b"); onEnglishChange(fieldValue.text); focusRequester.requestFocus() }) { Text("B", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { fieldValue = applyInlineFormat(fieldValue, "i"); onEnglishChange(fieldValue.text); focusRequester.requestFocus() }) { Text("I", fontStyle = FontStyle.Italic) }
            }
        }
    }
}

private fun applyInlineFormat(value: TextFieldValue, tag: String): TextFieldValue {
    val open = "[$tag]"; val close = "[/$tag]"
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val updated = buildString(value.text.length + open.length + close.length) {
        append(value.text, 0, start); append(open); append(value.text, start, end); append(close); append(value.text, end, value.text.length)
    }
    val selection = if (start == end) TextRange(start + open.length) else TextRange(start + open.length, end + open.length)
    return TextFieldValue(updated, selection = selection)
}
