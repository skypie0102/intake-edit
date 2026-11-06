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
import org.junit.Assert.assertTrue
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
            parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )

        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()

        assertEquals("draft raw", viewModel.chapterForDisplay()?.raw)
        assertEquals("draft raw", chapterRepository.restoredRaw)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun incompleteSavedDraftOpensForRepairWithoutDiscardingRawText() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "sha-1")
        val drafts = FakeDraftRepository(loaded = LocalDraft(remote.file.path, "sha-1", "broken draft", 1L))
        val repository = FakeEditorChapterRepository(remote, restoreFailure = true)
        val viewModel = EditorViewModel(draftRepository = drafts, parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository })
        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()
        val opened = requireNotNull(viewModel.chapterForDisplay())
        assertEquals("broken draft", opened.raw)
        assertEquals(remote.document, opened.document)
        assertTrue(opened.rawNeedsRepair)
        assertTrue(viewModel.uiState.value.notice.orEmpty().contains("incomplete"))
    }

    @Test
    fun failedDraftSaveKeepsEditorOpenAndCanBeRetried() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")
        val drafts = FakeDraftRepository(failPersist = true)
        val repository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(draftRepository = drafts, parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository })
        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()
        viewModel.onDraft(remote.copy(raw = "new text"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.notice.orEmpty().contains("save failed"))
        viewModel.closeEditor()
        advanceUntilIdle()
        assertEquals("new text", viewModel.chapterForDisplay()?.raw)
        assertTrue(viewModel.uiState.value.notice.orEmpty().contains("remain open"))
        drafts.failPersist = false
        viewModel.closeEditor()
        advanceUntilIdle()
        assertNull(viewModel.chapterForDisplay())
        assertEquals("new text", drafts.persisted.last().raw)
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
            parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )

        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()

        assertEquals("remote raw", viewModel.chapterForDisplay()?.raw)
        assertNull(chapterRepository.restoredRaw)
        assertEquals(listOf("stale draft"), viewModel.uiState.value.recoveryDrafts.map { it.raw })
        assertEquals(
            "An older local draft was preserved. Open Saved drafts to inspect or copy it.",
            viewModel.uiState.value.notice,
        )
    }

    @Test
    fun staleDraftArchiveFailureStopsOpeningBeforeAnyOverwrite() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "new-sha")
        val drafts = FakeDraftRepository(
            loaded = LocalDraft(remote.file.path, "old-sha", "unsaved work", 1L), failArchive = true,
        )
        val viewModel = EditorViewModel(draftRepository = drafts, parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> FakeEditorChapterRepository(remote) })
        viewModel.open(remote.file, RepoSettings(), "token")
        advanceUntilIdle()
        assertNull(viewModel.chapterForDisplay())
        assertEquals("archive failed", viewModel.uiState.value.notice)
        assertTrue(drafts.persisted.isEmpty())
    }

    @Test
    fun successfulReviewedCommitReturnsToChapterList() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val feedings = mutableListOf<ChapterFile>()
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
            onTaggedForQa = { _, file -> feedings += file },
        )
        val event = async { viewModel.events.first() }

        viewModel.commit(remote, true, RepoSettings(), "token")
        advanceUntilIdle()

        assertSame(EditorEvent.ReturnChapterList, event.await())
        assertNull(viewModel.chapterForDisplay())
        assertEquals(listOf(remote.file.path), draftRepository.deleted)
        assertEquals(remote, chapterRepository.committedChapter)
        assertEquals(listOf(remote.file), feedings)
    }

    @Test
    fun commitWithoutReviewStaysInEditorWithFreshRemoteSha() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val feedings = mutableListOf<ChapterFile>()
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
            onTaggedForQa = { _, file -> feedings += file },
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
        assertEquals(emptyList<ChapterFile>(), feedings)
    }

    @Test
    fun approveDispatchReturnsToChapterList() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            parseDispatcher = dispatcher,
            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },
        )
        val event = async { viewModel.events.first() }

        viewModel.approve(remote, RepoSettings(), "token")
        advanceUntilIdle()

        assertSame(EditorEvent.ReturnChapterList, event.await())
        assertSame(remote, chapterRepository.approvedChapter)
        assertNull(viewModel.chapterForDisplay())
        assertEquals(
            "Approval workflow started. Refresh to check when the chapter becomes Approved.",
            viewModel.uiState.value.notice,
        )
    }

    @Test
    fun closeEditorFlushesLatestDraftBeforeDebounceFires() = runTest(dispatcher) {
        val remote = sampleChapter(raw = "remote raw", sha = "sha-1")
        val draftRepository = FakeDraftRepository()
        val chapterRepository = FakeEditorChapterRepository(remote)
        val viewModel = EditorViewModel(
            draftRepository = draftRepository,
            parseDispatcher = dispatcher,
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
    var failPersist: Boolean = false,
    var failArchive: Boolean = false,
) : DraftRepository {
    val persisted = mutableListOf<OpenChapter>()
    val deleted = mutableListOf<String>()
    val savedArchives = mutableListOf<LocalDraft>()

    override suspend fun archive(draft: LocalDraft) {
        if (failArchive) error("archive failed")
        savedArchives += draft
    }

    override suspend fun archived(path: String): List<LocalDraft> = savedArchives.filter { it.path == path }

    override suspend fun load(path: String): LocalDraft? = loaded

    override suspend fun persist(chapter: OpenChapter) {
        if (failPersist) error("disk full")
        persisted += chapter
    }

    override suspend fun delete(path: String) {
        deleted += path
    }
}

private class FakeEditorChapterRepository(
    private val remote: OpenChapter,
    private val restoreFailure: Boolean = false,
) : ChapterRepository {
    var restoredRaw: String? = null
        private set
    var committedChapter: OpenChapter? = null
        private set
    var committedMarkReviewed: Boolean? = null
        private set
    var approvedChapter: OpenChapter? = null
        private set

    override suspend fun listVolumes(): List<Int> = error("Not used in this test")
    override suspend fun loadVolumeStatus(volume: Int): IndexedVolumeStatus? = error("Not used in this test")
    override suspend fun listFiles(volume: Int): List<ChapterFile> = error("Not used in this test")
    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress = error("Not used in this test")
    override suspend fun loadQaCounts(file: ChapterFile, document: EditorDocument, editorContentSha256: String): QaProgressCounts = error("Not used in this test")
    override suspend fun loadChapter(file: ChapterFile): OpenChapter = remote

    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter {
        restoredRaw = raw
        if (restoreFailure) error("malformed draft")
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

    override suspend fun approveChapter(chapter: OpenChapter): ApprovalDispatch {
        approvedChapter = chapter
        return ApprovalDispatch("request-1", "run-1")
    }
    override suspend fun waitForApproval(dispatch: ApprovalDispatch): GitHubWorkflowRun = error("Not used in this test")
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

