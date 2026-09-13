package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.text.Normalizer
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/** Local-only rough translation reference imported from SDLXLIFF/XLIFF. */
data class ImportedTranslationOverlay(
    val chapterPath: String,
    val sourceSha256: String,
    val fileName: String,
    val translations: Map<String, String>,
    val matchedCount: Int,
    val totalEntries: Int,
    val sourceParagraphs: Int,
    val targetParagraphs: Int,
    val alignment: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
) {
    fun translationFor(locator: String): String? = translations[locator]?.takeIf { it.isNotBlank() }
}

class TranslationImportStore(context: Context) {
    private val dir = File(context.filesDir, "translation_imports").also { it.mkdirs() }

    private fun file(path: String): File {
        val key = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        return File(dir, "$key.json")
    }

    fun save(overlay: ImportedTranslationOverlay): Boolean = runCatching {
        val translations = JSONObject()
        overlay.translations.forEach { (locator, text) -> translations.put(locator, text) }
        val payload = JSONObject()
            .put("chapter_path", overlay.chapterPath)
            .put("source_sha256", overlay.sourceSha256)
            .put("file_name", overlay.fileName)
            .put("matched_count", overlay.matchedCount)
            .put("total_entries", overlay.totalEntries)
            .put("source_paragraphs", overlay.sourceParagraphs)
            .put("target_paragraphs", overlay.targetParagraphs)
            .put("alignment", overlay.alignment)
            .put("imported_at_ms", overlay.importedAtMillis)
            .put("translations", translations)
            .toString()
        val atomic = AtomicFile(file(overlay.chapterPath))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(payload.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            stream?.let { atomic.failWrite(it) }
            throw t
        }
        true
    }.getOrDefault(false)

    fun load(path: String, sourceSha256: String): ImportedTranslationOverlay? {
        val target = file(path)
        if (!target.exists()) return null
        return runCatching {
            val root = JSONObject(String(AtomicFile(target).readFully(), Charsets.UTF_8))
            if (root.optString("source_sha256") != sourceSha256) return@runCatching null
            val translationsObj = root.getJSONObject("translations")
            val translations = linkedMapOf<String, String>()
            val keys = translationsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                translations[key] = translationsObj.optString(key)
            }
            ImportedTranslationOverlay(
                chapterPath = root.optString("chapter_path", path),
                sourceSha256 = root.getString("source_sha256"),
                fileName = root.optString("file_name", "Imported XLIFF"),
                translations = translations,
                matchedCount = root.optInt("matched_count", translations.size),
                totalEntries = root.optInt("total_entries", translations.size),
                sourceParagraphs = root.optInt("source_paragraphs", translations.size),
                targetParagraphs = root.optInt("target_paragraphs", translations.size),
                alignment = root.optString("alignment", "Verified"),
                importedAtMillis = root.optLong("imported_at_ms", 0L),
            )
        }.getOrNull()
    }

    fun delete(path: String): Boolean {
        AtomicFile(file(path)).delete()
        return !file(path).exists()
    }
}

object XliffTranslationImporter {
    private data class PairText(val source: String, val target: String)

    fun parse(
        raw: String,
        fileName: String,
        chapterPath: String,
        document: EditorDocument,
    ): ImportedTranslationOverlay {
        val pairs = parsePairs(raw)
        require(pairs.isNotEmpty()) { "No source/target translation units were found in this XLIFF file." }

        val sourceParagraphs = mutableListOf<String>()
        val targetParagraphs = mutableListOf<String>()
        pairs.forEach { pair ->
            val sourceParts = extractParagraphsOrSegment(pair.source)
            val targetParts = extractParagraphsOrSegment(pair.target)
            if (sourceParts.size == targetParts.size) {
                sourceParagraphs += sourceParts
                targetParagraphs += targetParts
            } else if (sourceParts.size == 1 && targetParts.size == 1) {
                sourceParagraphs += sourceParts.first()
                targetParagraphs += targetParts.first()
            } else {
                // Keep positional integrity: mismatched unit internals are not flattened into neighboring units.
                val common = minOf(sourceParts.size, targetParts.size)
                repeat(common) { index ->
                    sourceParagraphs += sourceParts[index]
                    targetParagraphs += targetParts[index]
                }
            }
        }

        require(sourceParagraphs.isNotEmpty()) { "The XLIFF source contains no usable text segments." }
        require(targetParagraphs.isNotEmpty()) { "The XLIFF target contains no usable English segments." }

        val translations = linkedMapOf<String, String>()
        val usedSourceIndices = mutableSetOf<Int>()

        // First pass: same-position exact normalized source match. This is the safest and fastest path.
        document.entries.forEachIndexed { index, entry ->
            if (index >= sourceParagraphs.size || index >= targetParagraphs.size) return@forEachIndexed
            if (normalizeForMatch(entry.sourceJapanese) == normalizeForMatch(sourceParagraphs[index])) {
                targetParagraphs[index].trim().takeIf { it.isNotBlank() }?.let { translations[entry.locator] = it }
                usedSourceIndices += index
            }
        }

        // Second pass: exact normalized source lookup for shifted/partial files, but only when the source is unique.
        val sourceIndex = mutableMapOf<String, MutableList<Int>>()
        sourceParagraphs.forEachIndexed { index, source ->
            sourceIndex.getOrPut(normalizeForMatch(source)) { mutableListOf() } += index
        }
        document.entries.forEach { entry ->
            if (translations.containsKey(entry.locator)) return@forEach
            val candidates = sourceIndex[normalizeForMatch(entry.sourceJapanese)].orEmpty().filter { it !in usedSourceIndices }
            if (candidates.size == 1) {
                val index = candidates.single()
                if (index < targetParagraphs.size) {
                    targetParagraphs[index].trim().takeIf { it.isNotBlank() }?.let {
                        translations[entry.locator] = it
                        usedSourceIndices += index
                    }
                }
            }
        }

        val matched = translations.size
        val total = document.entries.size
        val minimumCredible = if (total <= 2) 1 else ceil(total * 0.60).toInt()
        require(matched >= minimumCredible) {
            "This file does not appear to match this chapter: only $matched/$total source paragraphs matched exactly."
        }

        val exact = matched == total && sourceParagraphs.size == total && targetParagraphs.size == total
        return ImportedTranslationOverlay(
            chapterPath = chapterPath,
            sourceSha256 = document.sourceSha256,
            fileName = fileName,
            translations = translations,
            matchedCount = matched,
            totalEntries = total,
            sourceParagraphs = sourceParagraphs.size,
            targetParagraphs = targetParagraphs.size,
            alignment = if (exact) "Exact" else "Verified partial",
        )
    }

