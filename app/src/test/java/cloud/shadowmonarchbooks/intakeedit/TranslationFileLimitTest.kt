package cloud.shadowmonarchbooks.intakeedit

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranslationFileLimitTest {
    @Test fun acceptsExactLimitAndRejectsOversizedInput() {
        val bytes = "abcd".toByteArray()
        assertArrayEquals(bytes, readTranslationFile(ByteArrayInputStream(bytes), maxBytes = 4))
        assertThrows(IllegalArgumentException::class.java) { readTranslationFile(ByteArrayInputStream(bytes), maxBytes = 3) }
    }
}
