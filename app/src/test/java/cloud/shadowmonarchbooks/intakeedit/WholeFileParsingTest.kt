package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WholeFileParsingTest {
    @Test fun cancelledDraftIsNotParsedAndInvalidDraftIsPreserved() = runTest {
        val parsed = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = async { parseWholeFileDraft("old", dispatcher) { raw -> parsed += raw; error("invalid") } }
        runCurrent()
        advanceTimeBy(100)
        old.cancel()
        val latest = async { parseWholeFileDraft("latest", dispatcher) { raw -> parsed += raw; error("invalid") } }
        advanceTimeBy(250)
        runCurrent()
        assertNull(latest.await())
        assertEquals(listOf("latest"), parsed)
    }
}