    private fun parsePairs(raw: String): List<PairText> {
        val document = parseXml(raw)
        val pairs = mutableListOf<PairText>()

        // XLIFF 1.2: trans-unit/source/target.
        elementsByLocalName(document.documentElement, "trans-unit").forEach { unit ->
            val source = childByLocalName(unit, "source")?.textContent.orEmpty()
            val target = childByLocalName(unit, "target")?.textContent.orEmpty()
            if (source.isNotBlank() && target.isNotBlank()) pairs += PairText(source, target)
        }
        if (pairs.isNotEmpty()) return pairs

        // XLIFF 2.x: unit/segment/source/target.
        elementsByLocalName(document.documentElement, "segment").forEach { segment ->
            val source = childByLocalName(segment, "source")?.textContent.orEmpty()
            val target = childByLocalName(segment, "target")?.textContent.orEmpty()
            if (source.isNotBlank() && target.isNotBlank()) pairs += PairText(source, target)
        }
        return pairs
    }

    private fun extractParagraphsOrSegment(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        if (!Regex("(?i)<p(?:\\s|>)").containsMatchIn(trimmed)) return listOf(cleanSegmentText(trimmed)).filter { it.isNotBlank() }

        val xml = sanitizeEmbeddedXml(trimmed)
        val parsed = runCatching { parseXml(xml) }.getOrNull()
        if (parsed != null) {
            return elementsByLocalName(parsed.documentElement, "p")
                .map { cleanSegmentText(it.textContent.orEmpty()) }
                .filter { it.isNotBlank() }
        }

        // Fallback for HTML-ish targets that are not perfectly XML-well-formed.
        return Regex("(?is)<p\\b[^>]*>(.*?)</p>").findAll(trimmed)
            .map { match ->
                cleanSegmentText(
                    match.groupValues[1]
                        .replace(Regex("(?is)<br\\s*/?>"), "\n")
                        .replace(Regex("(?is)<[^>]+>"), ""),
                )
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun cleanSegmentText(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("[\\t\\r\\n ]+"), " ")
        .trim()

    private fun normalizeForMatch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), "")
        .trim()

    private fun sanitizeEmbeddedXml(value: String): String = value
        .replace(Regex("(?is)<\\?xml[^>]*\\?>"), "")
        .replace(Regex("(?is)<!DOCTYPE[^>]*>"), "")
        .replace("&nbsp;", "&#160;")
        .trim()

    private fun parseXml(raw: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
    }

    private fun childByLocalName(parent: Element, name: String): Element? {
        var child: Node? = parent.firstChild
        while (child != null) {
            if (child is Element && (child.localName == name || child.nodeName.substringAfter(':') == name)) return child
            child = child.nextSibling
        }
        return null
    }

    private fun elementsByLocalName(root: Element, name: String): List<Element> {
        val out = mutableListOf<Element>()
        fun visit(node: Node) {
            if (node is Element && (node.localName == name || node.nodeName.substringAfter(':') == name)) out += node
            var child = node.firstChild
            while (child != null) {
                visit(child)
                child = child.nextSibling
            }
        }
        visit(root)
        return out
    }
}
