package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun hideThenRestoreNeverRequestsAnotherTranslation() {
        assertEquals(MachineToggle.TRANSLATE, nextMachineAction(selected = false, cached = false))
        assertEquals(MachineToggle.HIDE, nextMachineAction(selected = true, cached = true))
        assertEquals(MachineToggle.RESTORE, nextMachineAction(selected = false, cached = true))
    }

    @Test fun cacheIdentitySeparatesProjectsAndAutomaticallyTargetsEnglish() {
        val languages = TranslationLanguages()
        assertEquals("auto", languages.normalizedSource)
        assertEquals("en", languages.normalizedTarget)
        assertNotEquals(machineCacheKey(RepoSettings(), languages), machineCacheKey(RepoSettings(repo = "other"), languages))
        assertNotEquals(machineCacheKey(RepoSettings(), languages), machineCacheKey(RepoSettings(branch = "other"), languages))
    }

    @Test fun parsesGoogleResponseWithoutLosingSegments() {
        assertEquals("Hello, world!", parseGoogleTranslation("[[[\"Hello, \",\"a\"],[\"world!\",\"b\"]],null,\"ja\"]"))
    }

    @Test fun longEntriesSplitWithoutLosingUnicodeOrSourceText() {
        val source = "あいうえお ".repeat(1000) + "𠮷"
        val chunks = splitForGoogle(source, 100)
        assertEquals(source, chunks.joinToString(""))
        assertEquals(true, chunks.all { it.codePointCount(0, it.length) <= 100 })
    }

    @Test fun rateLimitsHonorRetryAfterAndAvoidLongAutomaticWaits() {
        assertEquals(3000L, googleRetryDelayMillis(null, 0, 0L))
        assertEquals(6000L, googleRetryDelayMillis(null, 1, 0L))
        assertEquals(10000L, googleRetryDelayMillis("10", 0, 0L))
        assertEquals(null, googleRetryDelayMillis("60", 0, 0L))
        assertEquals(10000L, googleRetryDelayMillis("Thu, 01 Jan 1970 00:00:10 GMT", 0, 0L))
    }

    @Test fun resumedTranslationSkipsSavedEntriesAndCheckpointsNewOnes() = runTest {
        val calls = mutableListOf<String>()
        val checkpoints = mutableListOf<Map<String, String>>()
        val result = translateChapterReference("chapter.yml", document(),
            alreadyTranslated = mapOf("H1-1" to "Saved title"),
            translate = { calls += it; "translated:$it" },
            onCheckpoint = { checkpoints += it })
        assertEquals(listOf("本文", "最後"), calls)
        assertEquals("Saved title", result.translationFor("H1-1"))
        assertEquals(2, checkpoints.size)
        assertEquals(3, checkpoints.last().size)
    }

    @Test fun multipleEntriesUseOneRequestAndStayAligned() = runTest {
        var calls = 0
        val result = translateChapterInBatches("chapter.yml", document(), request = { payload ->
            calls++
            payload.replace("題名", "Title").replace("本文", "Body").replace("最後", "Last")
        })
        assertEquals(1, calls)
        assertEquals("Title", result.translationFor("H1-1"))
        assertEquals("Body", result.translationFor("P1"))
        assertEquals("Last", result.translationFor("P2"))
    }

    @Test fun completedEntriesAreNotIncludedInNewBatches() = runTest {
        var calls = 0
        val result = translateChapterInBatches("chapter.yml", document(),
            alreadyTranslated = mapOf("H1-1" to "Saved title"), request = { payload ->
                calls++
                assertEquals(false, payload.contains("題名"))
                payload.replace("本文", "Body").replace("最後", "Last")
            })
        assertEquals(1, calls)
        assertEquals("Saved title", result.translationFor("H1-1"))
        assertEquals("Body", result.translationFor("P1"))
    }

    @Test fun changedOrMissingMarkersNeverSaveMisalignedResults() = runTest {
        var checkpoints = 0
        var caught: Exception? = null
        try {
            translateChapterInBatches("chapter.yml", document(), request = { "A response without markers" },
                onCheckpoint = { checkpoints++ })
        } catch (error: IllegalArgumentException) { caught = error }
        assertEquals(0, checkpoints)
        assertEquals(true, caught?.message?.contains("markers"))
    }

    @Test fun batchPlannerRespectsRequestSizeAndCanSpanManyRequests() {
        val entries = (1..20).map { EditorEntry("P$it", "日本語".repeat(40), "") }
        val batches = planTranslationBatches(entries, emptyMap(), maxEncodedChars = 2400, maxPieces = 10)
        assertEquals(true, batches.size > 1)
        assertEquals(entries.map { it.locator }, batches.flatMap { it.pieces }.map { it.locator })
        assertEquals(true, batches.all { java.net.URLEncoder.encode(it.requestText(), "UTF-8").length <= 2400 })
    }

    @Test fun google429WaitsBeforeRetryAndCanRecover() = runTest {
        var requests = 0
        val waits = mutableListOf<Long>()
        val result = googleTextWithBackoff(onRetry = { waits += it }) {
            requests++
            if (requests == 1) throw GoogleRateLimit("4")
            "Success"
        }
        assertEquals("Success", result)
        assertEquals(2, requests)
        assertEquals(listOf(4000L), waits)
    }

    @Test fun google429WithLongRetryAfterStopsWithoutAnotherRequest() = runTest {
        var requests = 0
        var caught: Exception? = null
        try {
            googleTextWithBackoff(onRetry = {}) { requests++; throw GoogleRateLimit("60") }
        } catch (error: IllegalStateException) { caught = error }
        assertEquals(1, requests)
        assertEquals(true, caught?.message?.contains("HTTP 429"))
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
