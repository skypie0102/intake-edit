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

    private fun file(path: String, machine: Boolean = false, machineKey: String? = null, projectKey: String? = null): File {
        val key = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        if (!machine && projectKey != null) {
            val identity = (path + "\u0000" + projectKey).toByteArray(Charsets.UTF_8)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(identity).joinToString("") { "%02x".format(it) }
            return File(dir, "import-$hash.json")
        }
        if (machine && machineKey != null) {
            val identity = (path + "\u0000" + machineKey).toByteArray(Charsets.UTF_8)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(identity).joinToString("") { "%02x".format(it) }
            return File(dir, "machine-$hash.json")
        }
        return File(dir, if (machine) "$key.machine.json" else "$key.json")
    }

    fun save(overlay: ImportedTranslationOverlay, machine: Boolean = false, machineKey: String? = null, projectKey: String? = null): Boolean = runCatching {
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
        val atomic = AtomicFile(file(overlay.chapterPath, machine, machineKey, projectKey))
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

    fun load(path: String, sourceSha256: String, machine: Boolean = false, machineKey: String? = null, projectKey: String? = null): ImportedTranslationOverlay? {
        val target = file(path, machine, machineKey, projectKey)
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

    fun delete(path: String, machine: Boolean = false, machineKey: String? = null, projectKey: String? = null): Boolean {
        AtomicFile(file(path, machine, machineKey, projectKey)).delete()
        return !file(path, machine, machineKey, projectKey).exists()
    }
}

/**
 * Import translated XHTML as a positional chapter reference, or fall back to XLIFF's
 * source-verified alignment. XHTML does not carry a separate Japanese source column.
 */
object TranslationReferenceImporter {
    private val xhtmlRoot = Regex(
        """(?is)^\s*(?:\uFEFF)?(?:<\?xml[^>]*\?>\s*)?(?:<!DOCTYPE[^>]*>\s*)?<(?:[\w.-]+:)?html(?:\s|>)""",
    )

    fun parse(
        raw: String,
        fileName: String,
        chapterPath: String,
        document: EditorDocument,
    ): ImportedTranslationOverlay =
        if (fileName.endsWith(".xhtml", ignoreCase = true) || xhtmlRoot.containsMatchIn(raw)) {
            XliffTranslationImporter.parseXhtml(raw, fileName, chapterPath, document)
        } else {
            XliffTranslationImporter.parse(raw, fileName, chapterPath, document)
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
            val sourceParts = extractBlocksOrSegment(pair.source)
            val targetParts = extractBlocksOrSegment(pair.target)
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
            "This file does not appear to match this chapter: only $matched/$total source blocks matched exactly."
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

    /**
     * Whole translated XHTML is aligned only when its nonempty h1/h2/p structure
     * exactly matches the chapter entry structure. Unlike XLIFF, a translated XHTML
     * file cannot be source-verified by matching Japanese strings.
     */
    fun parseXhtml(
        raw: String,
        fileName: String,
        chapterPath: String,
        document: EditorDocument,
    ): ImportedTranslationOverlay {
        val root = parseXml(sanitizeEmbeddedXml(raw.trim().removePrefix("\uFEFF")))
            .documentElement
        require(localName(root) == "html") { "The selected file is not an XHTML document." }
        val body = elementsByLocalName(root, "body").firstOrNull()
            ?: throw IllegalArgumentException("The XHTML document has no body.")
        val blocks = translatableBlockElements(body)
            .map { element -> localName(element) to blockText(element) }
            .filter { (_, text) -> text.isNotBlank() }

        require(blocks.isNotEmpty()) { "The XHTML document has no non-empty h1, h2, or p blocks." }

        // When the exported filename contains a recognizable chapter number, guard
        // against importing another chapter with an otherwise identical structure.
        val chapterInFile = Regex(
            """(?i)(?:^|[_. -])ch(?:apter)?[_. -]*0*(\d+)(?=[_. -]|$)""",
        ).find(fileName)?.groupValues?.get(1)?.toIntOrNull()
        require(chapterInFile == null || chapterInFile == document.chapter) {
            "This XHTML belongs to chapter $chapterInFile, but the open chapter is ${document.chapter}."
        }

        val entries = document.entries
        require(blocks.size == entries.size) {
            "XHTML has ${blocks.size} non-empty h1/h2/p blocks, but this chapter has ${entries.size} entries. " +
                "The structure must match before importing; nothing was changed."
        }
        entries.forEachIndexed { index, entry ->
            val expectedTag = when {
                entry.locator.startsWith("H1-", ignoreCase = true) -> "h1"
                entry.locator.startsWith("H2-", ignoreCase = true) -> "h2"
                entry.locator.startsWith("P", ignoreCase = true) -> "p"
                else -> throw IllegalArgumentException(
                    "Unsupported chapter locator ${entry.locator}; cannot align this XHTML safely.",
                )
            }
            require(blocks[index].first == expectedTag) {
                "XHTML block ${index + 1} is <${blocks[index].first}>, but ${entry.locator} " +
                    "requires <$expectedTag>. Nothing was imported."
            }
        }

        val translations = linkedMapOf<String, String>()
        entries.forEachIndexed { index, entry ->
            val text = blocks[index].second.trim()
            // The original, untranslated Japanese is not a useful English reference.
            if (normalizeForMatch(text) != normalizeForMatch(entry.sourceJapanese)) {
                translations[entry.locator] = text
            }
        }
        require(translations.isNotEmpty()) {
            "The XHTML contains no translated blocks; it appears to contain only the Japanese source."
        }
        return ImportedTranslationOverlay(
            chapterPath = chapterPath,
            sourceSha256 = document.sourceSha256,
            fileName = fileName,
            translations = translations,
            matchedCount = translations.size,
            totalEntries = entries.size,
            sourceParagraphs = 0,
            targetParagraphs = blocks.size,
            alignment = if (translations.size == entries.size) "XHTML structure verified" else "XHTML verified partial",
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

    private fun extractBlocksOrSegment(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        val blockTag = Regex("(?i)<(?:h1|h2|p)(?:\\s|>)")
        if (!blockTag.containsMatchIn(trimmed)) {
            return listOf(cleanSegmentText(trimmed)).filter { it.isNotBlank() }
        }

        val xml = sanitizeEmbeddedXml(trimmed)
        val parsed = runCatching { parseXml(xml) }
            .recoverCatching { parseXml("<root>$xml</root>") }
            .getOrNull()
        if (parsed != null) {
            return translatableBlockElements(parsed.documentElement)
                .map { blockText(it) }
                .filter { it.isNotBlank() }
        }

        // Fallback for HTML-ish targets that are not perfectly XML-well-formed.
        return Regex("(?is)<(h1|h2|p)\\b[^>]*>(.*?)</\\1>").findAll(trimmed)
            .map { match ->
                val tag = match.groupValues[1].lowercase()
                var inner = match.groupValues[2]
                if (tag == "h1") {
                    inner = inner.replace(
                        Regex("(?is)<(?:a|span)\\b[^>]*class\\s*=\\s*[\"'][^\"']*chapter-number[^\"']*[\"'][^>]*>.*?</(?:a|span)>"),
                        "",
                    )
                }
                cleanSegmentText(
                    inner
                        .replace(Regex("(?is)<br\\s*/?>"), "\n")
                        .replace(Regex("(?is)<[^>]+>"), ""),
                )
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun translatableBlockElements(root: Element): List<Element> {
        val out = mutableListOf<Element>()
        fun visit(node: Node) {
            if (node is Element) {
                val name = localName(node)
                if (name == "h1" || name == "h2" || name == "p") out += node
            }
            var child = node.firstChild
            while (child != null) {
                visit(child)
                child = child.nextSibling
            }
        }
        visit(root)
        return out
    }

    private fun blockText(element: Element): String {
        if (localName(element) != "h1") {
            // DOM textContent drops <br/> boundaries; keep them as spaces in imported reference text.
            val out = StringBuilder()
            fun collect(node: Node) {
                if (node is Element && localName(node) == "br") {
                    out.append(' ')
                    return
                }
                if (node.nodeType == Node.TEXT_NODE) out.append(node.nodeValue.orEmpty())
                var child = node.firstChild
                while (child != null) {
                    collect(child)
                    child = child.nextSibling
                }
            }
            collect(element)
            return cleanSegmentText(out.toString())
        }

        val badges = descendantsWithClass(element, "chapter-kind-badge")
        val titles = descendantsWithClass(element, "chapter-title-text")
        if (badges.isNotEmpty() || titles.isNotEmpty()) {
            return (badges + titles)
                .map { cleanSegmentText(it.textContent.orEmpty()) }
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

        val out = StringBuilder()
        fun collect(node: Node, skip: Boolean) {
            val skipHere = skip || (node is Element && hasClass(node, "chapter-number"))
            if (!skipHere && node.nodeType == Node.TEXT_NODE) out.append(node.nodeValue.orEmpty())
            var child = node.firstChild
            while (child != null) {
                collect(child, skipHere)
                child = child.nextSibling
            }
        }
        collect(element, false)
        return cleanSegmentText(out.toString())
    }

    private fun descendantsWithClass(root: Element, className: String): List<Element> =
        buildList {
            fun visit(node: Node) {
                if (node is Element && hasClass(node, className)) add(node)
                var child = node.firstChild
                while (child != null) {
                    visit(child)
                    child = child.nextSibling
                }
            }
            visit(root)
        }

    private fun hasClass(element: Element, className: String): Boolean =
        element.getAttribute("class")
            .split(Regex("\\s+"))
            .any { it == className }

    private fun localName(element: Element): String =
        (element.localName ?: element.nodeName.substringAfter(':')).lowercase()

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


internal fun readTranslationFile(input: java.io.InputStream, maxBytes: Int = 20 * 1024 * 1024): ByteArray {
    require(maxBytes >= 0)
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(count <= maxBytes - output.size()) { "Translation file is too large to import safely (20 MB limit)." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
