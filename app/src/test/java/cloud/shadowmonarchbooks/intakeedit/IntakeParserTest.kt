package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeParserTest {
    private val yaml = """
        schema_version: 5
        volume: 1
        chapter: 1
        source_href: canonical/vol-01/ch_0001.xhtml
        source_sha256: abc123
        reader_file: reader/vol-01/ch_0001.xhtml
        english_title: Did Yukino Shirasaka Get a Boyfriend?
        instructions: Supply authoritative English for every paragraph, then explicitly mark the chapter reviewed.
        editor_review_complete: false
        entries:
        - locator: P1
          source_japanese: テストです。
          english: 'It''s a test.'
        - locator: P2
          source_japanese: 二番目の文。
          english: ''
    """.trimIndent()

    @Test
    fun parsesSchemaV5Yaml() {
        val document = IntakeParser.parse(yaml)

        assertEquals(5, document.schemaVersion)
        assertEquals(1, document.volume)
        assertEquals(1, document.chapter)
        assertEquals("Did Yukino Shirasaka Get a Boyfriend?", document.englishTitle)
        assertEquals(2, document.entries.size)
        assertEquals("It's a test.", document.entries[0].english)
        assertEquals(1, document.englishSupplied)
        assertEquals(2, document.englishTotal)
        assertFalse(document.editorReviewComplete)
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

        assertTrue(patched.startsWith("schema_version: 5"))
        assertTrue(patched.contains("editor_review_complete: true"))
        assertEquals("Revised \"English\".\nSecond line.", reparsed.entries[0].english)
        assertEquals("Editor supplied", reparsed.entries[1].english)
        assertTrue(reparsed.editorReviewComplete)
        assertTrue(IntakeParser.validate(patched).isSuccess)
    }

    @Test
    fun rejectsOldSchemaFields() {
        val invalid = yaml.replace("          english: 'It''s a test.'", "          kind: safe\n          english: 'It''s a test.'")
        assertTrue(IntakeParser.validate(invalid).isFailure)
    }
}
