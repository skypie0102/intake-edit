package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MachineTranslationTest {
    private fun document() = EditorDocument(
        schemaVersion = 6, volume = 1, chapter = 1, sourceHref = "source.xhtml", sourceSha256 = "source-sha",
        readerFile = "chapter.xhtml", englishTitle = "", instructions = "", editorReviewComplete = false,
        entries = listOf(EditorEntry("H1-1", "題名", "Existing title"), EditorEntry("P1", "本文", ""), EditorEntry("P2", "最後", "Existing English")),
    )

    @Test fun translatesEveryEntryIntoAnOrderedReferenceWithoutChangingEnglish() = runTest {
        val document = document()
        val calls = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = translateChapterReference("chapter.yml", document, translate = {
            calls += it
            "translated:$it"
        }, onProgress = { current, total -> progress += current to total })
        assertEquals(listOf("題名", "本文", "最後"), calls)
        assertEquals("translated:題名", result.translationFor("H1-1"))
        assertEquals("translated:本文", result.translationFor("P1"))
        assertEquals("translated:最後", result.translationFor("P2"))
        assertEquals("Existing English", document.entries.last().english)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals("source-sha", result.sourceSha256)
    }

    @Test fun failedEntryDoesNotReturnAPartialReference() = runTest {
        var caught: Exception? = null
        try {
            translateChapterReference("chapter.yml", document(), translate = {
                if (it == "本文") error("translation failed") else "translated"
            })
        } catch (e: IllegalStateException) { caught = e }
        assertEquals("translation failed", caught?.message)
    }
}
