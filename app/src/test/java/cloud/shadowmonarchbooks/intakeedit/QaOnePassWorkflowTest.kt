package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaOnePassWorkflowTest {
    private fun editor(p1: String = "One", p2: String = "Two", p3: String = "Three") = EditorDocument(
        schemaVersion = 6,
        volume = 1,
        chapter = 1,
        sourceHref = "canonical/vol-01/ch_0001.xhtml",
        sourceSha256 = "source-sha",
        readerFile = "reader/vol-01/ch_0001.xhtml",
        englishTitle = "Chapter",
        instructions = "",
        editorReviewComplete = false,
        endnotes = emptyList(),
        entries = listOf(
            EditorEntry("P1", "一", p1),
            EditorEntry("P2", "二", p2),
            EditorEntry("P3", "三", p3),
        ),
    )

    private fun paragraphPass(base: EditorDocument): QaFindingsDocument {
        val f1 = QaFinding(
            id = "f1",
            locator = "P1",
            category = "test",
            severity = "warning",
            message = "Fix P1",
            overridable = true,
            baselineContentSha256 = QaFindingsParser.findingContentSha256(base, "P1"),
        )
        val f2 = QaFinding(
            id = "f2",
            locator = "P2",
            category = "test",
            severity = "error",
            message = "Fix P2",
            overridable = true,
            baselineContentSha256 = QaFindingsParser.findingContentSha256(base, "P2"),
        )
        return QaFindingsDocument(
            schemaVersion = 1,
            volume = 1,
            chapter = 1,
            qaPass = QaPass(
                completedAt = "2026-09-17T00:00:00Z",
                editorContentSha256 = "a".repeat(64),
                protectedContentSha256 = QaFindingsParser.protectedContentSha256(base, setOf("P1", "P2")),
                mutableLocators = listOf("P1", "P2"),
                findingIds = listOf("f1", "f2"),
            ),
            findings = listOf(f1, f2),
        )
    }

    @Test
    fun protectedFingerprintMatchesPythonWorkflowCanonicalization() {
        assertEquals(
            "4a8b9caef8e56274ca6527754dae9eccc6313e7a74ed29069ad0b927563669cc",
            QaFindingsParser.protectedContentSha256(editor(), setOf("P1", "P2")),
        )
    }

    @Test
    fun flaggedParagraphMayChangeWithoutInvalidatingPass() {
        val base = editor()
        val qa = paragraphPass(base)

        assertTrue(qa.qaPassReusable(base))
        assertTrue(qa.qaPassReusable(editor(p1 = "One fixed")))
        assertFalse(qa.qaPassReusable(editor(p3 = "Unflagged edit")))
    }

    @Test
    fun resolveRequiresActualChangeAndIsScopedToItsParagraph() {
        val base = editor()
        val qa = paragraphPass(base)
        val fixed = editor(p1 = "One fixed")

        assertFalse(qa.canResolve(qa.findings.first(), base))
        assertTrue(qa.canResolve(qa.findings.first(), fixed))

        val resolved = qa.withResolution(
            "f1",
            QaResolution(
                resolvedAt = "2026-09-17T01:00:00Z",
                resolvedBy = "editor",
                contentSha256 = QaFindingsParser.findingContentSha256(fixed, "P1"),
            ),
        )
        assertEquals(QaFindingDisposition.RESOLVED, resolved.disposition(resolved.findings.first(), fixed, "editor-sha"))
        assertEquals(
            QaFindingDisposition.RESOLVED,
            resolved.disposition(resolved.findings.first(), editor(p1 = "One fixed", p2 = "Two fixed"), "editor-sha"),
        )
        assertEquals(
            QaFindingDisposition.ACTIVE,
            resolved.disposition(resolved.findings.first(), editor(p1 = "One changed again"), "editor-sha"),
        )
    }

    @Test
    fun paragraphScopedOverrideSurvivesOtherFlaggedParagraphEdits() {
        val base = editor()
        val qa = paragraphPass(base)
        val overridden = qa.withOverride(
            "f1",
            QaOverride(
                reason = "Intentional",
                overriddenAt = "2026-09-17T01:00:00Z",
                overriddenBy = "editor",
                editorContentSha256 = "legacy-sha",
                contentSha256 = QaFindingsParser.findingContentSha256(base, "P1"),
            ),
        )

        assertEquals(
            QaFindingDisposition.OVERRIDDEN,
            overridden.disposition(overridden.findings.first(), editor(p2 = "Two fixed"), "different-editor-sha"),
        )
        assertEquals(
            QaFindingDisposition.ACTIVE,
            overridden.disposition(overridden.findings.first(), editor(p1 = "One changed"), "different-editor-sha"),
        )
    }

    @Test
    fun chapterWideFindingAllowsChapterParagraphFixesWithinSamePass() {
        val base = editor()
        val finding = QaFinding(
            id = "chapter-wide",
            locator = "",
            category = "test",
            severity = "warning",
            message = "Chapter-wide issue",
            overridable = true,
            baselineContentSha256 = QaFindingsParser.findingContentSha256(base, ""),
        )
        val allLocators = base.entries.map { it.locator }.sorted()
        val qa = QaFindingsDocument(
            schemaVersion = 1,
            volume = 1,
            chapter = 1,
            qaPass = QaPass(
                completedAt = "2026-09-17T00:00:00Z",
                editorContentSha256 = "a".repeat(64),
                protectedContentSha256 = QaFindingsParser.protectedContentSha256(base, allLocators.toSet()),
                mutableLocators = allLocators,
                findingIds = listOf("chapter-wide"),
            ),
            findings = listOf(finding),
        )
        val changed = editor(p1 = "One fixed")

        assertTrue(qa.qaPassReusable(changed))
        assertTrue(qa.canResolve(finding, changed))
    }
}
