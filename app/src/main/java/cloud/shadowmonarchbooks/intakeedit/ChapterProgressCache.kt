package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class ChapterListCacheSnapshot(
    val files: List<ChapterFile>,
    val progressByPath: Map<String, ChapterProgress>,
)

internal interface ChapterProgressCacheStore {
    fun load(settings: RepoSettings): ChapterListCacheSnapshot?
    fun save(settings: RepoSettings, snapshot: ChapterListCacheSnapshot)
}

internal object NoOpChapterProgressCacheStore : ChapterProgressCacheStore {
    override fun load(settings: RepoSettings): ChapterListCacheSnapshot? = null
    override fun save(settings: RepoSettings, snapshot: ChapterListCacheSnapshot) = Unit
}

internal class SharedPreferencesChapterProgressCacheStore(context: Context) : ChapterProgressCacheStore {
    private val prefs = context.getSharedPreferences("chapter_progress_cache", Context.MODE_PRIVATE)

    override fun load(settings: RepoSettings): ChapterListCacheSnapshot? = runCatching {
        val raw = prefs.getString(cacheKey(settings), null) ?: return null
        val root = JSONObject(raw)
        if (root.optInt("schema_version") != 1) return null

        val filesArray = root.optJSONArray("files") ?: JSONArray()
        val files = buildList {
            for (index in 0 until filesArray.length()) {
                val item = filesArray.getJSONObject(index)
                add(
                    ChapterFile(
                        path = item.getString("path"),
                        volume = item.getInt("volume"),
                        chapter = item.getInt("chapter"),
                    ),
                )
            }
        }

        val progressObject = root.optJSONObject("progress") ?: JSONObject()
        val progress = buildMap {
            val keys = progressObject.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val item = progressObject.getJSONObject(path)
                put(
                    path,
                    ChapterProgress(
                        englishSupplied = item.getInt("english_supplied"),
                        englishTotal = item.getInt("english_total"),
                        editorReviewComplete = item.optBoolean("editor_review_complete", false),
                        approved = item.optBoolean("approved", false),
                        qaActive = item.optInt("qa_active", 0),
                        qaTotal = item.optInt("qa_total", 0),
                        qaReusable = item.optBoolean("qa_reusable", false),
                    ),
                )
            }
        }
        ChapterListCacheSnapshot(files, progress)
    }.getOrNull()

    override fun save(settings: RepoSettings, snapshot: ChapterListCacheSnapshot) {
        val files = JSONArray()
        snapshot.files.forEach { file ->
            files.put(
                JSONObject()
                    .put("path", file.path)
                    .put("volume", file.volume)
                    .put("chapter", file.chapter),
            )
        }

        val progress = JSONObject()
        snapshot.progressByPath.forEach { (path, value) ->
            progress.put(
                path,
                JSONObject()
                    .put("english_supplied", value.englishSupplied)
                    .put("english_total", value.englishTotal)
                    .put("editor_review_complete", value.editorReviewComplete)
                    .put("approved", value.approved)
                    .put("qa_active", value.qaActive)
                    .put("qa_total", value.qaTotal)
                    .put("qa_reusable", value.qaReusable),
            )
        }

        val raw = JSONObject()
            .put("schema_version", 1)
            .put("files", files)
            .put("progress", progress)
            .toString()
        prefs.edit().putString(cacheKey(settings), raw).apply()
    }

    private fun cacheKey(settings: RepoSettings): String = buildString {
        append(settings.owner.trim())
        append('/')
        append(settings.repo.trim())
        append('@')
        append(settings.branch.trim())
        append(':')
        append(settings.intakeRoot.trim().trim('/'))
    }
}
