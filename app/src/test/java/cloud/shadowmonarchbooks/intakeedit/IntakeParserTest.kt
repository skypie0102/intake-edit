package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntakeParserTest {
    private val yaml = """
        schema_version: 4
        volume: 1
        chapter: 1
        source_href: canonical/vol-01/ch_0001.xhtml
        source_sha256: abc123
        reader_file: reader/vol-01/ch_0001.xhtml
        english_title: Did Yukino Shirasaka Get a Boyfriend?
        instructions: Edit authoritative English only. Restricted English is human-supplied.
        editor_review_complete: false
        entries:
        - locator: P1
          kind: safe
          source_japanese: テストです。
          safe_translation: 'It''s a test.'
          sanitized_translation: ''
          english: 'It''s a test.'
        - locator: P2
          kind: restricted
          source_japanese: 制限された文。
          safe_translation: ''
          sanitized_translation: A non-explicit summary.
          english: ''
    """.trimIndent()

    @Test
    fun parsesSchemaV4Yaml() {
        val document = IntakeParser.parse(yaml)

        assertEquals(4, document.schemaVersion)
        assertEquals(1, document.volume)
        assertEquals(1, document.chapter)
        assertEquals("Did Yukino Shirasaka Get a Boyfriend?", document.englishTitle)
        assertEquals(2, document.entries.size)
        assertEquals("It's a test.", document.entries[0].english)
        assertTrue(document.entries[1].isRestricted)
        assertEquals("A non-explicit summary.", document.entries[1].sanitizedTranslation)
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

        assertTrue(patched.startsWith("schema_version: 4"))
        assertTrue(patched.contains("editor_review_complete: true"))
        assertEquals("Revised \"English\".\nSecond line.", reparsed.entries[0].english)
        assertEquals("Editor supplied", reparsed.entries[1].english)
        assertTrue(reparsed.editorReviewComplete)
        assertTrue(IntakeParser.validate(patched).isSuccess)
    }
}
