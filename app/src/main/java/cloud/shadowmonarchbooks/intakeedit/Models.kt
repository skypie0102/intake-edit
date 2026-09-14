package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

data class RepoSettings(
    val owner: String = "skypie0102",
    val repo: String = "purelove",
    val branch: String = "main",
    val intakeRoot: String = "editor_input",
)

data class ChapterFile(val path: String, val volume: Int, val chapter: Int)
data class FileSnapshot(val path: String, val sha: String, val content: String)

data class EditorEntry(
    val locator: String,
    val sourceJapanese: String,
    val english: String,
) {
    val isSupplied: Boolean get() = InlineMarkup.hasVisibleText(english)
}

data class EditorDocument(
    val schemaVersion: Int,
    val volume: Int,
    val chapter: Int,
    val sourceHref: String,
    val sourceSha256: String,
    val readerFile: String,
    val englishTitle: String,
    val instructions: String,
    val editorReviewComplete: Boolean,
    val entries: List<EditorEntry>,
    val endnotes: List<EndnoteDefinition> = emptyList(),
) {
    val englishSupplied get() = entries.count { it.isSupplied }
    val englishTotal get() = entries.size
    val complete get() = editorReviewComplete && englishSupplied == englishTotal
}

object IntakeParser {
    private val jsonEnglishFieldRegex = Regex("""("english"\s*:\s*)("(?:\\.|[^"\\])*")""")
    private val jsonReviewFieldRegex = Regex("""("editor_review_complete"\s*:\s*)(true|false)""")
    private val yamlEnglishFieldRegex = Regex("""(?m)^(\s{2}english:\s*)(.*)$""")
    private val yamlReviewFieldRegex = Regex("""(?m)^(editor_review_complete:\s*)(true|false)\s*$""")
    private val yamlSchemaFieldRegex = Regex("""(?m)^(schema_version:\s*)(\d+)\s*$""")
    private val yamlEntriesFieldRegex = Regex("""(?m)^entries:\s*$""")
    private val yamlEndnotesFieldRegex = Regex("""(?m)^endnotes:(?:\s*\[\])?\s*$""")

    fun parse(raw: String): EditorDocument = if (isJson(raw)) parseJson(raw) else parseYaml(raw)

    private fun isJson(raw: String): Boolean = raw.firstOrNull { !it.isWhitespace() } == '{'

