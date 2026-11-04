package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

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

/** Retain completed entries after an interruption; publish a reference only after all succeed. */
internal suspend fun translateChapterReference(
    chapterPath: String,
    document: EditorDocument,
    translate: suspend (String) -> String,
    alreadyTranslated: Map<String, String> = emptyMap(),
    onCheckpoint: suspend (Map<String, String>) -> Unit = {},
    onProgress: suspend (Int, Int) -> Unit = { _, _ -> },
): ImportedTranslationOverlay {
    val results = linkedMapOf<String, String>()
    val entries = document.entries.filter { it.sourceJapanese.isNotBlank() }
    for ((index, entry) in entries.withIndex()) {
        val saved = alreadyTranslated[entry.locator]?.takeIf { it.isNotBlank() }
        val text = saved ?: translate(entry.sourceJapanese)
        require(text.isNotBlank()) { "No translation returned for ${entry.locator}." }
        results[entry.locator] = text
        if (saved == null) onCheckpoint(results.toMap())
        onProgress(index + 1, entries.size)
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

internal fun splitForGoogle(text: String, limit: Int = 400): List<String> {
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

/** Honor the server's Retry-After and never retry sooner than our backoff. */
internal fun googleRetryDelayMillis(retryAfter: String?, attempt: Int, nowMillis: Long): Long? {
    val exponential = 3_000L shl attempt.coerceIn(0, 2)
    val serverMillis = retryAfter?.trim()?.let { value ->
        value.toLongOrNull()?.coerceAtLeast(0)?.coerceAtMost(86_400)?.times(1000)
            ?: runCatching { (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis).coerceAtLeast(0) }.getOrNull()
    } ?: 0L
    val wait = maxOf(exponential, serverMillis)
    return wait.takeIf { it <= 30_000L }
}

internal class GoogleRateLimit(val retryAfter: String?) : Exception()

internal data class GoogleRoute(val endpoint: String, val method: String, val mobile: Boolean = false)

internal val googleRoutes = listOf(
    GoogleRoute("https://translate.google.co.in/translate_a/single", "POST"),
    GoogleRoute("https://translate.google.com/translate_a/single", "POST"),
    GoogleRoute("https://clients5.google.com/translate_a/t", "POST", mobile = true),
    GoogleRoute("https://translate.googleapis.com/translate_a/single", "GET"),
)

internal fun parseGoogleMobileTranslation(response: String): String {
    val first = JSONArray(response).get(0)
    return when (first) {
        is String -> first
        is JSONArray -> first.getString(0)
        else -> error("Google returned an unsupported translation response.")
    }
}

private fun requestGoogleText(source: String, languages: TranslationLanguages, route: GoogleRoute): String {
    val params = mutableListOf("client" to if (route.mobile) "t" else "gtx", "sl" to languages.normalizedSource,
        "tl" to languages.normalizedTarget)
    if (!route.mobile) params += "dt" to "t"
    params += "q" to source
    val query = params
        .joinToString("&") { (key, value) -> URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8") }
    val url = if (route.method == "GET") "${route.endpoint}?$query" else route.endpoint
    val connection = URL(url).openConnection() as HttpsURLConnection
    try {
        connection.requestMethod = route.method
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.setRequestProperty("Referer", "https://translate.google.com/")
        connection.setRequestProperty("Origin", "https://translate.google.com")
        if (route.method == "POST") {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            connection.outputStream.use { it.write(query.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        if (status == 429) throw GoogleRateLimit(connection.getHeaderField("Retry-After"))
        if (status !in 200..299) error("Google Translate returned HTTP $status. Check your connection or try again later.")
        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return (if (route.mobile) parseGoogleMobileTranslation(response) else parseGoogleTranslation(response))
            .also { require(it.isNotBlank()) { "Google returned an empty translation." } }
    } finally { connection.disconnect() }
}

/** Try Google routes only; respect server cooldowns and never substitute another provider. */
internal suspend fun translateUsingGoogleRoutes(
    routes: List<GoogleRoute> = googleRoutes,
    onRetry: suspend (Long) -> Unit = {},
    pause: suspend (Long) -> Unit = { delay(it) },
    request: suspend (GoogleRoute) -> String,
): String {
    require(routes.isNotEmpty())
    var limited = false
    for ((index, route) in routes.withIndex()) {
        coroutineContext.ensureActive()
        try { return request(route) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (rate: GoogleRateLimit) {
            limited = true
            if (index == routes.lastIndex) break
            val wait = googleRetryDelayMillis(rate.retryAfter, 0, System.currentTimeMillis())
                ?: error("Google Translate asked the app to wait before retrying (HTTP 429). Completed entries are saved.")
            onRetry(wait)
            pause(wait)
        } catch (_: Exception) {
            if (index != routes.lastIndex) pause(1000)
        }
    }
    if (limited) error("Google Translate could not complete this batch; a Google endpoint returned HTTP 429. Completed entries are saved.")
    error("Google Translate is unavailable. Completed entries are saved; try again later.")
}

internal suspend fun googleTextWithBackoff(
    onRetry: suspend (Long) -> Unit, request: suspend () -> String,
): String {
    for (attempt in 0..3) {
        coroutineContext.ensureActive()
        try { return request() } catch (limited: GoogleRateLimit) {
            val wait = googleRetryDelayMillis(limited.retryAfter, attempt, System.currentTimeMillis())
            if (attempt == 3 || wait == null) {
                error("Google Translate is rate-limiting this connection (HTTP 429). Try again later; completed entries are saved.")
            }
            onRetry(wait)
            delay(wait)
        }
    }
    error("Google Translate could not complete the request.")
}

internal data class TranslationPiece(val locator: String, val index: Int, val total: Int, val text: String)
internal data class MarkedBatch(val pieces: List<TranslationPiece>, val nonce: String) {
    private fun marker(index: Int) = "⟦${nonce}:${index.toString().padStart(4, '0')}⟧"
    fun requestText(): String = buildString {
        pieces.forEachIndexed { i, piece -> append(marker(i)).append('\n').append(piece.text.trim()).append('\n') }
        append(marker(pieces.size))
    }
    fun parse(translated: String): List<String> {
        val pattern = Regex("⟦${nonce}:[0-9]{4}⟧")
        val matches = pattern.findAll(translated).toList()
        require(matches.size == pieces.size + 1 && matches.map { it.value } == (0..pieces.size).map(::marker)) {
            "Google changed the line markers, so the batch could not be aligned safely. No unverified translations were saved."
        }
        require(translated.substring(0, matches.first().range.first).isBlank() &&
            translated.substring(matches.last().range.last + 1).isBlank()) {
            "Google returned text outside the line markers; the batch could not be aligned safely."
        }
        return pieces.indices.map { i ->
            translated.substring(matches[i].range.last + 1, matches[i + 1].range.first).trim().also {
                require(it.isNotBlank()) { "Google returned an empty translation for ${pieces[i].locator}." }
            }
        }
    }
}

/** Pack as much as safely fits into one keyless GET request without losing entry boundaries. */
internal fun planTranslationBatches(entries: List<EditorEntry>, alreadyTranslated: Map<String, String>,
    maxEncodedChars: Int = 7600, maxPieces: Int = 100): List<MarkedBatch> {
    require(maxEncodedChars > 0 && maxPieces > 0)
    val pieces = entries.filter { it.sourceJapanese.isNotBlank() && alreadyTranslated[it.locator].isNullOrBlank() }
        .flatMap { entry ->
            val parts = splitForGoogle(entry.sourceJapanese, 400)
            parts.mapIndexed { i, part -> TranslationPiece(entry.locator, i, parts.size, part) }
        }
    val batches = mutableListOf<MarkedBatch>()
    var current = mutableListOf<TranslationPiece>()
    fun batch(items: List<TranslationPiece>) = MarkedBatch(items.toList(), "123456789012")
    fun encodedSize(items: List<TranslationPiece>) = URLEncoder.encode(batch(items).requestText(), "UTF-8").length
    for (piece in pieces) {
        val next = current + piece
        if (current.isNotEmpty() && (next.size > maxPieces || encodedSize(next) > maxEncodedChars)) {
            batches += MarkedBatch(current.toList(), Random.nextLong(100_000_000_000L, 1_000_000_000_000L).toString())
            current = mutableListOf()
        }
        current += piece
        require(encodedSize(current) <= maxEncodedChars) { "A source entry is too long for a single translation request." }
    }
    if (current.isNotEmpty()) batches += MarkedBatch(current.toList(), Random.nextLong(100_000_000_000L, 1_000_000_000_000L).toString())
    return batches
}

/** Translate validated batches; completed entries are checkpointed between requests. */
internal suspend fun translateChapterInBatches(
    chapterPath: String, document: EditorDocument, alreadyTranslated: Map<String, String> = emptyMap(),
    request: suspend (String) -> String,
    onCheckpoint: suspend (Map<String, String>) -> Unit = {},
    onProgress: suspend (Int, Int) -> Unit = { _, _ -> },
): ImportedTranslationOverlay {
    val entries = document.entries.filter { it.sourceJapanese.isNotBlank() }
    val valid = entries.mapTo(hashSetOf()) { it.locator }
    val results = alreadyTranslated.filter { (key, value) -> key in valid && value.isNotBlank() }.toMutableMap()
    val parts = mutableMapOf<String, MutableList<String>>()
    val batches = planTranslationBatches(entries, results)
    onProgress(results.size, entries.size)
    for (batch in batches) {
        coroutineContext.ensureActive()
        val translated = batch.parse(request(batch.requestText()))
        for ((i, piece) in batch.pieces.withIndex()) {
            val targetParts = parts.getOrPut(piece.locator) { mutableListOf() }
            require(targetParts.size == piece.index) { "Translation fragments are out of order." }
            val prefix = piece.text.takeWhile { it.isWhitespace() }
            val suffix = piece.text.takeLastWhile { it.isWhitespace() }
            var next = prefix + translated[i] + suffix
            if (targetParts.isNotEmpty() && targetParts.last().lastOrNull()?.isLetterOrDigit() == true &&
                next.firstOrNull()?.isLetterOrDigit() == true) next = " " + next
            targetParts += next
            if (targetParts.size == piece.total) {
                results[piece.locator] = targetParts.joinToString("")
                parts.remove(piece.locator)
            }
        }
        onCheckpoint(results.toMap())
        onProgress(results.size, entries.size)
    }
    require(parts.isEmpty() && results.size == entries.size) { "Google returned an incomplete chapter translation." }
    return translationOverlay(chapterPath, document, results)
}

internal suspend fun translateWithGoogle(
    chapterPath: String, document: EditorDocument, languages: TranslationLanguages,
    alreadyTranslated: Map<String, String> = emptyMap(),
    onCheckpoint: suspend (Map<String, String>) -> Unit = {},
    onRetry: suspend (Long) -> Unit = {},
    onProgress: suspend (Int, Int) -> Unit,
): ImportedTranslationOverlay = withContext(Dispatchers.IO) {
    languages.validate()
    var previousRequest = false
    translateChapterInBatches(chapterPath, document, alreadyTranslated = alreadyTranslated,
        onCheckpoint = onCheckpoint, onProgress = onProgress,
        request = { text ->
            if (previousRequest) delay(1000)
            previousRequest = true
            translateUsingGoogleRoutes(onRetry = onRetry) { route -> requestGoogleText(text, languages, route) }
        })
}
