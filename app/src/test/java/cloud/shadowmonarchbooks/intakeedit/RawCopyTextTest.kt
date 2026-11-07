package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Test

class RawCopyTextTest {
    @Test
    fun copiesOnlyRawWhenMachineReferenceIsVisible() {
        assertEquals("RAW:\n原文", rawCopyText("原文", "Machine draft", true))
    }

    @Test
    fun keepsImportedReferenceInCopy() {
        assertEquals("RAW:\n原文\n\nIMPORTED TRANSLATION:\nImported draft", rawCopyText("原文", "Imported draft", false))
    }

    @Test
    fun copiesRawWhenThereIsNoReference() {
        assertEquals("RAW:\n原文", rawCopyText("原文", null, false))
    }
}