    private fun parseJson(raw: String): EditorDocument {
        val root = JSONObject(raw)
        val schemaVersion = root.optInt("schema_version", -1)
        require(schemaVersion == 6) {
            "This app version edits schema-v6 chapter documents only."
        }
        val endnoteArray = root.optJSONArray("endnotes")
            ?: error("Schema-v6 document is missing top-level endnotes array.")
        val endnotes = buildList {
            for (i in 0 until endnoteArray.length()) {
                val item = endnoteArray.getJSONObject(i)
                val allowed = setOf("id", "locator", "content")
                val unexpected = buildList {
                    val keys = item.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in allowed) add(key)
                    }
                }
                require(unexpected.isEmpty()) { "Endnote $i has unsupported field(s): ${unexpected.joinToString()}" }
                add(
                    EndnoteDefinition(
                        id = item.getString("id"),
                        locator = item.getString("locator"),
                        content = item.getString("content"),
                    ),
                )
            }
        }

        val array = root.optJSONArray("entries") ?: error("Missing top-level entries array.")
        val entries = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val allowedEntryFields = setOf("locator", "source_japanese", "english")
                val unexpected = buildList {
                    val keys = item.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key !in allowedEntryFields) add(key)
                    }
                }
                require(unexpected.isEmpty()) { "Entry $i has unsupported field(s): ${unexpected.joinToString()}" }
                add(
                    EditorEntry(
                        locator = item.getString("locator"),
                        sourceJapanese = item.getString("source_japanese"),
                        english = item.getString("english"),
                    ),
                )
            }
        }
        return EditorDocument(
            schemaVersion = schemaVersion,
            volume = root.getInt("volume"),
            chapter = root.getInt("chapter"),
            sourceHref = root.getString("source_href"),
            sourceSha256 = root.getString("source_sha256"),
            readerFile = root.getString("reader_file"),
            englishTitle = root.getString("english_title"),
            instructions = root.optString("instructions"),
            editorReviewComplete = root.optBoolean("editor_review_complete", false),
            entries = entries,
            endnotes = endnotes,
        ).also(EndnoteIntegrity::validate)
    }

    private fun parseYaml(raw: String): EditorDocument {
        val top = linkedMapOf<String, String>()
        val entryMaps = mutableListOf<Map<String, String>>()
        val endnoteMaps = mutableListOf<Map<String, String>>()
        var current: LinkedHashMap<String, String>? = null
        var section: String? = null
        var sawEntries = false
        var sawEndnotes = false

        fun flushItem() {
            current?.let { item ->
                when (section) {
                    "entries" -> entryMaps += LinkedHashMap(item)
                    "endnotes" -> endnoteMaps += LinkedHashMap(item)
                    else -> error("List item found outside a supported schema section.")
                }
            }
            current = null
        }

        raw.lineSequence().forEachIndexed { index, sourceLine ->
            val line = sourceLine.removeSuffix("\r")
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed

            when {
                line.startsWith("- ") -> {
                    flushItem()
                    require(section == "entries" || section == "endnotes") {
                        "Line ${index + 1}: list item outside entries/endnotes."
                    }
                    val map = linkedMapOf<String, String>()
                    val (key, value) = yamlPair(line.removePrefix("- "), index + 1)
                    map[key] = decodeYamlScalar(value, index + 1)
                    current = map
                }
                line.startsWith("  ") && current != null -> {
                    val (key, value) = yamlPair(line.substring(2), index + 1)
                    current!![key] = decodeYamlScalar(value, index + 1)
                }
                !line.startsWith(" ") -> {
                    flushItem()
                    val (key, value) = yamlPair(line, index + 1)
                    when (key) {
                        "entries" -> {
                            require(value.isBlank()) { "Line ${index + 1}: entries must be a block list." }
                            sawEntries = true
                            section = "entries"
                        }
                        "endnotes" -> {
                            sawEndnotes = true
                            if (value == "[]") {
                                section = null
                            } else {
                                require(value.isBlank()) { "Line ${index + 1}: endnotes must be a block list or []." }
                                section = "endnotes"
                            }
                        }
                        else -> {
                            section = null
                            top[key] = decodeYamlScalar(value, index + 1)
                        }
                    }
                }
                else -> error("Line ${index + 1}: unsupported YAML indentation in chapter document.")
            }
        }
        flushItem()

        fun required(key: String): String = top[key] ?: error("Missing top-level $key field.")
        fun requiredInt(key: String): Int = required(key).toIntOrNull() ?: error("Top-level $key must be an integer.")
        fun requiredBoolean(key: String): Boolean = when (required(key).lowercase()) {
            "true" -> true
            "false" -> false
            else -> error("Top-level $key must be a boolean.")
        }

        val schemaVersion = requiredInt("schema_version")
        require(schemaVersion == 6) {
            "This app version edits schema-v6 chapter documents only."
        }
        require(sawEntries && entryMaps.isNotEmpty()) { "Missing or empty top-level entries array." }
        require(sawEndnotes) { "Schema-v6 document is missing top-level endnotes list." }

        val endnotes = endnoteMaps.mapIndexed { index, item ->
            fun noteRequired(key: String): String = item[key] ?: error("Endnote $index is missing $key.")
            val unexpected = item.keys - setOf("id", "locator", "content")
            require(unexpected.isEmpty()) { "Endnote $index has unsupported field(s): ${unexpected.joinToString()}" }
            EndnoteDefinition(
                id = noteRequired("id"),
                locator = noteRequired("locator"),
                content = noteRequired("content"),
            )
        }

        val entries = entryMaps.mapIndexed { index, item ->
            fun entryRequired(key: String): String = item[key] ?: error("Entry $index is missing $key.")
            val unexpected = item.keys - setOf("locator", "source_japanese", "english")
            require(unexpected.isEmpty()) { "Entry $index has unsupported field(s): ${unexpected.joinToString()}" }
            EditorEntry(
                locator = entryRequired("locator"),
                sourceJapanese = entryRequired("source_japanese"),
                english = entryRequired("english"),
            )
        }

        return EditorDocument(
            schemaVersion = schemaVersion,
            volume = requiredInt("volume"),
            chapter = requiredInt("chapter"),
            sourceHref = required("source_href"),
            sourceSha256 = required("source_sha256"),
            readerFile = required("reader_file"),
            englishTitle = required("english_title"),
            instructions = top["instructions"].orEmpty(),
            editorReviewComplete = requiredBoolean("editor_review_complete"),
            entries = entries,
            endnotes = endnotes,
        ).also(EndnoteIntegrity::validate)
    }

    private fun yamlPair(text: String, lineNumber: Int): Pair<String, String> {
        val colon = text.indexOf(':')
        require(colon > 0) { "Line $lineNumber: expected a YAML key/value pair." }
        val key = text.substring(0, colon).trim()
        require(key.isNotEmpty()) { "Line $lineNumber: YAML key is blank." }
        return key to text.substring(colon + 1).trim()
    }

    private fun decodeYamlScalar(value: String, lineNumber: Int): String {
        val text = value.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("'") || text.startsWith("\"")) {
            require(text.length >= 2 && text.last() == text.first()) { "Line $lineNumber: unterminated YAML string." }
        }
        if (text.startsWith("'")) return text.substring(1, text.length - 1).replace("''", "'")
        if (!text.startsWith("\"")) return text
        return decodeDoubleQuotedYaml(text.substring(1, text.length - 1), lineNumber)
    }

    private fun decodeDoubleQuotedYaml(text: String, lineNumber: Int): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i++]
            if (ch != '\\') {
                out.append(ch)
                continue
            }
            require(i < text.length) { "Line $lineNumber: dangling YAML escape." }
            when (val esc = text[i++]) {
                '0' -> out.append('\u0000')
                'a' -> out.append('\u0007')
                'b' -> out.append('\b')
                't', '\t' -> out.append('\t')
                'n' -> out.append('\n')
                'v' -> out.append('\u000B')
                'f' -> out.append('\u000C')
                'r' -> out.append('\r')
                'e' -> out.append('\u001B')
                ' ' -> out.append(' ')
                '\"' -> out.append('\"')
                '/' -> out.append('/')
                '\\' -> out.append('\\')
                'N' -> out.append('\u0085')
                '_' -> out.append('\u00A0')
                'L' -> out.append('\u2028')
                'P' -> out.append('\u2029')
                'x' -> out.append(readHexEscape(text, i, 2, lineNumber).also { i += 2 }.toChar())
                'u' -> out.append(readHexEscape(text, i, 4, lineNumber).also { i += 4 }.toChar())
                'U' -> {
                    val codePoint = readHexEscape(text, i, 8, lineNumber)
                    i += 8
                    out.append(String(Character.toChars(codePoint)))
                }
                else -> error("Line $lineNumber: unsupported YAML escape \\$esc.")
            }
        }
        return out.toString()
    }

    private fun readHexEscape(text: String, start: Int, length: Int, lineNumber: Int): Int {
        require(start + length <= text.length) { "Line $lineNumber: incomplete YAML hex escape." }
        return text.substring(start, start + length).toIntOrNull(16)
            ?: error("Line $lineNumber: invalid YAML hex escape.")
    }

    private fun quoteString(value: String): String = buildString(value.length + 2) {
        append('\"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
        }
        append('\"')
    }

    fun patchEnglish(raw: String, entries: List<EditorEntry>): String {
        val regex = if (isJson(raw)) jsonEnglishFieldRegex else yamlEnglishFieldRegex
        val matches = regex.findAll(raw).toList()
        require(matches.size == entries.size) {
            "Found ${matches.size} english fields but ${entries.size} editor entries. Use Whole File mode for this file."
        }
        val out = StringBuilder(raw.length)
        var cursor = 0
        matches.forEachIndexed { index, match ->
            val group = match.groups[2] ?: error("Could not locate English value at entry $index.")
            out.append(raw, cursor, group.range.first)
            out.append(quoteString(entries[index].english))
            cursor = group.range.last + 1
        }
        out.append(raw, cursor, raw.length)
        return out.toString()
    }

    fun patchEnglishAt(raw: String, entryIndex: Int, english: String): String {
        require(entryIndex >= 0) { "Entry index must not be negative." }
        val regex = if (isJson(raw)) jsonEnglishFieldRegex else yamlEnglishFieldRegex
        val match = regex.findAll(raw).drop(entryIndex).firstOrNull()
            ?: error("Could not locate English value at entry $entryIndex. Use Whole File mode for this file.")
        val group = match.groups[2] ?: error("Could not locate English value at entry $entryIndex.")
        return buildString(raw.length + english.length + 2) {
            append(raw, 0, group.range.first)
            append(quoteString(english))
            append(raw, group.range.last + 1, raw.length)
        }
    }

    fun patchReviewComplete(raw: String, editorReviewComplete: Boolean): String {
        val regex = if (isJson(raw)) jsonReviewFieldRegex else yamlReviewFieldRegex
        val match = regex.find(raw) ?: error("Could not locate top-level editor_review_complete boolean. Use Whole File mode for this file.")
        val group = match.groups[2] ?: error("Could not locate editor_review_complete value.")
        return buildString(raw.length) {
            append(raw, 0, group.range.first)
            append(if (editorReviewComplete) "true" else "false")
            append(raw, group.range.last + 1, raw.length)
        }
    }

    private fun patchSchemaVersion(raw: String, schemaVersion: Int): String {
        if (isJson(raw)) return raw
        val match = yamlSchemaFieldRegex.find(raw) ?: error("Could not locate top-level schema_version.")
        val group = match.groups[2] ?: error("Could not locate schema_version value.")
        return buildString(raw.length) {
            append(raw, 0, group.range.first)
            append(schemaVersion)
            append(raw, group.range.last + 1, raw.length)
        }
    }

    private fun renderEndnotesYaml(endnotes: List<EndnoteDefinition>): String {
        if (endnotes.isEmpty()) return "endnotes: []\n"
        return buildString {
            append("endnotes:\n")
            endnotes.forEach { note ->
                append("- id: ").append(note.id).append('\n')
                append("  locator: ").append(note.locator).append('\n')
                append("  content: ").append(quoteString(note.content)).append('\n')
            }
        }
    }

    private fun patchEndnotesYaml(raw: String, endnotes: List<EndnoteDefinition>): String {
        val rendered = renderEndnotesYaml(endnotes)
        val lines = raw.lines().toMutableList()
        val startIndex = lines.indexOfFirst { it.startsWith("endnotes:") }
        require(startIndex >= 0) { "Schema-v6 document is missing top-level endnotes." }
        var endIndex = startIndex + 1
        while (endIndex < lines.size) {
            val line = lines[endIndex]
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("- ")) break
            if (line.startsWith("- ")) {
                endIndex++
                while (endIndex < lines.size && lines[endIndex].startsWith("  ")) endIndex++
                continue
            }
            endIndex++
        }
        val renderedLines = rendered.trimEnd('\n').lines()
        lines.subList(startIndex, endIndex).clear()
        lines.addAll(startIndex, renderedLines)
        return lines.joinToString("\n").let { if (raw.endsWith("\n")) "$it\n" else it }
    }

    private fun patchJsonEndnotes(raw: String, document: EditorDocument): String {
        val root = JSONObject(raw)
        root.put("schema_version", document.schemaVersion)
        root.put("editor_review_complete", document.editorReviewComplete)
        val entries = root.getJSONArray("entries")
        require(entries.length() == document.entries.size) { "JSON entry count changed unexpectedly." }
        document.entries.forEachIndexed { index, entry -> entries.getJSONObject(index).put("english", entry.english) }
        require(document.schemaVersion == 6) { "Only schema-v6 documents can be written." }
        val notes = JSONArray()
        document.endnotes.forEach { note ->
            notes.put(
                JSONObject()
                    .put("id", note.id)
                    .put("locator", note.locator)
                    .put("content", note.content),
            )
        }
        root.put("endnotes", notes)
        return root.toString(2) + "\n"
    }

    fun patchDocument(raw: String, document: EditorDocument): String {
        require(document.schemaVersion == 6) { "Only schema-v6 documents can be written." }
        EndnoteIntegrity.validate(document)
        if (isJson(raw)) return patchJsonEndnotes(raw, document)
        var patched = patchReviewComplete(patchEnglish(raw, document.entries), document.editorReviewComplete)
        patched = patchSchemaVersion(patched, 6)
        patched = patchEndnotesYaml(patched, document.endnotes)
        return patched
    }

    fun validate(raw: String): Result<Unit> = runCatching {
        if (isJson(raw)) {
            val root = JSONObject(raw)
            require(root.has("editor_review_complete")) { "Missing top-level editor_review_complete boolean." }
            require(root.get("editor_review_complete") is Boolean) { "editor_review_complete must be a boolean." }
            require(root.has("entries")) { "Missing top-level entries array." }
            require(root.get("entries") is JSONArray) { "entries must be an array." }
        } else {
            require(yamlReviewFieldRegex.containsMatchIn(raw)) { "Missing top-level editor_review_complete boolean." }
            require(yamlEntriesFieldRegex.containsMatchIn(raw)) { "Missing top-level entries array." }
        }

        val document = parse(raw)
        document.entries.forEach { entry ->
            require(entry.sourceJapanese.isNotBlank()) { "${entry.locator}: raw source is blank." }
            if (entry.english.isNotBlank()) {
                InlineMarkup.validationError(entry.english)?.let { throw IllegalArgumentException("${entry.locator}: $it") }
                require(InlineMarkup.visibleText(entry.english).isNotBlank()) { "${entry.locator}: English formatting contains no visible text." }
            }
        }
        EndnoteIntegrity.validate(document)
    }
}

object DiffText {
    fun compact(oldText: String, newText: String, limit: Int = 12_000): String {
        if (oldText == newText) return "No changes."
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val out = StringBuilder()
        for (i in 0 until max(oldLines.size, newLines.size)) {
            val old = oldLines.getOrNull(i)
            val new = newLines.getOrNull(i)
            if (old == new) continue
            out.append("@@ line ${i + 1} @@\n")
            if (old != null) out.append("- ").append(old).append('\n')
            if (new != null) out.append("+ ").append(new).append('\n')
            if (out.length > limit) return out.take(limit).toString().plus("…diff truncated…\n").trimEnd()
        }
        return out.toString().trimEnd()
    }
}
