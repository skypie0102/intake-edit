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
    val kind: String,
    val sourceJapanese: String,
    val safeTranslation: String,
    val sanitizedTranslation: String,
    val english: String,
) {
    val isRestricted: Boolean get() = kind == "restricted"
    val referenceTranslation: String get() = if (isRestricted) sanitizedTranslation else safeTranslation
    val isSafeRevised: Boolean get() = !isRestricted && english != safeTranslation
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
) {
    val restrictedEntries get() = entries.filter { it.isRestricted }
    val restrictedSupplied get() = restrictedEntries.count { InlineMarkup.hasVisibleText(it.english) }
    val restrictedTotal get() = restrictedEntries.size
    val safeRevised get() = entries.count { it.isSafeRevised }
    val complete get() = editorReviewComplete && restrictedSupplied == restrictedTotal
}

object IntakeParser {
    private val englishFieldRegex = Regex("""("english"\s*:\s*)("(?:\\.|[^"\\])*")""")
    private val reviewFieldRegex = Regex("""("editor_review_complete"\s*:\s*)(true|false)""")

    fun parse(raw: String): EditorDocument {
        val root = JSONObject(raw)
        require(root.optInt("schema_version", -1) == 4) { "This app version edits schema-v4 chapter documents only." }
        val array = root.optJSONArray("entries") ?: error("Missing top-level entries array.")
        val entries = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val kind = item.optString("kind")
                require(kind == "safe" || kind == "restricted") { "Entry $i has invalid kind '$kind'." }
                add(EditorEntry(
                    locator = item.getString("locator"), kind = kind,
                    sourceJapanese = item.getString("source_japanese"),
                    safeTranslation = item.optString("safe_translation"),
                    sanitizedTranslation = item.optString("sanitized_translation"),
                    english = item.getString("english"),
                ))
            }
        }
        return EditorDocument(
            schemaVersion = root.getInt("schema_version"), volume = root.getInt("volume"), chapter = root.getInt("chapter"),
            sourceHref = root.getString("source_href"), sourceSha256 = root.getString("source_sha256"),
            readerFile = root.getString("reader_file"), englishTitle = root.getString("english_title"),
            instructions = root.optString("instructions"), editorReviewComplete = root.optBoolean("editor_review_complete", false),
            entries = entries,
        )
    }

    fun patchEnglish(raw: String, entries: List<EditorEntry>): String {
        val matches = englishFieldRegex.findAll(raw).toList()
        require(matches.size == entries.size) { "Found ${matches.size} english fields but ${entries.size} editor entries. Use Whole File mode for this file." }
        val out = StringBuilder(raw.length)
        var cursor = 0
        matches.forEachIndexed { index, match ->
            val group = match.groups[2] ?: error("Could not locate english JSON string at entry $index.")
            out.append(raw, cursor, group.range.first)
            out.append(JSONObject.quote(entries[index].english))
            cursor = group.range.last + 1
        }
        out.append(raw, cursor, raw.length)
        return out.toString()
    }

    fun patchDocument(raw: String, document: EditorDocument): String {
        val patched = patchEnglish(raw, document.entries)
        val match = reviewFieldRegex.find(patched) ?: error("Could not locate top-level editor_review_complete boolean. Use Whole File mode for this file.")
        val group = match.groups[2] ?: error("Could not locate editor_review_complete value.")
        return buildString(patched.length) {
            append(patched, 0, group.range.first)
            append(if (document.editorReviewComplete) "true" else "false")
            append(patched, group.range.last + 1, patched.length)
        }
    }

    fun validate(raw: String): Result<Unit> = runCatching {
        val root = JSONObject(raw)
        require(root.has("editor_review_complete")) { "Missing top-level editor_review_complete boolean." }
        require(root.get("editor_review_complete") is Boolean) { "editor_review_complete must be a boolean." }
        require(root.has("entries")) { "Missing top-level entries array." }
        require(root.get("entries") is JSONArray) { "entries must be an array." }
        val document = parse(raw)
        document.entries.forEach { entry ->
            require(entry.sourceJapanese.isNotBlank()) { "${entry.locator}: raw source is blank." }
            if (entry.english.isNotBlank()) {
                InlineMarkup.validationError(entry.english)?.let { throw IllegalArgumentException("${entry.locator}: $it") }
                require(InlineMarkup.visibleText(entry.english).isNotBlank()) { "${entry.locator}: English formatting contains no visible text." }
            }
            if (entry.isRestricted) {
                require(entry.sanitizedTranslation.isNotBlank()) { "${entry.locator}: restricted entry is missing sanitized_translation." }
            } else {
                require(entry.safeTranslation.isNotBlank()) { "${entry.locator}: safe entry is missing safe_translation." }
                require(InlineMarkup.hasVisibleText(entry.english)) { "${entry.locator}: safe English must remain non-blank and have valid inline formatting." }
            }
        }
    }
}

object DiffText {
    fun compact(oldText: String, newText: String, limit: Int = 12_000): String {
        if (oldText == newText) return "No changes."
        val oldLines = oldText.lines(); val newLines = newText.lines(); val out = StringBuilder()
        for (i in 0 until max(oldLines.size, newLines.size)) {
            val old = oldLines.getOrNull(i); val new = newLines.getOrNull(i)
            if (old == new) continue
            out.append("@@ line ${i + 1} @@\n")
            if (old != null) out.append("- ").append(old).append('\n')
            if (new != null) out.append("+ ").append(new).append('\n')
            if (out.length > limit) return (out.take(limit) + "…diff truncated…\n").trimEnd()
        }
        return out.toString().trimEnd()
    }
}
