package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openRestoresDraftWhenBaseShaMatchesRemote() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository(
            loaded = LocalDraft(remote.file.path, "sha-1", "draft raw", 1L),
        )
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )

        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()

        assertEquals("draft raw", viewModel.chapterForDisplay()?.raw)
        assertEquals("draft raw", chapterRepository.restoredRaw)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun staleDraftDoesNotReplaceRemoteAndShowsNotice() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "sha-new")
        val draftRepository = FakeDraftRepository(
            loaded = LocalDraft(remote.file.path, "sha-old", "stale draft", 1L),
        )
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )

        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()

        assertEquals("remote raw", viewModel.chapterForDisplay()?.raw)
        assertNull(chapterRepository.restoredRaw)
        assertEquals(
            "A local draft exists for an older GitHub revision. The latest remote copy was opened to avoid an unsafe overwrite.",
            viewModel.uiState.value.notice,
        )
    }

    @Test
    fun successfulReviewedCommitReturnsToChapterList() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )
        val event = async { viewModel.events.first() }

        viewModel.commit(remote, true, RepoSettings(), "token")
        advanceUntilIdle()

        assertSame(EditorEvent.ReturnChapterList, event.await())
        assertNull(viewModel.chapterForDisplay())
        assertEquals(listOf(remote.file.path), draftRepository.deleted)
        assertEquals(remote, chapterRepository.committedChapter)
    }

    @Test
    fun commitWithoutReviewStaysInEditorWithFreshRemoteSha() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )
        val event = async { viewModel.events.first() }

        viewModel.commit(remote, false, RepoSettings(), "token")
        advanceUntilIdle()

        assertSame(EditorEvent.RefreshChapterList, event.await())
        val stillOpen = requireNotNull(viewModel.chapterForDisplay())
        assertEquals("sha-committed", stillOpen.remote.sha)
        assertEquals("edited raw", stillOpen.raw)
        assertEquals("Committed as Pending Review.", viewModel.uiState.value.notice)
        assertEquals(listOf(remote.file.path), draftRepository.deleted)
        assertEquals(remote, chapterRepository.committedChapter)
        assertEquals(false, chapterRepository.committedMarkReviewed)
    }

    @Test
    fun closeEditorFlushesLatestDraftBeforeDebounceFires() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )

        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()
        val edited = remote.copy(raw = "edited raw")

        viewModel.onDraft(edited)
        viewModel.closeEditor()
        advanceUntilIdle()

        assertSame(edited, draftRepository.persisted.single())
        assertNull(viewModel.uiState.value.open)
    }
}

private class FakeDraftRepository(
    private val loaded: LocalDraft? = null,
) : DraftRepository {
    val persisted = mutableListOf<OpenChapter>()
    val deleted = mutableListOf<String>()

    override suspend fun load(path: String): LocalDraft? = loaded

    override suspend fun persist(chapter: OpenChapter) {
        persisted += chapter
    }

    override suspend fun delete(path: String) {
        deleted += path
    }
}

private class FakeEditorChapterRepository(
    private val remote: OpenChapter,
) : ChapterRepository {
    var restoredRaw: String? = null
        private set
    var committedChapter: OpenChapter? = null
        private set
    var committedMarkReviewed: Boolean? = null
        private set

    override suspend fun listVolumes(): List<Int> = error("Not used in this test")
    override suspend fun listFiles(volume: Int): List<ChapterFile> = error("Not used in this test")
    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress = error("Not used in this test")
    override suspend fun loadQaCounts(file: ChapterFile, document: EditorDocument, editorContentSha256: String): QaProgressCounts = error("Not used in this test")
    override suspend fun loadChapter(file: ChapterFile): OpenChapter = remote

    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter {
        restoredRaw = raw
        return base.copy(raw = raw)
    }

    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): ChapterCommitResult {
        committedChapter = chapter
        committedMarkReviewed = markReviewed
        return ChapterCommitResult(
            remote = chapter.remote.copy(sha = "sha-committed", content = chapter.raw),
            endnoteProposals = chapter.endnoteProposals,
        )
    }

    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter = error("Not used in this test")
    override suspend fun resolveQa(chapter: OpenChapter, findingId: String): OpenChapter = error("Not used in this test")
}

private fun sampleChapter(raw: String, sha: String): OpenChapter {
    val file = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
    return OpenChapter(
        file = file,
        remote = FileSnapshot(file.path, sha, "remote raw"),
        raw = raw,
        document = EditorDocument(
            schemaVersion = 5,
            volume = 1,
            chapter = 1,
            sourceHref = "source.xhtml",
            sourceSha256 = "source-sha",
            readerFile = "chapter.html",
            englishTitle = "Chapter 1",
            instructions = "",
            editorReviewComplete = false,
            entries = listOf(EditorEntry("p-1", "raw", "")),
        ),
        qa = null,
    )
}
