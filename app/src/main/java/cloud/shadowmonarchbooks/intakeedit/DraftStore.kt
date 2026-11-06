package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class LocalDraft(
    val path: String,
    val baseSha: String,
    val raw: String,
    val savedAtMillis: Long,
    val legacyUnassigned: Boolean = false,
)

class DraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_intake_drafts", Context.MODE_PRIVATE)
    private val draftDir = File(context.filesDir, "intake_drafts").also { it.mkdirs() }
    private fun oldKey(path: String) = "draft_" + Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    private fun scopedFile(settings: RepoSettings, path: String) = File(draftDir, "draft_v2_${draftScopeKey(settings, path)}.json")
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun scopedRecoveryPrefix(settings: RepoSettings, path: String) = "recovery_v2_${draftScopeKey(settings, path)}_"
    private fun legacyRecoveryPrefix(path: String) = "recovery_${sha(path)}_"

    private fun write(target: File, draft: LocalDraft): Boolean = runCatching {
        val payload = JSONObject().put("path", draft.path).put("base_sha", draft.baseSha)
            .put("raw", draft.raw).put("saved_at_ms", draft.savedAtMillis).toString()
        val atomic = AtomicFile(target)
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

    fun save(settings: RepoSettings, path: String, baseSha: String, raw: String): Boolean =
        write(scopedFile(settings, path), LocalDraft(path, baseSha, raw, System.currentTimeMillis()))

    private fun read(target: File, path: String): LocalDraft? = if (!target.exists()) null else runCatching {
        parseDraft(String(AtomicFile(target).readFully(), Charsets.UTF_8), path)
    }.getOrNull()

    fun load(settings: RepoSettings, path: String): LocalDraft? {
        val target = scopedFile(settings, path)
        if (!target.exists()) return null
        return requireNotNull(read(target, path)) { "The saved draft could not be read. It has been kept on this device." }
    }

    fun archive(settings: RepoSettings, draft: LocalDraft): Boolean {
        val target = File(draftDir, scopedRecoveryPrefix(settings, draft.path) + sha(draft.baseSha + "\u0000" + draft.raw) + ".json")
        val existing = read(target, draft.path)
        if (existing != null && existing.path == draft.path && existing.baseSha == draft.baseSha && existing.raw == draft.raw) return true
        return write(target, draft)
    }

    /** Older drafts have no repository identity; expose them only for explicit recovery. */
    fun archived(settings: RepoSettings, path: String): List<LocalDraft> {
        val files = draftDir.listFiles().orEmpty()
        fun matching(prefix: String, unassigned: Boolean) = files.asSequence()
            .filter { it.name.startsWith(prefix) && it.name.endsWith(".json") }
            .mapNotNull { read(it, path)?.copy(legacyUnassigned = unassigned) }
            .toList()
        val oldFile = read(File(draftDir, oldKey(path) + ".json"), path)
        val oldPreference = prefs.getString(oldKey(path), null)?.let { raw -> runCatching { parseDraft(raw, path) }.getOrNull() }
        return (matching(scopedRecoveryPrefix(settings, path), false) + matching(legacyRecoveryPrefix(path), true) +
            listOfNotNull(oldFile, oldPreference).map { it.copy(legacyUnassigned = true) })
            .distinctBy { Triple(it.baseSha, it.raw, it.legacyUnassigned) }
            .sortedByDescending { it.savedAtMillis }
    }

    private fun parseDraft(text: String, fallbackPath: String): LocalDraft {
        val obj = JSONObject(text)
        return LocalDraft(obj.optString("path", fallbackPath), obj.optString("base_sha"), obj.optString("raw"), obj.optLong("saved_at_ms"))
    }

    fun delete(settings: RepoSettings, path: String): Boolean {
        val target = scopedFile(settings, path)
        AtomicFile(target).delete()
        return !target.exists()
    }
}
