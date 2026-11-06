package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChapterQaLoadTest {
    @Test fun missingFindingsAreNotTreatedAsAnError() = runTest {
        assertNull(loadChapterQaSafely { null })
    }

    @Test fun failedQaLoadIsReportedRatherThanHidden() = runTest {
        try {
            loadChapterQaSafely { error("network unavailable") }
            fail("Expected QA loading to fail")
        } catch (error: GitHubException) {
            assertTrue(error.message.orEmpty().contains("Retry opening"))
            assertTrue(error.message.orEmpty().contains("network unavailable"))
        }
    }

    @Test fun cancellationIsNotConvertedToAnError() = runTest {
        try {
            loadChapterQaSafely { throw CancellationException("cancelled") }
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertTrue(error.message.orEmpty().contains("cancelled"))
        }
    }
}
