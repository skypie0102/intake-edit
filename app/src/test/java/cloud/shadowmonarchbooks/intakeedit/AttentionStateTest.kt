package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionStateTest {
    private fun document() = EditorDocument(
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
            EditorEntry("P1", "一", "One"),
            EditorEntry("P2", "二", "Two"),
            EditorEntry("P3", "三", "Three"),
        ),
    )

    private fun proposal(id: String, locator: String, status: String) = EndnoteProposal(
        id = id,
        volume = 1,
        chapter = 1,
        locator = locator,
        sourceAnchor = "anchor",
        suggestedEnglishAnchor = "anchor",
        suggestedContent = "note",
        reason = "reason",
        status = status,
    )

    @Test
    fun unresolvedCountCountsItemsWhileNavigationUsesUniqueCards() {
        val doc = document()
        val resolvedHash = QaFindingsParser.findingContentSha256(doc, "P1")
        val overriddenHash = QaFindingsParser.findingContentSha256(doc, "P2")
        val qa = QaFindingsDocument(
            schemaVersion = 1,
            volume = 1,
            chapter = 1,
            qaPass = null,
            findings = listOf(
                QaFinding(
                    id = "resolved",
                    locator = "P1",
                    category = "test",
                    severity = "warning",
                    message = "resolved",
                    overridable = true,
                    resolution = QaResolution("2026-09-18T00:00:00Z", "editor", resolvedHash),
                ),
                QaFinding(
                    id = "overridden",
                    locator = "P2",
                    category = "test",
                    severity = "warning",
                    message = "overridden",
                    overridable = true,
                    override = QaOverride(
                        reason = "intentional",
                        overriddenAt = "2026-09-18T00:00:00Z",
                        overriddenBy = "editor",
                        editorContentSha256 = "legacy",
                        contentSha256 = overriddenHash,
                    ),
                ),
                QaFinding(
                    id = "active",
                    locator = "P3",
                    category = "test",
                    severity = "warning",
                    message = "active",
                    overridable = true,
                ),
            ),
        )
        val proposals = EndnoteProposalDocument(
            schemaVersion = 1,
            proposals = listOf(
                proposal("pending", "P3", "pending"),
                proposal("accepted", "P1", "accepted"),
                proposal("rejected", "P2", "rejected"),
            ),
        )

        val state = buildAttentionState(doc, qa, proposals, 1, 1, "editor-sha")

        assertEquals(2, state.unresolvedCount)
        assertEquals(listOf("P3"), state.unresolvedLocators)
        assertEquals(setOf("P1", "P2", "P3"), state.allQaByLocator.keys)
        assertEquals(setOf("P3"), state.activeQaByLocator.keys)
        assertEquals(QaFindingDisposition.RESOLVED, state.qaDispositionById.getValue("resolved"))
        assertEquals(QaFindingDisposition.OVERRIDDEN, state.qaDispositionById.getValue("overridden"))
        assertEquals(QaFindingDisposition.ACTIVE, state.qaDispositionById.getValue("active"))
    }

    @Test
    fun chapterWideActiveFindingStillCountsWithoutCardLocator() {
        val doc = document()
        val qa = QaFindingsDocument(
            schemaVersion = 1,
            volume = 1,
            chapter = 1,
            qaPass = null,
            findings = listOf(
                QaFinding(
                    id = "chapter-wide",
                    locator = "",
                    category = "test",
                    severity = "warning",
                    message = "chapter-wide",
                    overridable = true,
                ),
            ),
        )

        val state = buildAttentionState(doc, qa, null, 1, 1, "editor-sha")

        assertEquals(1, state.unresolvedCount)
        assertTrue(state.hasUnlocatedActiveQa)
        assertTrue(state.unresolvedLocators.isEmpty())
        assertFalse(state.allQaByLocator.containsKey(""))
    }
}
