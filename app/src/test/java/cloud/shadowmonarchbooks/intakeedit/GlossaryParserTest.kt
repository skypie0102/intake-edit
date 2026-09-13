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
    }

    @Test
    fun proposalRoundTripPreservesVolumeEvidence() {
        val proposals = listOf(
            GlossaryProposal(
                id = "volume-lock",
                action = "lock_recommendation",
                reason = "Whole-volume evidence.",
                targetId = "characters:白坂雪乃",
                sourceVolume = 1,
                sourceChapter = 0,
                sourceLocator = "P5",
                occurrenceCount = 42,
                sourceChapters = listOf(0, 1, 2),
            ),
        )
        val parsed = GlossaryParser.parseProposals(GlossaryParser.serializeProposals(proposals))
        assertEquals(proposals, parsed)
    }

    @Test
    fun pendingNewEntryThatAlreadyExistsIsHidden() {
        val base = GlossaryEntry("terms:既存", "terms", listOf("既存"), "Existing Term")
        val documents = GlossaryDocuments(
            baseEntries = listOf(base),
            additions = emptyList(),
            governance = emptyMap(),
            proposals = listOf(
                GlossaryProposal(
                    id = "duplicate",
                    action = "new_entry",
                    reason = "duplicate",
                    section = "terms",
                    sourceAliases = listOf("既存"),
                    translatedName = "Something Else",
                ),
            ),
        )
        assertTrue(documents.pendingProposals.isEmpty())
    }

    @Test
    fun originalFormatExportUsesExactHeaderAndSpacing() {
        val entries = listOf(
            GlossaryEntry("characters:吉田", "characters", listOf("吉田"), "Yoshida", "male", "protagonist"),
        )
        val raw = GlossaryExport.serialize(entries)
        assertTrue(raw.startsWith("Glossary Columns: raw_name, translated_name, gender, description, description\n\n=== CHARACTERS ===\n* 吉田 = Yoshida [male]: protagonist\n\n=== LOCATIONS ===\n"))
        assertTrue(raw.endsWith("=== HONORIFICS ===\n"))
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
