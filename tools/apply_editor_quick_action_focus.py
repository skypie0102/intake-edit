from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    (ROOT / path).write_text(text)


path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt'
s = read(path)

# Focus manager is shared by the editor tree. Quick actions clear editing focus;
# tapping the OutlinedTextField itself still focuses normally and opens the IME.
if 'import androidx.compose.ui.platform.LocalFocusManager\n' not in s:
    s = s.replace(
        'import androidx.compose.ui.platform.LocalContext\n',
        'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalFocusManager\n',
        1,
    )

old = '''    val context = LocalContext.current\n    val scope = rememberCoroutineScope()\n'''
new = '''    val context = LocalContext.current\n    val focusManager = LocalFocusManager.current\n    val scope = rememberCoroutineScope()\n'''
if old not in s:
    raise AssertionError('EditorScreen root focus marker not found')
s = s.replace(old, new, 1)

old = '''    fun jumpToFirstMissing() {\n        val target = current.document.entries.firstOrNull { !it.isSupplied } ?: return\n'''
new = '''    fun jumpToFirstMissing() {\n        val target = current.document.entries.firstOrNull { !it.isSupplied } ?: return\n        focusManager.clearFocus(force = true)\n'''
if old not in s:
    raise AssertionError('jumpToFirstMissing marker not found')
s = s.replace(old, new, 1)

old = '''                            TextButton(\n                                onClick = { fillBlanksFromImport(overlay) },\n                                enabled = !busy && !importBusy,\n'''
new = '''                            TextButton(\n                                onClick = {\n                                    focusManager.clearFocus(force = true)\n                                    fillBlanksFromImport(overlay)\n                                },\n                                enabled = !busy && !importBusy,\n'''
if old not in s:
    raise AssertionError('Fill blanks action marker not found')
s = s.replace(old, new, 1)

old = '''                    IconButton(\n                        onClick = { importLauncher.launch(arrayOf("application/xml", "text/xml", "application/octet-stream", "*/*")) },\n                        enabled = !busy && !importBusy,\n'''
new = '''                    IconButton(\n                        onClick = {\n                            focusManager.clearFocus(force = true)\n                            importLauncher.launch(arrayOf("application/xml", "text/xml", "application/octet-stream", "*/*"))\n                        },\n                        enabled = !busy && !importBusy,\n'''
if old not in s:
    raise AssertionError('Import launcher action marker not found')
s = s.replace(old, new, 1)

old = '''    val context = LocalContext.current\n    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n    val focusRequester = remember(entry.locator) { FocusRequester() }\n'''
new = '''    val context = LocalContext.current\n    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n    val focusManager = LocalFocusManager.current\n    val focusRequester = remember(entry.locator) { FocusRequester() }\n'''
if old not in s:
    raise AssertionError('EntryCard focus marker not found')
s = s.replace(old, new, 1)

old = '''                IconButton(onClick = {\n                    val reference = importedTranslation?.let { "\\n\\nIMPORTED TRANSLATION:\\n$it" }.orEmpty()\n                    clipboard.setPrimaryClip(ClipData.newPlainText("raw ${entry.locator}", "RAW:\\n${entry.sourceJapanese}$reference"))\n                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()\n                    rich = rich.moveCaretToEnd()\n                    focusRequester.requestFocus()\n                }) { Icon(Icons.Default.ContentCopy, "Copy raw and imported text") }\n'''
new = '''                IconButton(onClick = {\n                    focusManager.clearFocus(force = true)\n                    val reference = importedTranslation?.let { "\\n\\nIMPORTED TRANSLATION:\\n$it" }.orEmpty()\n                    clipboard.setPrimaryClip(ClipData.newPlainText("raw ${entry.locator}", "RAW:\\n${entry.sourceJapanese}$reference"))\n                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()\n                }) { Icon(Icons.Default.ContentCopy, "Copy raw and imported text") }\n'''
if old not in s:
    raise AssertionError('Copy action block not found')
s = s.replace(old, new, 1)

old = '''                importedTranslation?.let { importedText ->\n                    TextButton(onClick = {\n                        rich = RichInlineState.plain(importedText)\n                        onUseImport(importedText)\n                        focusRequester.requestFocus()\n                    }) { Text("Use Import") }\n                }\n                IconButton(onClick = {\n                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()\n                    if (text.isNotEmpty()) {\n                        rich = RichInlineState.fromClipboard(text)\n                        onEnglishChange(rich.toMarkup())\n                        focusRequester.requestFocus()\n                    }\n                }, enabled = clipboard.hasPrimaryClip()) {\n'''
new = '''                importedTranslation?.let { importedText ->\n                    TextButton(onClick = {\n                        focusManager.clearFocus(force = true)\n                        rich = RichInlineState.plain(importedText)\n                        onUseImport(importedText)\n                    }) { Text("Use Import") }\n                }\n                IconButton(onClick = {\n                    focusManager.clearFocus(force = true)\n                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()\n                    if (text.isNotEmpty()) {\n                        rich = RichInlineState.fromClipboard(text)\n                        onEnglishChange(rich.toMarkup())\n                    }\n                }, enabled = clipboard.hasPrimaryClip()) {\n'''
if old not in s:
    raise AssertionError('Use Import / Paste action block not found')
s = s.replace(old, new, 1)

write(path, s)

# Patch release.
path = 'app/build.gradle.kts'
s = read(path)
if 'versionCode = 24' not in s or 'versionName = "0.8.1"' not in s:
    raise AssertionError('expected v0.8.1 version not found')
s = s.replace('versionCode = 24', 'versionCode = 25', 1)
s = s.replace('versionName = "0.8.1"', 'versionName = "0.8.2"', 1)
write(path, s)
