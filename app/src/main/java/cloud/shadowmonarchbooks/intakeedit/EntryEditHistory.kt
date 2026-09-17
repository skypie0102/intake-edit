package cloud.shadowmonarchbooks.intakeedit

internal data class EntryEditSnapshot(
    val english: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val endnotes: List<EndnoteDefinition>,
    val proposalStatuses: Map<String, String>,
)

internal enum class EntryEditKind { INPUT, COMMAND }

internal data class EntryEditHistory(
    val undoStack: List<EntryEditSnapshot> = emptyList(),
    val redoStack: List<EntryEditSnapshot> = emptyList(),
    val lastInputAtMs: Long? = null,
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun recordBefore(
        snapshot: EntryEditSnapshot,
        kind: EntryEditKind,
        nowMs: Long,
    ): EntryEditHistory {
        val coalesce = kind == EntryEditKind.INPUT &&
            lastInputAtMs != null &&
            nowMs - lastInputAtMs in 0..INPUT_COALESCE_MS &&
            undoStack.isNotEmpty()
        val nextUndo = if (coalesce) undoStack else (undoStack + snapshot).takeLast(MAX_HISTORY)
        return copy(
            undoStack = nextUndo,
            redoStack = emptyList(),
            lastInputAtMs = if (kind == EntryEditKind.INPUT) nowMs else null,
        )
    }

    fun undo(current: EntryEditSnapshot): EntryEditStep? {
        val target = undoStack.lastOrNull() ?: return null
        return EntryEditStep(
            history = copy(
                undoStack = undoStack.dropLast(1),
                redoStack = (redoStack + current).takeLast(MAX_HISTORY),
                lastInputAtMs = null,
            ),
            snapshot = target,
        )
    }

    fun redo(current: EntryEditSnapshot): EntryEditStep? {
        val target = redoStack.lastOrNull() ?: return null
        return EntryEditStep(
            history = copy(
                undoStack = (undoStack + current).takeLast(MAX_HISTORY),
                redoStack = redoStack.dropLast(1),
                lastInputAtMs = null,
            ),
            snapshot = target,
        )
    }

    companion object {
        private const val INPUT_COALESCE_MS = 900L
        private const val MAX_HISTORY = 100
    }
}

internal data class EntryEditStep(
    val history: EntryEditHistory,
    val snapshot: EntryEditSnapshot,
)
