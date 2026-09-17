package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class QaOverride(val reason: String, val overriddenAt: String, val overriddenBy: String, val editorContentSha256: String)
data class QaFinding(val id: String, val locator: String, val category: String, val severity: String, val message: String, val overridable: Boolean, val override: QaOverride?)
data class QaFindingsDocument(val schemaVersion: Int, val volume: Int, val chapter: Int, val findings: List<QaFinding>) {
    fun active(editorContentSha256: String) = findings.filter { it.override?.editorContentSha256 != editorContentSha256 }
    fun overridden(editorContentSha256: String) = findings.filter { it.override?.editorContentSha256 == editorContentSha256 }
    fun activeBlocking(editorContentSha256: String) = active(editorContentSha256).filter { it.severity == "error" || it.severity == "blocking" }
    fun withOverride(findingId: String, override: QaOverride) = copy(findings = findings.map { if (it.id == findingId) it.copy(override = override) else it })
}
data class QaFindingsSnapshot(val path: String, val sha: String, val raw: String, val document: QaFindingsDocument)

object QaFindingsParser {
    private val yamlTagRegex = Regex("(?m)^(\\s*editor_review_complete\\s*:\\s*)(true|false)([ \\t]*)$")
    private val jsonTagRegex = Regex("(\\\"editor_review_complete\\\"\\s*:\\s*)(true|false)")

    fun parse(raw: String): QaFindingsDocument {
        val root = JSONObject(raw); require(root.optInt("schema_version", -1) == 1) { "Unsupported QA findings schema." }
        val array = root.optJSONArray("findings") ?: JSONArray()
        val findings = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val severity = item.getString("severity")
                require(severity in setOf("warning", "error", "blocking")) { "Unsupported QA severity: $severity" }
                val override = item.optJSONObject("override")?.let { QaOverride(it.getString("reason"), it.getString("overridden_at"), it.getString("overridden_by"), it.getString("editor_content_sha256")) }
                add(QaFinding(item.getString("id"), item.optString("locator").takeUnless { it.isBlank() }.orEmpty(), item.getString("category"), severity, item.getString("message"), item.optBoolean("overridable", false), override))
            }
        }
        return QaFindingsDocument(root.getInt("schema_version"), root.optInt("volume", -1), root.optInt("chapter", -1), findings)
    }

    fun serialize(document: QaFindingsDocument): String {
        val root = JSONObject().put("schema_version", document.schemaVersion)
        if (document.volume >= 0) root.put("volume", document.volume)
        if (document.chapter >= 0) root.put("chapter", document.chapter)
        val findings = JSONArray()
        document.findings.forEach { f ->
            val obj = JSONObject().put("id", f.id).put("locator", f.locator).put("category", f.category).put("severity", f.severity).put("message", f.message).put("overridable", f.overridable)
            val o = f.override
            if (o != null) obj.put("override", JSONObject().put("reason", o.reason).put("overridden_at", o.overriddenAt).put("overridden_by", o.overriddenBy).put("editor_content_sha256", o.editorContentSha256)) else obj.put("override", JSONObject.NULL)
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

    fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
