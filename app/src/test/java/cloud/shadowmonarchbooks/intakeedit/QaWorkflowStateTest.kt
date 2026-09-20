package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QaWorkflowStateTest {
    @Test
    fun onlyApprovedChapterCountsAsComplete() {
        val pendingReview = ChapterProgress(
            englishSupplied = 10,
            englishTotal = 10,
            editorReviewComplete = false,
        )
        val pendingQa = ChapterProgress(
            englishSupplied = 10,
            englishTotal = 10,
            editorReviewComplete = true,
        )
        val ready = ChapterProgress(
            englishSupplied = 10,
            englishTotal = 10,
            editorReviewComplete = true,
            qaActive = 0,
            qaTotal = 0,
            qaReusable = true,
        )
        val approved = ChapterProgress(
            englishSupplied = 10,
            englishTotal = 10,
            editorReviewComplete = true,
            approved = true,
            qaReusable = true,
        )

        assertEquals(ChapterWorkflowState.PENDING_REVIEW, pendingReview.workflowState)
        assertEquals(ChapterWorkflowState.PENDING_QA, pendingQa.workflowState)
        assertEquals(ChapterWorkflowState.READY_FOR_APPROVAL, ready.workflowState)
        assertEquals(ChapterWorkflowState.APPROVED, approved.workflowState)
        assertFalse(pendingReview.complete)
        assertFalse(pendingQa.complete)
        assertFalse(ready.complete)
        assertTrue(approved.complete)
    }

    @Test
    fun qaContentHashIgnoresOnlyTagBit() {
        val pendingReview = """schema_version: 6
editor_review_complete: false
entries:
- locator: P1
  source_japanese: '原文'
  english: 'English'
"""
        val pendingQa = pendingReview.replace(
            "editor_review_complete: false",
            "editor_review_complete: true",
        )
        val edited = pendingReview.replace("english: 'English'", "english: 'Changed'")

        assertEquals(
            QaFindingsParser.editorContentSha256(pendingReview),
            QaFindingsParser.editorContentSha256(pendingQa),
        )
        assertNotEquals(
            QaFindingsParser.editorContentSha256(pendingReview),
            QaFindingsParser.editorContentSha256(edited),
        )
    }

    @Test
    fun approvalParserUsesSchemaV2ContentHashes() {
        val raw = """{
  "schema_version": 2,
  "volume": 1,
  "chapter": 7,
  "editor_content_sha256": "editor-hash",
  "reader_content_sha256": "reader-hash"
}
"""
        val approval = ApprovalParser.parse(raw)

        assertEquals(2, approval.schemaVersion)
        assertEquals(1, approval.volume)
        assertEquals(7, approval.chapter)
        assertEquals("editor-hash", approval.editorContentSha256)
        assertEquals("reader-hash", approval.readerContentSha256)
        assertEquals("approvals/vol-01/ch_0007.approved.json", ApprovalParser.path(1, 7))
    }
}
