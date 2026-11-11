package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Test

class RawCopyTextTest {
    @Test fun excludesMachineWhenCopyRefIsOff() {
        assertEquals("RAW:\n原文", rawCopyText("原文", "Machine draft", false, true))
    }

    @Test fun excludesImportedWhenCopyRefIsOff() {
        assertEquals("RAW:\n原文", rawCopyText("原文", "Imported draft", false, false))
    }

    @Test fun includesMachineWhenCopyRefIsOn() {
        assertEquals(
            "RAW:\n原文\n\nMACHINE TRANSLATION:\nMachine draft",
            rawCopyText("原文", "Machine draft", true, true),
        )
    }

    @Test fun includesImportedWhenCopyRefIsOn() {
        assertEquals(
            "RAW:\n原文\n\nIMPORTED TRANSLATION:\nImported draft",
            rawCopyText("原文", "Imported draft", true, false),
        )
    }

    @Test fun copiesRawWithoutAnAvailableReference() {
        assertEquals("RAW:\n原文", rawCopyText("原文", null, true, true))
    }
}
