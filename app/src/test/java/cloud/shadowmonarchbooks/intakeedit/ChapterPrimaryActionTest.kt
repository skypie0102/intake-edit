package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPrimaryActionTest {
    private fun document(supplied: Int, total: Int = 480, tagged: Boolean = false): EditorDocument =
        EditorDocument(
            schemaVersion = 6,
            volume = 1,
            chapter = 17,
            sourceHref = "canonical/vol-01/ch_0017.xhtml",
            sourceSha256 = "a".repeat(64),
            readerFile = "reader/vol-01/ch_0017.xhtml",
            englishTitle = "Test Chapter",
            instructions = "",
            editorReviewComplete = tagged,
            entries = (1..total).map { position ->
                EditorEntry("P$position", "日本語$position", if (position <= supplied) "English $position" else "")
            },
        )

    private fun action(
        supplied: Int = 151,
        tagged: Boolean = false,
        dirty: Boolean = true,
        reusable: Boolean = false,
        active: Int = 0,
        qaRecorded: Boolean = false,
        approved: Boolean = false,
        hasStagedDecisions: Boolean = false,
    ) = resolveChapterPrimaryAction(
        document = document(supplied, tagged = tagged),
        approved = approved,
        hasLocalChanges = dirty || hasStagedDecisions,
        qaPassReusable = reusable,
        activeQaFindings = active,
        hasRecordedQaPass = qaRecorded,
        hasStagedEndnoteDecisions = hasStagedDecisions,
    )

    @Test fun partialChapterOffersOnlyPendingReviewCommit() {
        val current = action()
        assertEquals(ChapterPrimaryAction.COMMIT, current.action)
        assertEquals("Commit", current.label)
        assertEquals(false, current.action.tagForQa)
        assertTrue(current.enabled)
        assertFalse(current.canCommitAsPendingReview(true))
        assertTrue(current.confirmationMessage.orEmpty().contains("151 of 480"))
    }

    @Test fun noChangesDisablesPendingReviewCommit() {
        val current = action(dirty = false)
        assertEquals(ChapterPrimaryAction.COMMIT, current.action)
        assertFalse(current.enabled)
    }

    @Test fun lastEnglishEntryFlipsButtonWithoutAutomaticallyTagging() {
        val before = action(supplied = 479)
        val after = action(supplied = 480)
        assertEquals(ChapterPrimaryAction.COMMIT, before.action)
        assertEquals(ChapterPrimaryAction.TAG_FOR_QA, after.action)
        assertEquals(true, after.action.tagForQa)
        assertTrue(after.enabled)
        assertTrue(after.canCommitAsPendingReview(hasLocalChanges = true))
        assertTrue(after.confirmationMessage.orEmpty().contains("Confirm you have reviewed"))
    }

    @Test fun removingEnglishRevertsToCommitEvenAfterComplete() {
        val current = action(supplied = 479, tagged = true, reusable = false, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.COMMIT, current.action)
        assertEquals(false, current.action.tagForQa)
    }

    @Test fun completePendingReviewCanTagEvenWithoutOtherChanges() {
        val current = action(supplied = 480, dirty = false)
        assertEquals(ChapterPrimaryAction.TAG_FOR_QA, current.action)
        assertTrue(current.enabled)
        assertFalse(current.canCommitAsPendingReview(hasLocalChanges = false))
    }

    @Test fun completeAlreadyTaggedUnchangedChapterAwaitsQa() {
        val current = action(supplied = 480, tagged = true, dirty = false)
        assertEquals(ChapterPrimaryAction.AWAITING_QA, current.action)
        assertFalse(current.enabled)
    }

    @Test fun recordedStaleQaPassRequiresFreshQaWhenComplete() {
        val current = action(supplied = 480, tagged = false, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.TAG_FOR_FRESH_QA, current.action)
        assertEquals(true, current.action.tagForQa)
        assertTrue(current.confirmationMessage.orEmpty().contains("stale"))
    }

    @Test fun unresolvedReusableQaPassShowsReviewNotRetag() {
        val current = action(supplied = 480, tagged = true, reusable = true, active = 2, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.REVIEW_QA_FINDINGS, current.action)
        assertEquals(null, current.action.tagForQa)
        assertTrue(current.enabled)
        assertTrue(current.canCommitAsPendingReview(hasLocalChanges = true))
    }

    @Test fun noChangesAndUnresolvedQaStillShowsReviewAction() {
        val current = action(supplied = 480, tagged = true, dirty = false, reusable = true, active = 1, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.REVIEW_QA_FINDINGS, current.action)
        assertTrue(current.enabled)
        assertFalse(current.canCommitAsPendingReview(hasLocalChanges = false))
    }

    @Test fun resolvedReusableQaPassCanCommitForApproval() {
        val current = action(supplied = 480, tagged = false, reusable = true, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.COMMIT_FOR_APPROVAL, current.action)
        assertTrue(current.enabled)
        assertEquals(true, current.action.tagForQa)
    }

    @Test fun readyForApprovalNeedsNoChangesAndAllFindingsClosed() {
        val current = action(supplied = 480, tagged = true, dirty = false, reusable = true, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.APPROVE_CHAPTER, current.action)
        assertTrue(current.enabled)
        assertFalse(current.canCommitAsPendingReview(hasLocalChanges = false))
    }

    @Test fun stagedEndnoteDecisionsBlockDirectApprovalUntilCommitted() {
        val current = action(
            supplied = 480, tagged = true, dirty = false, reusable = true,
            qaRecorded = true, hasStagedDecisions = true,
        )
        assertEquals(ChapterPrimaryAction.COMMIT_FOR_APPROVAL, current.action)
        assertTrue(current.confirmationMessage.orEmpty().contains("Staged endnote"))
    }

    @Test fun approvedChapterDisablesActionRegardlessOfOtherState() {
        val current = action(supplied = 480, approved = true, reusable = true, qaRecorded = true)
        assertEquals(ChapterPrimaryAction.APPROVED, current.action)
        assertFalse(current.enabled)
    }

    @Test fun invalidEmptyChapterDoesNotAppearFullyTranslated() {
        val current = resolveChapterPrimaryAction(
            document = document(supplied = 0, total = 0),
            approved = false,
            hasLocalChanges = false,
            qaPassReusable = false,
            activeQaFindings = 0,
            hasRecordedQaPass = false,
        )
        assertEquals(ChapterPrimaryAction.COMMIT, current.action)
        assertFalse(current.enabled)
    }

    @Test fun onlySuppliedEditorEnglishCountsNotImportReferences() {
        // A translation imported as a reference never alters document.entries.
        val doc = document(supplied = 151)
        val reference = ImportedTranslationOverlay(
            chapterPath = "test.yml", sourceSha256 = doc.sourceSha256,
            fileName = "ch_0017.xhtml", translations = mapOf("P480" to "Rough"),
            matchedCount = 1, totalEntries = 480, sourceParagraphs = 0,
            targetParagraphs = 480, alignment = "XHTML verified partial",
        )
        assertEquals("Rough", reference.translationFor("P480"))
        val current = resolveChapterPrimaryAction(
            document = doc, approved = false, hasLocalChanges = true,
            qaPassReusable = false, activeQaFindings = 0, hasRecordedQaPass = false,
        )
        assertEquals(ChapterPrimaryAction.COMMIT, current.action)
    }
}
