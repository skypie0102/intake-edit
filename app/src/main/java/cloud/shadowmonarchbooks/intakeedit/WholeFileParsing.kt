package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Wait for typing to settle, then parse away from the UI thread. Invalid drafts remain editable. */
internal suspend fun parseWholeFileDraft(
    raw: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    parse: (String) -> EditorDocument = IntakeParser::parse,
): EditorDocument? {
    delay(250)
    return withContext(dispatcher) { runCatching { parse(raw) }.getOrNull() }
}
