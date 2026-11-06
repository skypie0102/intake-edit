package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface DraftRepository {
    suspend fun load(settings: RepoSettings, path: String): LocalDraft?
    suspend fun archive(settings: RepoSettings, draft: LocalDraft)
    suspend fun archived(settings: RepoSettings, path: String): List<LocalDraft>
    suspend fun persist(settings: RepoSettings, chapter: OpenChapter)
    suspend fun delete(settings: RepoSettings, path: String)
}

internal class LocalDraftRepository(context: Context, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : DraftRepository {
    private val store = DraftStore(context)
    override suspend fun load(settings: RepoSettings, path: String): LocalDraft? = withContext(ioDispatcher) { store.load(settings, path) }
    override suspend fun archive(settings: RepoSettings, draft: LocalDraft) = withContext(ioDispatcher) {
        check(store.archive(settings, draft)) { "Could not preserve the older local draft." }
    }
    override suspend fun archived(settings: RepoSettings, path: String): List<LocalDraft> = withContext(ioDispatcher) { store.archived(settings, path) }
    override suspend fun persist(settings: RepoSettings, chapter: OpenChapter) {
        withContext(ioDispatcher) {
            if (chapter.raw == chapter.remote.content) {
                check(store.delete(settings, chapter.file.path)) { "Could not remove the local draft." }
            } else {
                check(store.save(settings, chapter.file.path, chapter.remote.sha, chapter.raw)) { "Could not save the local draft." }
            }
        }
    }
    override suspend fun delete(settings: RepoSettings, path: String) {
        withContext(ioDispatcher) { store.delete(settings, path) }
    }
}
