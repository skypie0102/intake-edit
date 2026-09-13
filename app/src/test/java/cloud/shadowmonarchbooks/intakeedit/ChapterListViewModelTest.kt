package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val progress = mapOf(
            first.path to ChapterProgress(4, 5, false),
            second.path to ChapterProgress(8, 8, true, qaActive = 1, qaTotal = 2),
        )
        val repository = FakeChapterRepository(listOf(first, second), progress)
        val viewModel = ChapterListViewModel(ChapterRepositoryFactory { _, _ -> repository })

        viewModel.refresh(RepoSettings(), "token")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(first, second), state.files)
        assertEquals(progress, state.progressByPath)
        assertFalse(state.refreshing)
        assertNull(state.notice)
    }

    @Test
    fun refreshSurfacesRepositoryFailure() = runTest(dispatcher) {
        val viewModel = ChapterListViewModel(
            ChapterRepositoryFactory { _, _ -> ThrowingChapterRepository("network unavailable") },
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
) : ChapterRepository {
    override suspend fun listFiles(): List<ChapterFile> = files

    override suspend fun loadProgress(file: ChapterFile): ChapterProgress =
        requireNotNull(progress[file.path])

    override suspend fun loadChapter(file: ChapterFile): OpenChapter = error("Not used in this test")
    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter = error("Not used in this test")
    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) = error("Not used in this test")
    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter = error("Not used in this test")
}

private class ThrowingChapterRepository(private val message: String) : ChapterRepository {
    override suspend fun listFiles(): List<ChapterFile> = error(message)
    override suspend fun loadProgress(file: ChapterFile): ChapterProgress = error("Not used in this test")
    override suspend fun loadChapter(file: ChapterFile): OpenChapter = error("Not used in this test")
    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter = error("Not used in this test")
    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) = error("Not used in this test")
    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter = error("Not used in this test")
}
