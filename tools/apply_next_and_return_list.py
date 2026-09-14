from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    (ROOT / path).write_text(text)


# EditorScreen: remove floating Next control and restore bottom-bar Next button.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt'
s = read(path)
old_fab = '''        floatingActionButton = {\n            if (missingEnglish.isNotEmpty() && !busy) {\n                SmallFloatingActionButton(\n                    onClick = { jumpToFirstMissing() },\n                    modifier = Modifier.padding(bottom = 52.dp),\n                ) {\n                    BadgedBox(badge = { Badge { Text(missingEnglish.size.toString()) } }) {\n                        Icon(Icons.Default.SkipNext, "Go to first unsupplied English field")\n                    }\n                }\n            }\n        },\n'''
if old_fab not in s:
    raise AssertionError('floating Next block not found')
s = s.replace(old_fab, '', 1)
old_row = '''            ) {\n                if (imported == null) {\n'''
new_row = '''            ) {\n                TextButton(\n                    onClick = { jumpToFirstMissing() },\n                    enabled = !busy && missingEnglish.isNotEmpty(),\n                ) {\n                    Text("Next (${missingEnglish.size.toString().padStart(2, '0')})")\n                }\n                if (imported == null) {\n'''
if old_row not in s:
    raise AssertionError('editor bottom row marker not found')
s = s.replace(old_row, new_row, 1)
s = s.replace('import androidx.compose.material.icons.filled.SkipNext\n', '')
s = s.replace('import androidx.compose.material3.SmallFloatingActionButton\n', '')
write(path, s)


# Editor event: successful commit returns to the chapter list, not workspace home.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorViewModel.kt'
s = read(path)
if 'data object ReturnHome : EditorEvent' not in s:
    raise AssertionError('ReturnHome event not found')
s = s.replace('data object ReturnHome : EditorEvent', 'data object ReturnChapterList : EditorEvent', 1)
if 'eventChannel.send(EditorEvent.ReturnHome)' not in s:
    raise AssertionError('ReturnHome send not found')
s = s.replace('eventChannel.send(EditorEvent.ReturnHome)', 'eventChannel.send(EditorEvent.ReturnChapterList)', 1)
write(path, s)


# App shell: stay in Chapter Intake and refresh list on successful commit.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt'
s = read(path)
old = '''    LaunchedEffect(editorViewModel) {\n        editorViewModel.events.collect { event ->\n            if (event == EditorEvent.ReturnHome) onExitToHome()\n        }\n    }\n'''
new = '''    LaunchedEffect(editorViewModel) {\n        editorViewModel.events.collect { event ->\n            if (event == EditorEvent.ReturnChapterList) refresh()\n        }\n    }\n'''
if old not in s:
    raise AssertionError('editor event collector not found')
s = s.replace(old, new, 1)
write(path, s)


# Regression test successful commit closes editor and emits list-return event.
path = 'app/src/test/java/cloud/shadowmonarchbooks/intakeedit/EditorViewModelTest.kt'
s = read(path)
if 'import kotlinx.coroutines.async\n' not in s:
    s = s.replace('import kotlinx.coroutines.Dispatchers\n', 'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.async\n')
if 'import kotlinx.coroutines.flow.first\n' not in s:
    s = s.replace('import kotlinx.coroutines.ExperimentalCoroutinesApi\n', 'import kotlinx.coroutines.ExperimentalCoroutinesApi\nimport kotlinx.coroutines.flow.first\n')
marker = '''    @Test\n    fun closeEditorFlushesLatestDraftBeforeDebounceFires() = runTest(dispatcher) {\n'''
if marker not in s:
    raise AssertionError('test insertion marker not found')
new_test = '''    @Test\n    fun successfulCommitReturnsToChapterList() = runTest(dispatcher) {\n        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")\n        val draftRepository = FakeDraftRepository()\n        val chapterRepository = FakeEditorChapterRepository(remote)\n        val viewModel = EditorViewModel(\n            draftRepository = draftRepository,\n            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },\n        )\n        val event = async { viewModel.events.first() }\n\n        viewModel.commit(remote, false, RepoSettings(), "token")\n        advanceUntilIdle()\n\n        assertSame(EditorEvent.ReturnChapterList, event.await())\n        assertNull(viewModel.chapterForDisplay())\n        assertEquals(listOf(remote.file.path), draftRepository.deleted)\n        assertEquals(remote, chapterRepository.committedChapter)\n    }\n\n'''
s = s.replace(marker, new_test + marker, 1)
old_fake = '''private class FakeEditorChapterRepository(\n    private val remote: OpenChapter,\n) : ChapterRepository {\n    var restoredRaw: String? = null\n        private set\n'''
new_fake = '''private class FakeEditorChapterRepository(\n    private val remote: OpenChapter,\n) : ChapterRepository {\n    var restoredRaw: String? = null\n        private set\n    var committedChapter: OpenChapter? = null\n        private set\n'''
if old_fake not in s:
    raise AssertionError('fake repository marker not found')
s = s.replace(old_fake, new_fake, 1)
old_commit = '    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) = error("Not used in this test")\n'
new_commit = '''    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) {\n        committedChapter = chapter\n    }\n'''
if old_commit not in s:
    raise AssertionError('fake commit method not found')
s = s.replace(old_commit, new_commit, 1)
write(path, s)


# Patch release.
path = 'app/build.gradle.kts'
s = read(path)
if 'versionCode = 23' not in s or 'versionName = "0.8.0"' not in s:
    raise AssertionError('expected 0.8.0 version not found')
s = s.replace('versionCode = 23', 'versionCode = 24', 1)
s = s.replace('versionName = "0.8.0"', 'versionName = "0.8.1"', 1)
write(path, s)
