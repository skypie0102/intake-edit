package cloud.shadowmonarchbooks.intakeedit

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitTranslationTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

/** Translation is a local reference. English is changed only by an explicit editor action. */
internal suspend fun translateChapterReference(
    chapterPath: String,
    document: EditorDocument,
    translate: suspend (String) -> String,
    onProgress: (Int, Int) -> Unit = { _, _ -> },
): ImportedTranslationOverlay {
    val entries = document.entries
    val results = linkedMapOf<String, String>()
    for ((index, entry) in entries.withIndex()) {
        val source = entry.sourceJapanese
        if (source.isNotBlank()) {
            val text = translate(source)
            require(text.isNotBlank()) { "No translation returned for ${entry.locator}." }
            results[entry.locator] = text
        }
        onProgress(index + 1, entries.size)
    }
    return ImportedTranslationOverlay(
        chapterPath = chapterPath,
        sourceSha256 = document.sourceSha256,
        fileName = "Machine Translation (Google Translate offline model)",
        translations = results,
        matchedCount = results.size,
        totalEntries = entries.size,
        sourceParagraphs = entries.size,
        targetParagraphs = results.size,
        alignment = "Per entry",
    )
}

internal suspend fun translateWithGoogle(
    chapterPath: String,
    document: EditorDocument,
    onProgress: (Int, Int) -> Unit,
): ImportedTranslationOverlay {
    val translator = Translation.getClient(TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.JAPANESE)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build())
    try {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTranslationTask()
        return translateChapterReference(chapterPath, document,
            translate = { translator.translate(it).awaitTranslationTask() }, onProgress = onProgress)
    } finally {
        translator.close()
    }
}
