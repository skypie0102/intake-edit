package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class QaPass(
    val completedAt: String,
    val editorContentSha256: String,
    val protectedContentSha256: String,
    val mutableLocators: List<String>,
    val findingIds: List<String>,
)

data class QaResolution(
    val resolvedAt: String,
    val resolvedBy: String,
    val contentSha256: String,
)

data class QaOverride(
    val reason: String,
    val overriddenAt: String,
    val overriddenBy: String,
    val editorContentSha256: String,
    val contentSha256: String? = null,
)

data class QaFinding(
    val id: String,
    val locator: String,
    val category: String,
    val severity: String,
    val message: String,
    val overridable: Boolean,
    val baselineContentSha256: String? = null,
    val resolution: QaResolution? = null,
    val override: QaOverride? = null,
) {
    val canOverride: Boolean get() = severity != "blocking"
}

enum class QaFindingDisposition { ACTIVE, RESOLVED, OVERRIDDEN }

data class QaFindingsDocument(
    val schemaVersion: Int,
    val volume: Int,
    val chapter: Int,
    val qaPass: QaPass?,
    val findings: List<QaFinding>,
) {
    fun qaPassReusable(editor: EditorDocument): Boolean {
        val pass = qaPass ?: return false
        val currentIds = findings.map { it.id }.sorted()
        if (currentIds != pass.findingIds.sorted()) return false
        val mutableLocators = if (findings.any { it.locator.isBlank() }) {
            editor.entries.map { it.locator }.sorted()
        } else {
            findings.map { it.locator }.filter { it.isNotBlank() }.distinct().sorted()
        }
        if (mutableLocators != pass.mutableLocators.sorted()) return false
        return QaFindingsParser.protectedContentSha256(editor, mutableLocators.toSet()) == pass.protectedContentSha256
    }

    fun disposition(finding: QaFinding, editor: EditorDocument, editorContentSha256: String): QaFindingDisposition {
        val currentScope = QaFindingsParser.findingContentSha256(editor, finding.locator)
        if (finding.resolution?.contentSha256 == currentScope) return QaFindingDisposition.RESOLVED
        val override = finding.override
        if (override != null) {
            if (override.contentSha256 != null && override.contentSha256 == currentScope) return QaFindingDisposition.OVERRIDDEN
            if (override.contentSha256 == null && override.editorContentSha256 == editorContentSha256) return QaFindingDisposition.OVERRIDDEN
        }
        return QaFindingDisposition.ACTIVE
    }

    fun active(editor: EditorDocument, editorContentSha256: String) =
        findings.filter { disposition(it, editor, editorContentSha256) == QaFindingDisposition.ACTIVE }

    fun activeBlocking(editor: EditorDocument, editorContentSha256: String) =
        active(editor, editorContentSha256).filter { it.severity == "error" || it.severity == "blocking" }

    fun canResolve(finding: QaFinding, editor: EditorDocument): Boolean {
        if (!qaPassReusable(editor)) return false
        val baseline = finding.baselineContentSha256 ?: return false
        return QaFindingsParser.findingContentSha256(editor, finding.locator) != baseline
    }

    fun withOverride(findingId: String, override: QaOverride) = copy(
        findings = findings.map {
            if (it.id == findingId) it.copy(resolution = null, override = override) else it
        },
    )

    fun withResolution(findingId: String, resolution: QaResolution) = copy(
        findings = findings.map {
            if (it.id == findingId) it.copy(resolution = resolution, override = null) else it
        },
    )
}

data class QaFindingsSnapshot(val path: String, val sha: String, val raw: String, val document: QaFindingsDocument)

object QaFindingsParser {
    private val yamlTagRegex = Regex("(?m)^(\\s*editor_review_complete\\s*:\\s*)(true|false)([ \\t]*)$")
    private val jsonTagRegex = Regex("(\\\"editor_review_complete\\\"\\s*:\\s*)(true|false)")

