package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterListViewModelTest {
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
    fun refreshPublishesFilesAndProgressFromRepository() = runTest(dispatcher) {
        val first = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        val second = ChapterFile("editor_input/vol-01/chapters/ch_0002.yml", 1, 2)
        val baseProgress = mapOf(
            first.path to ChapterProgress(4, 5, false),
            second.path to ChapterProgress(8, 8, true),
        )
        val qaProgress = mapOf(
            first.path to QaProgressCounts(0, 0),
            second.path to QaProgressCounts(1, 2, reusable = true),
        )
        val repository = FakeChapterRepository(listOf(first, second), baseProgress, qaProgress)
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository },
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(first, second), state.files)
        assertEquals(listOf(1), state.availableVolumes)
        assertEquals(1, state.selectedVolume)
        assertEquals(listOf(1), repository.requestedVolumes)
        assertEquals(ChapterProgress(4, 5, false), state.progressByPath[first.path])
        assertEquals(ChapterProgress(8, 8, true, qaActive = 1, qaTotal = 2, qaReusable = true), state.progressByPath[second.path])
        assertFalse(state.refreshing)
        assertNull(state.notice)
    }

    @Test
    fun workflowStateWaitsForQaBeforePublishing() = runTest(dispatcher) {
        val file = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        val qaGate = CompletableDeferred<Unit>()
        val repository = FakeChapterRepository(
            files = listOf(file),
            progress = mapOf(file.path to ChapterProgress(3, 5, false)),
            qaProgress = mapOf(file.path to QaProgressCounts(2, 4)),
            beforeQa = { qaGate.await() },
        )
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository },
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        runCurrent()

        var state = viewModel.uiState.value
        assertEquals(listOf(file), state.files)
        assertNull(state.progressByPath[file.path])
        assertTrue(state.refreshing)

        qaGate.complete(Unit)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(ChapterProgress(3, 5, false, qaActive = 2, qaTotal = 4), state.progressByPath[file.path])
        assertFalse(state.refreshing)
    }

    @Test
    fun cachedProgressAppearsBeforeRemoteListCompletesAndIsRefreshed() = runTest(dispatcher) {
        val file = ChapterFile("editor_input/vol-01/chapters/ch_0003.yml", 1, 3)
        val cachedProgress = ChapterProgress(1, 6, false, qaActive = 3, qaTotal = 3)
        val freshBase = ChapterProgress(4, 6, false)
        val remoteGate = CompletableDeferred<Unit>()
        val cache = FakeChapterProgressCacheStore(
            ChapterListCacheSnapshot(
                files = listOf(file),
                progressByPath = mapOf(file.path to cachedProgress),
            ),
        )
        val repository = FakeChapterRepository(
            files = listOf(file),
            progress = mapOf(file.path to freshBase),
            qaProgress = mapOf(file.path to QaProgressCounts(1, 2)),
            beforeList = { remoteGate.await() },
        )
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository },
            cacheStore = cache,
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        runCurrent()

        var state = viewModel.uiState.value
        assertEquals(listOf(file), state.files)
        assertEquals(cachedProgress, state.progressByPath[file.path])
        assertEquals(1, state.selectedVolume)
        assertTrue(state.refreshing)

        remoteGate.complete(Unit)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertFalse(state.refreshing)
        assertEquals(ChapterProgress(4, 6, false, qaActive = 1, qaTotal = 2), state.progressByPath[file.path])
        assertEquals(state.files, cache.saved?.files)
        assertEquals(state.progressByPath, cache.saved?.progressByPath)
    }

    @Test
    fun refreshDefaultsToLatestVolumeAndSelectionFetchesOnlyChosenVolume() = runTest(dispatcher) {
        val volumeOne = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        val volumeTwo = ChapterFile("editor_input/vol-02/chapters/ch_0001.yml", 2, 1)
        val repository = FakeChapterRepository(
            files = listOf(volumeOne, volumeTwo),
            progress = mapOf(
                volumeOne.path to ChapterProgress(1, 2, false),
                volumeTwo.path to ChapterProgress(2, 2, true),
            ),
            qaProgress = mapOf(
                volumeOne.path to QaProgressCounts(0, 0),
                volumeTwo.path to QaProgressCounts(0, 0),
            ),
        )
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository },
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(listOf(1, 2), state.availableVolumes)
        assertEquals(2, state.selectedVolume)
        assertEquals(listOf(volumeTwo), state.files)
        assertEquals(listOf(2), repository.requestedVolumes)

        viewModel.selectVolume(1, RepoSettings(), "token")
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.selectedVolume)
        assertEquals(listOf(volumeOne), state.files)
        assertEquals(listOf(2, 1), repository.requestedVolumes)
    }

    @Test
    fun approveAllReadyWaitsForEachWorkflowBeforeDispatchingNext() = runTest(dispatcher) {
        val readyOne = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        val readyTwo = ChapterFile("editor_input/vol-01/chapters/ch_0002.yml", 1, 2)
        val pendingQa = ChapterFile("editor_input/vol-01/chapters/ch_0003.yml", 1, 3)
        val repository = FakeChapterRepository(
            files = listOf(readyOne, readyTwo, pendingQa),
            progress = mapOf(
                readyOne.path to ChapterProgress(5, 5, true),
                readyTwo.path to ChapterProgress(5, 5, true),
                pendingQa.path to ChapterProgress(5, 5, true),
            ),
            qaProgress = mapOf(
                readyOne.path to QaProgressCounts(active = 0, total = 2, reusable = true),
                readyTwo.path to QaProgressCounts(active = 0, total = 1, reusable = true),
                pendingQa.path to QaProgressCounts(active = 1, total = 1, reusable = true),
            ),
        )
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository },
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        viewModel.approveAllReady(RepoSettings(), "token")
        advanceUntilIdle()

        assertEquals(
            listOf("dispatch:1", "wait:1", "dispatch:2", "wait:2"),
            repository.approvalEvents,
        )
        assertEquals(emptySet<String>(), viewModel.uiState.value.approvalRequestedPaths)
        assertEquals(ChapterWorkflowState.APPROVED, viewModel.uiState.value.progressByPath[readyOne.path]?.workflowState)
        assertEquals(ChapterWorkflowState.APPROVED, viewModel.uiState.value.progressByPath[readyTwo.path]?.workflowState)
        assertEquals(ChapterWorkflowState.PENDING_QA, viewModel.uiState.value.progressByPath[pendingQa.path]?.workflowState)
        assertFalse(viewModel.uiState.value.bulkApproving)
        assertEquals("Approved 2 chapters sequentially.", viewModel.uiState.value.notice)
    }

    @Test
    fun indexedRefreshAvoidsPerChapterRequests() = runTest(dispatcher) {
        val first = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        val second = ChapterFile("editor_input/vol-01/chapters/ch_0002.yml", 1, 2)
        val firstProgress = ChapterProgress(5, 5, true, qaActive = 0, qaTotal = 2, qaReusable = true)
        val secondProgress = ChapterProgress(3, 5, false)
        val repository = FakeChapterRepository(
            files = listOf(first, second),
            progress = emptyMap(),
            qaProgress = emptyMap(),
            indexedStatus = IndexedVolumeStatus(
                sourceCommitSha = "a".repeat(40),
                files = listOf(first, second),
                progressByPath = mapOf(first.path to firstProgress, second.path to secondProgress),
            ),
        )
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> repository },
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(first, second), state.files)
        assertEquals(firstProgress, state.progressByPath[first.path])
        assertEquals(secondProgress, state.progressByPath[second.path])
        assertTrue(repository.requestedVolumes.isEmpty())
        assertFalse(state.refreshing)
    }

    @Test
    fun refreshSurfacesRepositoryFailure() = runTest(dispatcher) {
        val viewModel = ChapterListViewModel(
            repositoryFactory = ChapterRepositoryFactory { _, _ -> ThrowingChapterRepository("network unavailable") },
            cacheDispatcher = dispatcher,
        )

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("network unavailable", state.notice)
        assertFalse(state.refreshing)
    }
}

