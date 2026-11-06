package cloud.shadowmonarchbooks.intakeedit

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

class QaPerformanceAndroidTest {
    @Test fun reportsRepeatedQaHashTimingOnEmulator() {
        val entries = (1..1000).map { EditorEntry("P$it", "日本語 $it", "Translated paragraph $it") }
        val document = EditorDocument(6, 1, 1, "source", "sha", "reader", "title", "", false, entries)
        val iterations = 200
        val directStart = System.nanoTime()
        var direct = ""
        repeat(iterations) { direct = QaFindingsParser.findingContentSha256(document, "P1000") }
        val directNanos = System.nanoTime() - directStart
        val cache = QaScopeHashes(document)
        val cachedStart = System.nanoTime()
        var cached = ""
        repeat(iterations) { cached = cache.get("P1000") }
        val cachedNanos = System.nanoTime() - cachedStart
        assertEquals(direct, cached)
        Log.i("IntakeQaBenchmark", "200 repeated paragraph hashes, 1000 entries: direct=${directNanos / 1_000_000}ms cached=${cachedNanos / 1_000_000}ms")
    }
}
