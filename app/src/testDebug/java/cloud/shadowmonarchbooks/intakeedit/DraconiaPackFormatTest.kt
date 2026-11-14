package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DraconiaPackFormatTest {
    private fun clip(id: String, frames: Int, duration: Int = 42) = JSONObject()
        .put("id", id).put("label", id).put("frame_ms", duration)
        .put("frames", JSONArray().apply { repeat(frames) { put("frame-$it") } })

    private fun pack(clips: JSONArray) = JSONObject().put("format", 1)
        .put("revision", "test").put("description", "test").put("clips", clips).toString()

    @Test fun acceptsFullDanceAndMoreThanOldPackLimits() {
        val dance = clip("dance", 192).put("fps", 24)
        val clips = JSONArray().put(dance).apply { repeat(12) { put(clip("other_$it", 5)) } }
        var validated = 0
        val parsed = parsePreviewPack(pack(clips)) { validated++ }
        assertEquals(13, parsed.clips.size)
        assertEquals(192, parsed.clips.first().frames.size)
        assertEquals("frame-191", parsed.clips.first().frames.last())
        assertEquals(24.0, parsed.clips.first().framesPerSecond, 0.0)
        assertEquals(252, validated)
    }

    @Test fun preservesLegacyTimingAndRejectsMalformedClips() {
        val legacy = parsePreviewPack(pack(JSONArray().put(clip("legacy", 1, 125)))) { }
        assertEquals(8.0, legacy.clips.first().framesPerSecond, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            parsePreviewPack(pack(JSONArray().put(clip("empty", 0)))) { }
        }
        assertThrows(IllegalArgumentException::class.java) {
            parsePreviewPack(pack(JSONArray().put(clip("same", 1)).put(clip("same", 1)))) { }
        }
    }
}