private class FakeChapterRepository(
    private val files: List<ChapterFile>,
    private val progress: Map<String, ChapterProgress>,
    private val qaProgress: Map<String, QaProgressCounts>,
    private val beforeList: suspend () -> Unit = {},
    private val beforeQa: suspend () -> Unit = {},
    private val indexedStatus: IndexedVolumeStatus? = null,
) : ChapterRepository {
    val requestedVolumes = mutableListOf<Int>()
    val approvalEvents = mutableListOf<String>()

    override suspend fun listVolumes(): List<Int> = files.map { it.volume }.distinct().sorted()
    override suspend fun loadVolumeStatus(volume: Int): IndexedVolumeStatus? = indexedStatus?.takeIf { it.files.firstOrNull()?.volume == volume }

    override suspend fun listFiles(volume: Int): List<ChapterFile> {
        requestedVolumes += volume
        beforeList()
        return files.filter { it.volume == volume }
    }

    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress =
        LoadedChapterProgress(
            progress = requireNotNull(progress[file.path]),
            editorContentSha256 = "editor-sha-${file.chapter}",
            document = EditorDocument(
                schemaVersion = 6, volume = file.volume, chapter = file.chapter,
                sourceHref = "source.xhtml", sourceSha256 = "source-sha", readerFile = "reader.xhtml",
                englishTitle = "Chapter ${file.chapter}", instructions = "", editorReviewComplete = false, entries = emptyList(),
            ),
        )

    override suspend fun loadQaCounts(file: ChapterFile, document: EditorDocument, editorContentSha256: String): QaProgressCounts {
        beforeQa()
        return requireNotNull(qaProgress[file.path])
    }

    override suspend fun loadChapter(file: ChapterFile): OpenChapter {
        val document = EditorDocument(
            schemaVersion = 6,
            volume = file.volume,
            chapter = file.chapter,
            sourceHref = "source.xhtml",
            sourceSha256 = "source-sha",
            readerFile = "reader.xhtml",
            englishTitle = "Chapter ${file.chapter}",
            instructions = "",
            editorReviewComplete = true,
            entries = listOf(EditorEntry("P1", "一", "One")),
        )
        return OpenChapter(
            file = file,
            remote = FileSnapshot(file.path, "sha-${file.chapter}", "raw"),
            raw = "raw",
            document = document,
            qa = null,
        )
    }
    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter = error("Not used in this test")
    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) = error("Not used in this test")
    override suspend fun approveChapter(chapter: OpenChapter): ApprovalDispatch {
        approvalEvents += "dispatch:${chapter.file.chapter}"
        return ApprovalDispatch(
            requestId = "request-${chapter.file.chapter}",
            workflowRunTitle = "run-${chapter.file.chapter}",
        )
    }

    override suspend fun waitForApproval(dispatch: ApprovalDispatch): GitHubWorkflowRun {
        val chapter = dispatch.workflowRunTitle.removePrefix("run-").toInt()
        approvalEvents += "wait:$chapter"
        return GitHubWorkflowRun(
            id = chapter.toLong(),
            displayTitle = dispatch.workflowRunTitle,
            status = "completed",
            conclusion = "success",
            htmlUrl = null,
        )
    }

    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter = error("Not used in this test")
    override suspend fun resolveQa(chapter: OpenChapter, findingId: String): OpenChapter = error("Not used in this test")
}

