package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File

data class LocalDraft(val path: String, val baseSha: String, val raw: String, val savedAtMillis: Long)

class DraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_intake_drafts", Context.MODE_PRIVATE)
    private val draftDir = File(context.filesDir, "intake_drafts").also { it.mkdirs() }
    private fun key(path: String) = "draft_" + Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    private fun file(path: String) = File(draftDir, key(path) + ".json")

    fun save(path: String, baseSha: String, raw: String): Boolean = runCatching {
        val payload = JSONObject().put("path", path).put("base_sha", baseSha).put("raw", raw).put("saved_at_ms", System.currentTimeMillis()).toString()
        val atomic = AtomicFile(file(path)); var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite(); stream.write(payload.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream)
        } catch (t: Throwable) { stream?.let { atomic.failWrite(it) }; throw t }
        prefs.edit().remove(key(path)).commit()
    }.getOrDefault(false)

    fun load(path: String): LocalDraft? {
        val target = file(path)
        val modern = if (target.exists()) runCatching { parseDraft(String(AtomicFile(target).readFully(), Charsets.UTF_8), path) }.getOrNull() else null
        if (modern != null) return modern
        val legacy = prefs.getString(key(path), null) ?: return null
        val parsed = runCatching { parseDraft(legacy, path) }.getOrNull() ?: return null
        save(parsed.path, parsed.baseSha, parsed.raw)
        return parsed
    }

    private fun parseDraft(text: String, fallbackPath: String): LocalDraft {
        val obj = JSONObject(text)
        return LocalDraft(obj.optString("path", fallbackPath), obj.optString("base_sha"), obj.optString("raw"), obj.optLong("saved_at_ms"))
    }

    fun delete(path: String): Boolean {
        AtomicFile(file(path)).delete(); prefs.edit().remove(key(path)).commit(); return !file(path).exists()
    }
}
