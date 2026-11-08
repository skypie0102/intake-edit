package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationImportTest {
    private fun document(vararg source: String) = EditorDocument(
        schemaVersion = 6,
        volume = 1,
        chapter = 1,
        sourceHref = "canonical/vol-01/ch_0001.xhtml",
        sourceSha256 = "a".repeat(64),
        readerFile = "reader/vol-01/ch_0001.xhtml",
        englishTitle = "Test",
        instructions = "",
        editorReviewComplete = false,
        entries = source.mapIndexed { index, text ->
            EditorEntry("P${index + 1}", text, "")
        },
    )

    @Test
    fun importsWholeXhtmlFromSingleSdlxliffUnit() {
        val sourceHtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>第一</p><p><br/></p><p>第二</p></body></html>"""
        val targetHtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>First rough</p><p><br/></p><p>Second rough</p></body></html>"""
        val xliff = """<?xml version="1.0"?><xliff xmlns="urn:oasis:names:tc:xliff:document:1.2" version="1.2"><file><body><trans-unit id="1"><source>${escape(sourceHtml)}</source><target>${escape(targetHtml)}</target></trans-unit></body></file></xliff>"""

        val result = XliffTranslationImporter.parse(xliff, "chapter.sdlxliff", "path/ch.yml", document("第一", "第二"))

        assertEquals("Exact", result.alignment)
        assertEquals(2, result.matchedCount)
        assertEquals("First rough", result.translationFor("P1"))
        assertEquals("Second rough", result.translationFor("P2"))
    }

    @Test
    fun importsH1H2AndParagraphsFromWholeXhtmlSdlxliffUnit() {
        val sourceHtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1 class="chapter-title chapter-numbered"><a class="chapter-number" href="toc.xhtml">1</a><span class="chapter-title-text">第一話</span></h1>
            <section><h2 class="note-title">前書き</h2><p>本文です。</p></section>
        </body></html>"""
        val targetHtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1 class="chapter-title chapter-numbered"><a class="chapter-number" href="toc.xhtml">1</a><span class="chapter-title-text">Chapter One</span></h1>
            <section><h2 class="note-title">Preface</h2><p>Body text.</p></section>
        </body></html>"""
        val xliff = """<?xml version="1.0"?><xliff xmlns="urn:oasis:names:tc:xliff:document:1.2" version="1.2"><file><body><trans-unit id="1"><source>${escape(sourceHtml)}</source><target>${escape(targetHtml)}</target></trans-unit></body></file></xliff>"""
        val editor = documentWithEntries(
            "H1-1" to "第一話",
            "H2-1" to "前書き",
            "P1" to "本文です。",
        )

        val result = XliffTranslationImporter.parse(xliff, "chapter.sdlxliff", "path/ch.yml", editor)

        assertEquals("Exact", result.alignment)
        assertEquals(3, result.matchedCount)
        assertEquals("Chapter One", result.translationFor("H1-1"))
        assertEquals("Preface", result.translationFor("H2-1"))
        assertEquals("Body text.", result.translationFor("P1"))
    }

    @Test
    fun importsConventionalMultiUnitXliff() {
        val xliff = """<xliff xmlns="urn:oasis:names:tc:xliff:document:1.2" version="1.2"><file><body>
            <trans-unit id="1"><source>第一</source><target>First</target></trans-unit>
            <trans-unit id="2"><source>第二</source><target>Second</target></trans-unit>
        </body></file></xliff>"""
        val result = XliffTranslationImporter.parse(xliff, "chapter.xlf", "path/ch.yml", document("第一", "第二"))
        assertEquals(2, result.matchedCount)
        assertEquals("First", result.translationFor("P1"))
        assertEquals("Second", result.translationFor("P2"))
    }

    @Test
    fun rejectsWrongChapterInsteadOfFuzzyGuessing() {
        val xliff = """<xliff xmlns="urn:oasis:names:tc:xliff:document:1.2" version="1.2"><file><body>
            <trans-unit id="1"><source>別の章</source><target>Wrong chapter</target></trans-unit>
            <trans-unit id="2"><source>違う段落</source><target>Still wrong</target></trans-unit>
            <trans-unit id="3"><source>さらに違う</source><target>Nope</target></trans-unit>
        </body></file></xliff>"""
        val failure = runCatching {
            XliffTranslationImporter.parse(xliff, "wrong.sdlxliff", "path/ch.yml", document("第一", "第二", "第三"))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("does not appear to match"))
    }

    @Test
    fun toleratesJapaneseWhitespaceDifferencesButNotDifferentText() {
        val xliff = """<xliff xmlns="urn:oasis:names:tc:xliff:document:1.2" version="1.2"><file><body>
            <trans-unit id="1"><source>白坂　雪乃</source><target>Shirasaka Yukino</target></trans-unit>
            <trans-unit id="2"><source>第二</source><target>Second</target></trans-unit>
        </body></file></xliff>"""
        val result = XliffTranslationImporter.parse(xliff, "chapter.xlf", "path/ch.yml", document("白坂雪乃", "第二"))
        assertEquals("Shirasaka Yukino", result.translationFor("P1"))
    }

    @Test
    fun importsTranslatedXhtmlWithHeadingsAndEmptyParagraphs() {
        val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>This is not a chapter entry</title></head>
            <body>
                <h1 class="chapter-title chapter-numbered"><a class="chapter-number" href="toc.xhtml">1</a><span class="chapter-title-text">Chapter One</span></h1>
                <p><br/></p>
                <section><h2>Preface</h2><p>And <em>now</em> more.</p></section>
            </body></html>"""
        val editor = documentWithEntries(
            "H1-1" to "第一話",
            "H2-1" to "前書き",
            "P1" to "そして続く。",
        )

        val result = TranslationReferenceImporter.parse(xhtml, "ch_0001.xhtml", "path/ch.yml", editor)

        assertEquals("XHTML structure verified", result.alignment)
        assertEquals(3, result.matchedCount)
        assertEquals(3, result.targetParagraphs)
        assertEquals("Chapter One", result.translationFor("H1-1"))
        assertEquals("Preface", result.translationFor("H2-1"))
        assertEquals("And now more.", result.translationFor("P1"))
    }

    @Test
    fun importsXhtmlWithoutRecognizableExtensionBasedOnDocumentRoot() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Translated line.</p></body></html>"""
        val result = TranslationReferenceImporter.parse(xhtml, "shared-document.xml", "path/ch.yml", document("原文"))
        assertEquals("Translated line.", result.translationFor("P1"))
    }

    @Test
    fun xhtmlImportPreservesBrSpacing() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>First<br/>second.</p></body></html>"""
        val result = TranslationReferenceImporter.parse(xhtml, "chapter.xhtml", "path/ch.yml", document("原文"))
        assertEquals("First second.", result.translationFor("P1"))
    }

    @Test
    fun xhtmlImportRejectsWrongChapterNumberEvenWithIdenticalStructure() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>English</p></body></html>"""
        val failure = runCatching {
            TranslationReferenceImporter.parse(xhtml, "response_ch_0017.html.xhtml", "path/ch.yml", document("原文"))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("belongs to chapter 17"))
    }

    @Test
    fun xhtmlImportRejectsDifferentBlockCounts() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>English</p><p>Extra</p></body></html>"""
        val failure = runCatching {
            TranslationReferenceImporter.parse(xhtml, "chapter.xhtml", "path/ch.yml", document("原文"))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("2 non-empty"))
        assertTrue(failure?.message.orEmpty().contains("1 entries"))
    }

    @Test
    fun xhtmlImportRejectsReorderedHeadingAndParagraph() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Body</p><h1>Title</h1></body></html>"""
        val editor = documentWithEntries("H1-1" to "題名", "P1" to "文章")
        val failure = runCatching {
            TranslationReferenceImporter.parse(xhtml, "chapter.xhtml", "path/ch.yml", editor)
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("requires <h1>"))
    }

    @Test
    fun xhtmlImportDoesNotTreatUntranslatedJapaneseAsEnglish() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>First</p><p>第二</p></body></html>"""
        val result = TranslationReferenceImporter.parse(xhtml, "chapter.xhtml", "path/ch.yml", document("第一", "第二"))
        assertEquals("XHTML verified partial", result.alignment)
        assertEquals(1, result.matchedCount)
        assertEquals("First", result.translationFor("P1"))
        assertEquals(null, result.translationFor("P2"))
    }

    @Test
    fun xhtmlImportRejectsEntirelyUntranslatedSource() {
        val xhtml = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>第一</p><p>第二</p></body></html>"""
        val failure = runCatching {
            TranslationReferenceImporter.parse(xhtml, "chapter.xhtml", "path/ch.yml", document("第一", "第二"))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("no translated blocks"))
    }

    @Test
    fun translationReferenceRouterPreservesSdlxliffImport() {
        val xliff = """<xliff xmlns="urn:oasis:names:tc:xliff:document:1.2" version="1.2"><file><body>
            <trans-unit id="1"><source>第一</source><target>First</target></trans-unit>
        </body></file></xliff>"""
        val result = TranslationReferenceImporter.parse(xliff, "chapter.sdlxliff", "path/ch.yml", document("第一"))
        assertEquals("First", result.translationFor("P1"))
    }

    private fun documentWithEntries(vararg entries: Pair<String, String>) = EditorDocument(
        schemaVersion = 6,
        volume = 1,
        chapter = 1,
        sourceHref = "canonical/vol-01/ch_0001.xhtml",
        sourceSha256 = "a".repeat(64),
        readerFile = "reader/vol-01/ch_0001.xhtml",
        englishTitle = "Test",
        instructions = "",
        editorReviewComplete = false,
        entries = entries.map { (locator, source) -> EditorEntry(locator, source, "") },
    )

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