    fun parse(raw: String): QaFindingsDocument {
        val root = JSONObject(raw)
        require(root.optInt("schema_version", -1) == 1) { "Unsupported QA findings schema." }
        val qaPass = root.optJSONObject("qa_pass")?.let { pass ->
            QaPass(
                completedAt = pass.getString("completed_at"),
                editorContentSha256 = pass.getString("editor_content_sha256"),
                protectedContentSha256 = pass.getString("protected_content_sha256"),
                mutableLocators = pass.getJSONArray("mutable_locators").toStringList(),
                findingIds = pass.getJSONArray("finding_ids").toStringList(),
            )
        }
        val array = root.optJSONArray("findings") ?: JSONArray()
        val findings = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val severity = item.getString("severity")
                require(severity in setOf("warning", "error", "blocking")) { "Unsupported QA severity: $severity" }
                val resolution = item.optJSONObject("resolution")?.let {
                    QaResolution(
                        resolvedAt = it.getString("resolved_at"),
                        resolvedBy = it.getString("resolved_by"),
                        contentSha256 = it.getString("content_sha256"),
                    )
                }
                val override = item.optJSONObject("override")?.let {
                    QaOverride(
                        reason = it.getString("reason"),
                        overriddenAt = it.getString("overridden_at"),
                        overriddenBy = it.getString("overridden_by"),
                        editorContentSha256 = it.getString("editor_content_sha256"),
                        contentSha256 = it.optString("content_sha256").takeIf(String::isNotBlank),
                    )
                }
                add(
                    QaFinding(
                        id = item.getString("id"),
                        locator = item.optString("locator").takeUnless { it.isBlank() }.orEmpty(),
                        category = item.getString("category"),
                        severity = severity,
                        message = item.getString("message"),
                        overridable = severity != "blocking",
                        baselineContentSha256 = item.optString("baseline_content_sha256").takeIf(String::isNotBlank),
                        resolution = resolution,
                        override = override,
                    ),
                )
            }
        }
        return QaFindingsDocument(
            schemaVersion = root.getInt("schema_version"),
            volume = root.optInt("volume", -1),
            chapter = root.optInt("chapter", -1),
            qaPass = qaPass,
            findings = findings,
        )
    }

    fun serialize(document: QaFindingsDocument): String {
        val root = JSONObject().put("schema_version", document.schemaVersion)
        if (document.volume >= 0) root.put("volume", document.volume)
        if (document.chapter >= 0) root.put("chapter", document.chapter)
        document.qaPass?.let { pass ->
            root.put(
                "qa_pass",
                JSONObject()
                    .put("completed_at", pass.completedAt)
                    .put("editor_content_sha256", pass.editorContentSha256)
                    .put("protected_content_sha256", pass.protectedContentSha256)
                    .put("mutable_locators", JSONArray(pass.mutableLocators))
                    .put("finding_ids", JSONArray(pass.findingIds)),
            )
        } ?: root.put("qa_pass", JSONObject.NULL)

        val findings = JSONArray()
        document.findings.forEach { finding ->
            val obj = JSONObject()
                .put("id", finding.id)
                .put("locator", finding.locator)
                .put("category", finding.category)
                .put("severity", finding.severity)
                .put("message", finding.message)
                .put("overridable", finding.canOverride)
            finding.baselineContentSha256?.let { obj.put("baseline_content_sha256", it) }
            finding.resolution?.let { resolution ->
                obj.put(
                    "resolution",
                    JSONObject()
                        .put("resolved_at", resolution.resolvedAt)
                        .put("resolved_by", resolution.resolvedBy)
                        .put("content_sha256", resolution.contentSha256),
                )
            }
            finding.override?.let { override ->
                val value = JSONObject()
                    .put("reason", override.reason)
                    .put("overridden_at", override.overriddenAt)
                    .put("overridden_by", override.overriddenBy)
                    .put("editor_content_sha256", override.editorContentSha256)
                override.contentSha256?.let { value.put("content_sha256", it) }
                obj.put("override", value)
            }
            findings.put(obj)
        }
        return root.put("findings", findings).toString(2) + "\n"
    }

    fun path(volume: Int, chapter: Int) = "qa/vol-${volume.toString().padStart(2, '0')}/ch_${chapter.toString().padStart(4, '0')}.findings.json"

    fun editorContentSha256(raw: String): String {
        val yaml = yamlTagRegex.find(raw)
        val normalized = when {
            yaml != null -> raw.replaceRange(yaml.range, yaml.groupValues[1] + "false" + yaml.groupValues[3])
            else -> {
                val json = jsonTagRegex.find(raw) ?: error("Could not normalize editor_review_complete for QA content hash.")
                raw.replaceRange(json.range, json.groupValues[1] + "false")
            }
        }
        return sha256(normalized)
    }

    fun findingContentSha256(document: EditorDocument, locator: String): String {
        if (locator.isBlank()) return canonicalDocumentSha256(document, emptySet())
        val entry = document.entries.firstOrNull { it.locator == locator }
            ?: error("Unknown QA paragraph locator: $locator")
        val notes = document.endnotes
            .filter { it.locator == locator }
            .sortedWith(compareBy<EndnoteDefinition> { it.id }.thenBy { it.content })
            .map(::endnoteMap)
        return canonicalSha256(
            mapOf(
                "entry" to entryMap(entry),
                "endnotes" to notes,
            ),
        )
    }

    fun protectedContentSha256(document: EditorDocument, mutableLocators: Set<String>): String =
        canonicalDocumentSha256(document, mutableLocators)

    private fun canonicalDocumentSha256(document: EditorDocument, mutableLocators: Set<String>): String {
        val notes = document.endnotes
            .filter { it.locator !in mutableLocators }
            .sortedWith(compareBy<EndnoteDefinition> { it.locator }.thenBy { it.id }.thenBy { it.content })
            .map { endnoteMap(it) as Any }
            .toMutableList()
        mutableLocators.sorted().forEach { locator ->
            notes += mapOf("id" to "<QA_EDITABLE>", "locator" to locator, "content" to "<QA_EDITABLE>")
        }
        val entries = document.entries.map { entry ->
            mapOf(
                "locator" to entry.locator,
                "source_japanese" to entry.sourceJapanese,
                "english" to if (entry.locator in mutableLocators) "<QA_EDITABLE>" else entry.english,
            )
        }
        return canonicalSha256(
            mapOf(
                "schema_version" to document.schemaVersion,
                "volume" to document.volume,
                "chapter" to document.chapter,
                "source_href" to document.sourceHref,
                "source_sha256" to document.sourceSha256,
                "reader_file" to document.readerFile,
                "english_title" to document.englishTitle,
                "instructions" to document.instructions,
                "editor_review_complete" to false,
                "endnotes" to notes,
                "entries" to entries,
            ),
        )
    }

    private fun entryMap(entry: EditorEntry): Map<String, Any> = mapOf(
        "locator" to entry.locator,
        "source_japanese" to entry.sourceJapanese,
        "english" to entry.english,
    )

    private fun endnoteMap(note: EndnoteDefinition): Map<String, Any> = mapOf(
        "id" to note.id,
        "locator" to note.locator,
        "content" to note.content,
    )

    private fun canonicalSha256(value: Any?): String = sha256(canonicalJson(value))

    private fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> pythonJsonString(value)
        is Boolean, is Number -> value.toString()
        is Map<*, *> -> value.entries
            .map { (key, item) -> require(key is String); key to item }
            .sortedBy { it.first }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, item) ->
                pythonJsonString(key) + ":" + canonicalJson(item)
            }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",") { canonicalJson(it) }
        else -> error("Unsupported canonical QA value: ${value::class.java.name}")
    }

    /**
     * Match Python json.dumps(..., ensure_ascii=False, separators=(",", ":")).
     *
     * org.json's JSONObject.quote() additionally escapes several Unicode punctuation
     * ranges. Those extra escapes change the UTF-8 bytes and therefore the SHA-256
     * used by the authoritative Python QA workflow.
     */
    private fun pythonJsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
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
        append('"')
    }

    fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }
}
