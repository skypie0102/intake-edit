package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryParserTest {
    @Test
    fun parsesRestoredTextEntry() {
        val entries = GlossaryParser.parseBaseFile(
            """
            === CHARACTERS ===
            * 白坂雪乃 = Shirasaka Yukino [female]: heroine
            """.trimIndent(),
        )
        assertEquals(1, entries.size)
        assertEquals("characters:白坂雪乃", entries.single().id)
        assertEquals("Shirasaka Yukino", entries.single().translatedName)
        assertEquals("female", entries.single().gender)
        assertFalse(entries.single().qaLock)
    }

    @Test
    fun governanceOverlayCanLockWithoutDuplicatingEntry() {
        val base = GlossaryEntry("characters:白坂雪乃", "characters", listOf("白坂雪乃"), "Shirasaka Yukino")
        val merged = GlossaryParser.merge(
            base = listOf(base),
            additions = emptyList(),
            governance = mapOf(base.id to GlossaryOverride(qaLock = true)),
        )
        assertEquals(1, merged.size)
        assertTrue(merged.single().qaLock)
        assertEquals("Shirasaka Yukino", merged.single().translatedName)
    }

    @Test
    fun proposalRoundTripPreservesTranslationSourceAndLockRecommendation() {
        val proposals = listOf(
            GlossaryProposal(
                id = "v1-ch2-p9-term",
                action = "new_entry",
                reason = "Recurring named term introduced during translation.",
                section = "terms",
                sourceAliases = listOf("架空用語"),
                translatedName = "Example Term",
                recommendQaLock = true,
                sourceVolume = 1,
                sourceChapter = 2,
                sourceLocator = "P9",
            ),
        )
        val parsed = GlossaryParser.parseProposals(GlossaryParser.serializeProposals(proposals))
        assertEquals(proposals, parsed)
    }

    @Test
    fun additionsRoundTripPreservesCaseSensitiveLock() {
        val entries = listOf(
            GlossaryEntry(
                id = "terms:架空ブランド",
                section = "terms",
                sourceAliases = listOf("架空ブランド"),
                translatedName = "ExampleBrand",
                qaLock = true,
                caseSensitive = true,
                origin = "editor-approved",
            ),
        )
        val parsed = GlossaryParser.parseAdditions(GlossaryParser.serializeAdditions(entries))
        assertEquals(entries, parsed)
    }
}
