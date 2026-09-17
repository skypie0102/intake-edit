package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONObject

data class ApprovalDocument(
    val schemaVersion: Int,
    val volume: Int,
    val chapter: Int,
    val editorContentSha256: String,
    val readerContentSha256: String,
)

object ApprovalParser {
    fun parse(raw: String): ApprovalDocument {
        val root = JSONObject(raw)
        return ApprovalDocument(
            schemaVersion = root.optInt("schema_version", -1),
            volume = root.optInt("volume", -1),
            chapter = root.optInt("chapter", -1),
            editorContentSha256 = root.optString("editor_content_sha256"),
            readerContentSha256 = root.optString("reader_content_sha256"),
        )
    }

    fun path(volume: Int, chapter: Int): String =
        "approvals/vol-${volume.toString().padStart(2, '0')}/ch_${chapter.toString().padStart(4, '0')}.approved.json"
}
