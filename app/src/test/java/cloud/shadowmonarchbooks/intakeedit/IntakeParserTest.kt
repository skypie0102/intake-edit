package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeParserTest {
    private val yaml = """
        schema_version: 6
        volume: 1
        chapter: 1
        source_href: canonical/vol-01/ch_0001.xhtml
        source_sha256: abc123
        reader_file: reader/vol-01/ch_0001.xhtml
        english_title: Did Yukino Shirasaka Get a Boyfriend?
        instructions: Supply authoritative English for every paragraph, then explicitly mark the chapter reviewed.
        editor_review_complete: false
        endnotes: []
        entries:
        - locator: P1
          source_japanese: テストです。
          english: 'It''s a test.'
        - locator: P2
          source_japanese: 二番目の文。
          english: ''
    """.trimIndent()

    @Test
    fun parsesSchemaV6Yaml() {
        val document = IntakeParser.parse(yaml)

        assertEquals(6, document.schemaVersion)
        assertEquals(1, document.volume)
        assertEquals(1, document.chapter)
        assertEquals("Did Yukino Shirasaka Get a Boyfriend?", document.englishTitle)
        assertEquals(2, document.entries.size)
        assertEquals("It's a test.", document.entries[0].english)
        assertEquals(1, document.englishSupplied)
        assertEquals(2, document.englishTotal)
        assertTrue(document.endnotes.isEmpty())
        assertFalse(document.editorReviewComplete)
    }

    @Test
    fun parsesHeadingEntriesAndUsesH1AsDisplayTitle() {
        val withHeading = yaml.replace(
            "entries:\n",
            "entries:\n- locator: H1-1\n  source_japanese: 原題\n  english: English Title\n",
        )
        val document = IntakeParser.parse(withHeading)

        assertEquals(3, document.entries.size)
        assertEquals("H1-1", document.entries.first().locator)
        assertEquals("English Title", document.displayTitle)
        assertEquals(2, document.englishSupplied)
        assertEquals(3, document.englishTotal)
    }

    @Test
    fun patchesYamlEnglishAndReviewFlagWithoutConvertingTheDocument() {
        val original = IntakeParser.parse(yaml)
        val entries = original.entries.mapIndexed { index, entry ->
            if (index == 0) entry.copy(english = "Revised \"English\".\nSecond line.") else entry.copy(english = "Editor supplied")
        }
        val updated = original.copy(entries = entries, editorReviewComplete = true)
        val patched = IntakeParser.patchDocument(yaml, updated)
        val reparsed = IntakeParser.parse(patched)

        assertTrue(patched.startsWith("schema_version: 6"))
        assertTrue(patched.contains("editor_review_complete: true"))
        assertEquals("Revised \"English\".\nSecond line.", reparsed.entries[0].english)
        assertEquals("Editor supplied", reparsed.entries[1].english)
        assertTrue(reparsed.editorReviewComplete)
        assertTrue(IntakeParser.validate(patched).isSuccess)
    }

    @Test
    fun patchesOnlySelectedEnglishEntry() {
        val patchedEnglish = IntakeParser.patchEnglishAt(yaml, 1, "Only the second entry changes.")
        val patched = IntakeParser.patchReviewComplete(patchedEnglish, true)
        val reparsed = IntakeParser.parse(patched)

        assertEquals("It's a test.", reparsed.entries[0].english)
        assertEquals("Only the second entry changes.", reparsed.entries[1].english)
        assertTrue(reparsed.editorReviewComplete)
        assertTrue(IntakeParser.validate(patched).isSuccess)
    }

    @Test
    fun upgradesSchemaV5YamlWhenEndnoteIsAdded() {
        val original = IntakeParser.parse(yaml)
        val entries = original.entries.toMutableList()
        entries[0] = entries[0].copy(english = "Visit [en=en-P1-01]Comiket[/en].")
        val upgraded = original.copy(
            schemaVersion = 6,
            entries = entries,
            endnotes = listOf(EndnoteDefinition("en-P1-01", "P1", "A translator note.")),
        )

        val patched = IntakeParser.patchDocument(yaml, upgraded)
        val reparsed = IntakeParser.parse(patched)

        assertTrue(patched.startsWith("schema_version: 6"))
        assertTrue(patched.contains("endnotes:"))
        assertEquals(6, reparsed.schemaVersion)
        assertEquals(1, reparsed.endnotes.size)
        assertEquals("A translator note.", reparsed.endnotes.single().content)
        assertEquals("Visit [en=en-P1-01]Comiket[/en].", reparsed.entries[0].english)
        assertTrue(IntakeParser.validate(patched).isSuccess)
    }

    @Test
    fun parsesEmptySchemaV6EndnotesList() {
        val upgraded = yaml
            .replace("schema_version: 6", "schema_version: 6")
            .replace("entries:\n", "endnotes: []\nentries:\n")
        val document = IntakeParser.parse(upgraded)
        assertEquals(6, document.schemaVersion)
        assertTrue(document.endnotes.isEmpty())
    }

    @Test
    fun rejectsOrphanEndnoteDefinition() {
        val invalid = yaml
            .replace("schema_version: 6", "schema_version: 6")
            .replace(
                "entries:\n",
                "endnotes:\n- id: en-P1-01\n  locator: P1\n  content: \"Unused note\"\nentries:\n",
            )
        assertTrue(IntakeParser.validate(invalid).isFailure)
    }

    @Test
    fun rejectsSchemaV5Yaml() {
        val legacy = yaml.replace("schema_version: 6", "schema_version: 5")
        assertTrue(IntakeParser.validate(legacy).isFailure)
    }

    @Test
    fun rejectsOldSchemaFields() {
        val invalid = yaml.replace("  english: 'It''s a test.'", "  kind: safe\n  english: 'It''s a test.'")
        assertTrue(IntakeParser.validate(invalid).isFailure)
    }
}
