package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Test

class QaScopeHashesTest {
    @Test fun repeatedFindingsAtOneLocatorReuseTheSameSnapshotHash() {
        val document = EditorDocument(6, 1, 1, "source", "sha", "reader", "title", "", false,
            listOf(EditorEntry("P1", "raw", "English")))
        var calls = 0
        val hashes = QaScopeHashes(document) { _, locator -> calls++; "hash:$locator" }
        assertEquals("hash:P1", hashes.get("P1"))
        assertEquals("hash:P1", hashes.get("P1"))
        assertEquals(1, calls)
    }
}
