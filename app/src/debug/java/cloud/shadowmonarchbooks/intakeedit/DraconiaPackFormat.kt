package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONObject

internal data class PreviewClip(val id: String, val label: String, val framesPerSecond: Double, val frames: List<String>)
internal data class PreviewPack(val revision: String, val description: String, val clips: List<PreviewClip>)

/** Parse pack metadata and keep frames encoded until the player needs them. */
internal fun parsePreviewPack(text: String, validateFrame: (String) -> Unit): PreviewPack {
    val root = JSONObject(text)
    require(root.getInt("format") == 1) { "Unsupported pack format" }
    val revision = root.getString("revision").take(100)
    val description = root.getString("description").take(500)
    val items = root.getJSONArray("clips")
    require(items.length() > 0) { "No clips in pack" }
    val ids = mutableSetOf<String>()
    val clips = (0 until items.length()).map { n ->
        val item = items.getJSONObject(n)
        val id = item.getString("id")
        require(id.matches(Regex("[a-z0-9_-]{1,40}")) && ids.add(id)) { "Invalid or duplicate clip ID" }
        val label = item.getString("label")
        require(label.length in 1..80) { "Invalid clip label" }
        val ms = item.getLong("frame_ms")
        require(ms > 0) { "Invalid frame duration" }
        val fps = if (item.has("fps")) item.getDouble("fps") else 1000.0 / ms
        require(fps.isFinite() && fps > 0) { "Invalid frame rate" }
        val encoded = item.getJSONArray("frames")
        require(encoded.length() > 0) { "Clip has no frames" }
        val frames = (0 until encoded.length()).map { i ->
            encoded.getString(i).also(validateFrame)
        }
        PreviewClip(id, label, fps, frames)
    }
    return PreviewPack(revision, description, clips)
}
