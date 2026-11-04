package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal data class TranslationLanguages(val source: String = "auto", val target: String = "en") {
    val normalizedSource: String get() = source.trim().lowercase()
    val normalizedTarget: String get() = target.trim().lowercase()
    fun validate() {
        val pattern = Regex("[a-z]{2,3}(?:-[a-z0-9]{2,8})*")
        require(normalizedSource == "auto" || pattern.matches(normalizedSource)) { "Use a source language code such as ja, or auto." }
        require(pattern.matches(normalizedTarget)) { "Use a target language code such as en." }
    }
}

internal class OnlineTranslationSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("online_translation", Context.MODE_PRIVATE)
    private fun visibilityKey(cacheKey: String, path: String, sourceHash: String): String {
        val text = listOf(cacheKey, path, sourceHash).joinToString("\u0000")
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    fun visible(cacheKey: String, path: String, sourceHash: String): Boolean =
        prefs.getBoolean(visibilityKey(cacheKey, path, sourceHash), true)
    fun setVisible(cacheKey: String, path: String, sourceHash: String, visible: Boolean) {
        prefs.edit().putBoolean(visibilityKey(cacheKey, path, sourceHash), visible).apply()
    }
}

internal fun machineCacheKey(settings: RepoSettings, languages: TranslationLanguages): String =
    listOf(settings.owner.trim().lowercase(), settings.repo.trim().lowercase(), settings.branch.trim(),
        settings.intakeRoot.trim('/'), languages.normalizedSource, languages.normalizedTarget).joinToString("|")

/** Produce the entire reference in memory; callers persist it only after every batch succeeds. */
internal suspend fun translateChapterReference(
    chapterPath: String,
    document: EditorDocument,
    translate: suspend (String) -> String,
    onProgress: (Int, Int) -> Unit = { _, _ -> },
): ImportedTranslationOverlay {
    val results = linkedMapOf<String, String>()
    for ((index, entry) in document.entries.withIndex()) {
        if (entry.sourceJapanese.isNotBlank()) {
            val text = translate(entry.sourceJapanese)
            require(text.isNotBlank()) { "No translation returned for ${entry.locator}." }
            results[entry.locator] = text
        }
        onProgress(index + 1, document.entries.size)
    }
    return translationOverlay(chapterPath, document, results)
}

internal fun translationOverlay(path: String, document: EditorDocument, results: Map<String, String>) = ImportedTranslationOverlay(
    chapterPath = path, sourceSha256 = document.sourceSha256,
    fileName = "Machine Translation (Google Translate online)", translations = results,
    matchedCount = results.size, totalEntries = document.entries.size,
    sourceParagraphs = document.entries.size, targetParagraphs = results.size, alignment = "Per entry",
)

internal enum class MachineToggle { HIDE, RESTORE, TRANSLATE }
internal fun nextMachineAction(selected: Boolean, cached: Boolean): MachineToggle = when {
    selected -> MachineToggle.HIDE
    cached -> MachineToggle.RESTORE
    else -> MachineToggle.TRANSLATE
}

internal fun parseGoogleTranslation(response: String): String {
    val chunks = JSONArray(response).getJSONArray(0)
    return buildString {
        for (i in 0 until chunks.length()) {
            val chunk = chunks.optJSONArray(i) ?: continue
            if (!chunk.isNull(0)) append(chunk.optString(0))
        }
    }
}

internal fun splitForGoogle(text: String, limit: Int = 3500): List<String> {
    require(limit > 0)
    val parts = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val remaining = text.codePointCount(start, text.length)
        if (remaining <= limit) { parts += text.substring(start); break }
        val maximum = text.offsetByCodePoints(start, limit)
        val candidate = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t', '。', '！', '？', '.', '!', '?'), maximum - 1)
        val end = if (candidate > start + (maximum - start) / 2) candidate + 1 else maximum
        parts += text.substring(start, end)
        start = end
    }
    return parts
}

/** Requests one entry at a time so source-to-result alignment is never inferred. */
internal suspend fun translateWithGoogle(
    chapterPath: String, document: EditorDocument, languages: TranslationLanguages,
    onProgress: (Int, Int) -> Unit,
): ImportedTranslationOverlay = withContext(Dispatchers.IO) {
    languages.validate()
    val results = linkedMapOf<String, String>()
    val entries = document.entries.filter { it.sourceJapanese.isNotBlank() }
    for ((index, entry) in entries.withIndex()) {
        coroutineContext.ensureActive()
        val pieces = splitForGoogle(entry.sourceJapanese)
        val translatedEntry = StringBuilder()
        for ((partIndex, piece) in pieces.withIndex()) {
            coroutineContext.ensureActive()
            val prefix = piece.takeWhile { it.isWhitespace() }
            val suffix = piece.takeLastWhile { it.isWhitespace() }
            val source = piece.trim()
            if (source.isEmpty()) { translatedEntry.append(piece); continue }
            val body = listOf("client" to "gtx", "sl" to languages.normalizedSource,
                "tl" to languages.normalizedTarget, "dt" to "t", "q" to source)
                .joinToString("&") { (key, value) ->
                    URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
                }.toByteArray(Charsets.UTF_8)
            val connection = URL("https://translate.google.co.in/translate_a/single").openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                if (status !in 200..299) error("Google Translate returned HTTP $status. Check your connection or try again later.")
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val translated = parseGoogleTranslation(response)
                require(translated.isNotBlank()) { "Google returned no translation for ${entry.locator}." }
                translatedEntry.append(prefix).append(translated).append(suffix)
                if (partIndex < pieces.lastIndex && suffix.isEmpty() && pieces[partIndex + 1].firstOrNull()?.isWhitespace() == false) translatedEntry.append(' ')
            } finally { connection.disconnect() }
            if (partIndex < pieces.lastIndex || index != entries.lastIndex) delay(300)
        }
        results[entry.locator] = translatedEntry.toString()
        withContext(Dispatchers.Main) { onProgress(index + 1, entries.size) }
    }
    translationOverlay(chapterPath, document, results)
}
