package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryEditHistoryTest {
    private fun snapshot(
        english: String,
        noteContent: String? = null,
        proposalStatus: String = "pending",
    ) = EntryEditSnapshot(
        english = english,
        selectionStart = english.length,
        selectionEnd = english.length,
        endnotes = noteContent?.let { listOf(EndnoteDefinition("en-P1-01", "P1", it)) }.orEmpty(),
        proposalStatuses = mapOf("proposal-1" to proposalStatus),
    )

    @Test
    fun rapidInputCoalescesIntoOneUndoStep() {
        val initial = snapshot("a")
        val afterFirst = snapshot("ab")
        val afterSecond = snapshot("abc")

        var history = EntryEditHistory()
        history = history.recordBefore(initial, EntryEditKind.INPUT, 1_000)
        history = history.recordBefore(afterFirst, EntryEditKind.INPUT, 1_500)

        assertEquals(1, history.undoStack.size)
        val step = history.undo(afterSecond)!!
        assertEquals(initial, step.snapshot)
        assertTrue(step.history.canRedo)
    }

    @Test
    fun inputAfterPauseStartsAnotherUndoStep() {
        val initial = snapshot("a")
        val afterFirst = snapshot("ab")
        val afterSecond = snapshot("abc")

        var history = EntryEditHistory()
        history = history.recordBefore(initial, EntryEditKind.INPUT, 1_000)
        history = history.recordBefore(afterFirst, EntryEditKind.INPUT, 2_000)

        val firstUndo = history.undo(afterSecond)!!
        assertEquals(afterFirst, firstUndo.snapshot)
        val secondUndo = firstUndo.history.undo(afterFirst)!!
        assertEquals(initial, secondUndo.snapshot)
    }

    @Test
    fun commandsAlwaysCreateDiscreteUndoSteps() {
        val initial = snapshot("plain")
        val bold = snapshot("[b]plain[/b]")
        val italic = snapshot("[b][i]plain[/i][/b]")

        var history = EntryEditHistory()
        history = history.recordBefore(initial, EntryEditKind.COMMAND, 1_000)
        history = history.recordBefore(bold, EntryEditKind.COMMAND, 1_001)

        val firstUndo = history.undo(italic)!!
        assertEquals(bold, firstUndo.snapshot)
        val secondUndo = firstUndo.history.undo(bold)!!
        assertEquals(initial, secondUndo.snapshot)
    }

    @Test
    fun undoAndRedoCarryEndnotesAndProposalStatus() {
        val pending = snapshot("Tsureshon Men", proposalStatus = "pending")
        val accepted = snapshot(
            "[en=en-P1-01]Tsureshon Men[/en]",
            noteContent = "A cultural note.",
            proposalStatus = "accepted",
        )

        var history = EntryEditHistory().recordBefore(pending, EntryEditKind.COMMAND, 1_000)
        val undo = history.undo(accepted)!!
        history = undo.history

        assertEquals("pending", undo.snapshot.proposalStatuses.getValue("proposal-1"))
        assertTrue(undo.snapshot.endnotes.isEmpty())
        assertTrue(history.canRedo)

        val redo = history.redo(pending)!!
        assertEquals("accepted", redo.snapshot.proposalStatuses.getValue("proposal-1"))
        assertEquals("A cultural note.", redo.snapshot.endnotes.single().content)
        assertFalse(redo.history.canRedo)
    }
}
