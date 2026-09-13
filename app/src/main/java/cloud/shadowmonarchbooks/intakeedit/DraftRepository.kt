package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface DraftRepository {
    suspend fun load(path: String): LocalDraft?
    suspend fun persist(chapter: OpenChapter)
    suspend fun delete(path: String)
}

internal class LocalDraftRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DraftRepository {
    private val store = DraftStore(context)

    override suspend fun load(path: String): LocalDraft? = withContext(ioDispatcher) {
        store.load(path)
    }

    override suspend fun persist(chapter: OpenChapter) {
        withContext(ioDispatcher) {
            if (chapter.raw == chapter.remote.content) {
                store.delete(chapter.file.path)
            } else {
                store.save(chapter.file.path, chapter.remote.sha, chapter.raw)
            }
        }
    }

    override suspend fun delete(path: String) {
        withContext(ioDispatcher) {
            store.delete(path)
        }
    }
}
