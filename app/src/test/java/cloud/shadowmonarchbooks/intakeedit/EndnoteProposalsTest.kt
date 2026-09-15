package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndnoteProposalsTest {
    private val raw = """
        {
          "schema_version": 1,
          "proposals": [
            {
              "id": "endnote-v01-c0012-p7-001",
              "volume": 1,
              "chapter": 12,
              "locator": "P7",
              "source_anchor": "コミケ",
              "suggested_english_anchor": "Comiket",
              "suggested_content": "A concise cultural note.",
              "reason": "Cultural context for English readers.",
              "status": "pending"
            },
            {
              "id": "endnote-v01-c0013-p2-001",
              "volume": 1,
              "chapter": 13,
              "locator": "P2",
              "source_anchor": "例",
              "suggested_english_anchor": "example",
              "suggested_content": "Another note.",
              "reason": "Context.",
              "status": "pending"
            }
          ]
        }
    """.trimIndent() + "\n"

    @Test
    fun pendingForFiltersByChapterAndLocator() {
        val document = EndnoteProposalParser.parse(raw)

        val pending = document.pendingFor(volume = 1, chapter = 12, locator = "P7")

        assertEquals(1, pending.size)
        assertEquals("endnote-v01-c0012-p7-001", pending.single().id)
    }

    @Test
    fun visibleForKeepsAcceptedSuggestionsAndHidesRejectedOnes() {
        val document = EndnoteProposalParser.parse(raw)
            .withStatus("endnote-v01-c0012-p7-001", "accepted")
            .withStatus("endnote-v01-c0013-p2-001", "rejected")

        assertEquals(1, document.visibleFor(1, 12, "P7").size)
        assertEquals("accepted", document.visibleFor(1, 12, "P7").single().status)
        assertTrue(document.pendingFor(1, 12, "P7").isEmpty())
        assertTrue(document.visibleFor(1, 13, "P2").isEmpty())
    }

    @Test
    fun stageAcceptsProposalAndMarksSnapshotChanged() {
        val snapshot = EndnoteProposalSnapshot(
            path = EndnoteProposalParser.PATH,
            remote = FileSnapshot(EndnoteProposalParser.PATH, "sha-1", raw),
            raw = raw,
            document = EndnoteProposalParser.parse(raw),
        )
        assertFalse(snapshot.changed)

        val staged = EndnoteProposalParser.stage(snapshot, "endnote-v01-c0012-p7-001", "accepted")

        assertTrue(staged.changed)
        assertEquals(
            "accepted",
            staged.document.proposals.single { it.id == "endnote-v01-c0012-p7-001" }.status,
        )
        assertTrue(staged.document.pendingFor(1, 12, "P7").isEmpty())
        assertEquals(1, staged.document.visibleFor(1, 12, "P7").size)
        assertEquals(staged.document, EndnoteProposalParser.parse(staged.raw))
    }

    @Test
    fun stageRejectsProposalWithoutChangingOtherPendingItems() {
        val snapshot = EndnoteProposalSnapshot(
            path = EndnoteProposalParser.PATH,
            remote = FileSnapshot(EndnoteProposalParser.PATH, "sha-1", raw),
            raw = raw,
            document = EndnoteProposalParser.parse(raw),
        )

        val staged = EndnoteProposalParser.stage(snapshot, "endnote-v01-c0012-p7-001", "rejected")

        assertEquals(
            "rejected",
            staged.document.proposals.single { it.id == "endnote-v01-c0012-p7-001" }.status,
        )
        assertTrue(staged.document.visibleFor(1, 12, "P7").isEmpty())
        assertEquals(1, staged.document.pendingFor(1, 13, "P2").size)
    }
}
