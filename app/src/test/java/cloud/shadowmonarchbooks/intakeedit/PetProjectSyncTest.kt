package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PetProjectSyncTest {
    @Test fun keysSeparateRepositoriesBranchesAndTokens() {
        val original = RepoSettings()
        val key = petProjectKey(original, "token-a")
        assertNotEquals(key, petProjectKey(original.copy(repo = "another"), "token-a"))
        assertNotEquals(key, petProjectKey(original.copy(branch = "other"), "token-a"))
        assertNotEquals(key, petProjectKey(original, "token-b"))
        assertEquals(key, petProjectKey(original, "token-a"))
    }

    @Test fun countsIndexedAndDirtyChaptersOnceAcrossVolumes() = runTest {
        val file1 = ChapterFile("a", 1, 1)
        val file2 = ChapterFile("b", 1, 2)
        val file3 = ChapterFile("c", 2, 1)
        val done = ChapterProgress(1, 1, true, approved = true)
        val notDone = ChapterProgress(1, 1, true, approved = false)
        val indexes = mapOf(
            1 to IndexedVolumeStatus("sha", listOf(file1, file2), mapOf("a" to done, "b" to done), listOf(file2)),
            2 to IndexedVolumeStatus("sha", listOf(file3), mapOf("c" to notDone)),
        )
        val repository = FakePetRepository(indexes, mapOf("b" to notDone))
        assertEquals(1, countProjectCompletions(repository, listOf(1, 2, 1)))
        assertEquals(listOf("b"), repository.loaded)
    }

    @Test(expected = IllegalStateException::class)
    fun missingIndexDoesNotPretendItsChaptersAreIncomplete() = runTest {
        countProjectCompletions(FakePetRepository(emptyMap(), emptyMap()), listOf(1))
    }

    @Test fun widgetChoosesFirstPendingReviewChapterWithMissingEnglish() = runTest {
        val chapter1 = ChapterFile("a", 1, 1)
        val chapter2 = ChapterFile("b", 1, 2)
        val chapter3 = ChapterFile("c", 1, 3)
        val approved = ChapterProgress(1, 1, true, approved = true)
        val needsQa = ChapterProgress(1, 1, true)
        val needsTranslation = ChapterProgress(0, 1, false)
        val index = IndexedVolumeStatus("sha", listOf(chapter1, chapter2, chapter3),
            mapOf("a" to approved, "b" to needsQa, "c" to needsTranslation))
        assertEquals(chapter3, findNextTranslationChapter(FakePetRepository(mapOf(1 to index), emptyMap())))
    }

    @Test fun widgetReloadsStaleIndexEntriesBeforeChoosing() = runTest {
        val chapter1 = ChapterFile("a", 1, 1)
        val chapter2 = ChapterFile("b", 1, 2)
        val pending = ChapterProgress(0, 1, false)
        val approved = ChapterProgress(1, 1, true, approved = true)
        val index = IndexedVolumeStatus("sha", listOf(chapter1, chapter2),
            mapOf("a" to pending, "b" to pending), listOf(chapter1))
        val repository = FakePetRepository(mapOf(1 to index), mapOf("a" to approved))
        assertEquals(chapter2, findNextTranslationChapter(repository))
        assertEquals(listOf("a"), repository.loaded)
    }
}

private class FakePetRepository(
    val indexes: Map<Int, IndexedVolumeStatus>,
    val dirtyProgress: Map<String, ChapterProgress>,
) : ChapterRepository {
    val loaded = mutableListOf<String>()
    override suspend fun listVolumes(): List<Int> = indexes.keys.toList()
    override suspend fun loadVolumeStatus(volume: Int): IndexedVolumeStatus? = indexes[volume]
    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress {
        loaded += file.path
        return LoadedChapterProgress(dirtyProgress.getValue(file.path), "", EditorDocument(
            schemaVersion = 6, volume = file.volume, chapter = file.chapter, sourceHref = "", sourceSha256 = "",
            readerFile = "", englishTitle = "", instructions = "", editorReviewComplete = false, entries = emptyList()))
    }
    override suspend fun listFiles(volume: Int): List<ChapterFile> = error("unused")
    override suspend fun loadQaCounts(file: ChapterFile, document: EditorDocument, editorContentSha256: String): QaProgressCounts = error("unused")
    override suspend fun loadChapter(file: ChapterFile): OpenChapter = error("unused")
    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter = error("unused")
    override suspend fun commitChapter(chapter: OpenChapter, tagForQa: Boolean): ChapterCommitResult = error("unused")
    override suspend fun approveChapter(chapter: OpenChapter): ApprovalDispatch = error("unused")
    override suspend fun waitForApproval(dispatch: ApprovalDispatch): GitHubWorkflowRun = error("unused")
    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter = error("unused")
    override suspend fun resolveQa(chapter: OpenChapter, findingId: String): OpenChapter = error("unused")
}