private class ThrowingChapterRepository(private val message: String) : ChapterRepository {
    override suspend fun listVolumes(): List<Int> = error(message)
    override suspend fun loadVolumeStatus(volume: Int): IndexedVolumeStatus? = error("Not used in this test")
    override suspend fun listFiles(volume: Int): List<ChapterFile> = error("Not used in this test")
    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress = error("Not used in this test")
    override suspend fun loadQaCounts(file: ChapterFile, document: EditorDocument, editorContentSha256: String): QaProgressCounts = error("Not used in this test")
    override suspend fun loadChapter(file: ChapterFile): OpenChapter = error("Not used in this test")
    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter = error("Not used in this test")
    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) = error("Not used in this test")
    override suspend fun approveChapter(chapter: OpenChapter): ApprovalDispatch = error("Not used in this test")
    override suspend fun waitForApproval(dispatch: ApprovalDispatch): GitHubWorkflowRun = error("Not used in this test")
    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter = error("Not used in this test")
    override suspend fun resolveQa(chapter: OpenChapter, findingId: String): OpenChapter = error("Not used in this test")
}

private class FakeChapterProgressCacheStore(
    private val initial: ChapterListCacheSnapshot?,
) : ChapterProgressCacheStore {
    var saved: ChapterListCacheSnapshot? = null
        private set

    override fun load(settings: RepoSettings): ChapterListCacheSnapshot? = initial

    override fun save(settings: RepoSettings, snapshot: ChapterListCacheSnapshot) {
        saved = snapshot
    }
}
