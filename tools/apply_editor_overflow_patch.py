from pathlib import Path

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    (ROOT / path).write_text(text)

# Editor UI: move Whole file and Fill/Remove all into overflow and remove the dedicated fill row.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt'
s = read(path)

s = s.replace('import androidx.compose.material.icons.filled.Description\n', '')
s = s.replace('import androidx.compose.material.icons.filled.ViewAgenda\n', '')
if 'import androidx.compose.material.icons.filled.MoreVert\n' not in s:
    s = s.replace('import androidx.compose.material.icons.filled.FilterList\n', 'import androidx.compose.material.icons.filled.FilterList\nimport androidx.compose.material.icons.filled.MoreVert\n')
if 'import androidx.compose.material3.DropdownMenu\n' not in s:
    s = s.replace('import androidx.compose.material3.Card\n', 'import androidx.compose.material3.Card\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem\n')

old = '    var showFilters by rememberSaveable { mutableStateOf(false) }\n    var showCommit by remember { mutableStateOf(false) }\n'
new = '    var showFilters by rememberSaveable { mutableStateOf(false) }\n    var showOverflow by remember { mutableStateOf(false) }\n    var showCommit by remember { mutableStateOf(false) }\n'
if old not in s:
    raise AssertionError('overflow state marker not found')
s = s.replace(old, new, 1)

old = '''                    IconButton(\n                        onClick = {\n                            showWholeFile = !showWholeFile\n                            showQa = false\n                        },\n                        enabled = !busy,\n                    ) {\n                        Icon(\n                            if (showWholeFile) Icons.Default.ViewAgenda else Icons.Default.Description,\n                            if (showWholeFile) "Show cards" else "Show whole file",\n                        )\n                    }\n'''
new = '''                    IconButton(\n                        onClick = { showOverflow = true },\n                        enabled = !busy,\n                    ) {\n                        Icon(Icons.Default.MoreVert, "Editor actions")\n                    }\n                    DropdownMenu(\n                        expanded = showOverflow,\n                        onDismissRequest = { showOverflow = false },\n                    ) {\n                        DropdownMenuItem(\n                            text = { Text(if (showWholeFile) "Cards mode" else "Whole file mode") },\n                            onClick = {\n                                showOverflow = false\n                                focusManager.clearFocus(force = true)\n                                showWholeFile = !showWholeFile\n                                showQa = false\n                            },\n                            enabled = !busy,\n                        )\n                        imported?.let { overlay ->\n                            DropdownMenuItem(\n                                text = {\n                                    Text(\n                                        if (fillableImportCount > 0) "Fill blanks ($fillableImportCount)"\n                                        else "Remove all English",\n                                    )\n                                },\n                                onClick = {\n                                    showOverflow = false\n                                    focusManager.clearFocus(force = true)\n                                    if (fillableImportCount > 0) fillBlanksFromImport(overlay)\n                                    else showRemoveAllConfirm = true\n                                },\n                                enabled = !busy && !importBusy && !showWholeFile &&\n                                    (fillableImportCount > 0 || suppliedEnglishCount > 0),\n                            )\n                        }\n                    }\n'''
if old not in s:
    raise AssertionError('whole file action marker not found')
s = s.replace(old, new, 1)

old = '''                imported?.let { overlay ->\n                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n                        if (fillableImportCount > 0) {\n                            TextButton(\n                                onClick = {\n                                    focusManager.clearFocus(force = true)\n                                    fillBlanksFromImport(overlay)\n                                },\n                                enabled = !busy && !importBusy,\n                            ) { Text("Fill blanks ($fillableImportCount)") }\n                        } else {\n                            TextButton(\n                                onClick = { showRemoveAllConfirm = true },\n                                enabled = !busy && !importBusy && suppliedEnglishCount > 0,\n                            ) { Text("Remove all") }\n                        }\n                    }\n                }\n'''
if old not in s:
    raise AssertionError('fill blanks row marker not found')
s = s.replace(old, '', 1)
write(path, s)

# Repository commit returns the new GitHub blob snapshot so an in-place editor can keep committing safely.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/ChapterRepository.kt'
s = read(path)
old = '    suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean)\n'
new = '    suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): FileSnapshot\n'
if old not in s:
    raise AssertionError('repository interface marker not found')
s = s.replace(old, new, 1)
old = '''    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) {\n        val document = if (markReviewed) {\n            require(chapter.document.englishSupplied == chapter.document.englishTotal) {\n                "Supply English for every paragraph before marking editor review complete."\n            }\n            chapter.document.copy(editorReviewComplete = true)\n        } else chapter.document\n        val raw = IntakeParser.patchDocument(chapter.raw, document)\n        IntakeParser.validate(raw).getOrThrow()\n        client.updateFile(\n            chapter.file.path,\n            chapter.remote.sha,\n            raw,\n            "edit: revise ch_${chapter.file.chapter.toString().padStart(4, '0')} English",\n        )\n    }\n'''
new = '''    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): FileSnapshot {\n        val document = if (markReviewed) {\n            require(chapter.document.englishSupplied == chapter.document.englishTotal) {\n                "Supply English for every paragraph before marking editor review complete."\n            }\n            chapter.document.copy(editorReviewComplete = true)\n        } else chapter.document\n        val raw = IntakeParser.patchDocument(chapter.raw, document)\n        IntakeParser.validate(raw).getOrThrow()\n        val sha = client.updateFile(\n            chapter.file.path,\n            chapter.remote.sha,\n            raw,\n            "edit: revise ch_${chapter.file.chapter.toString().padStart(4, '0')} English",\n        )\n        return FileSnapshot(chapter.file.path, sha.ifBlank { chapter.remote.sha }, raw)\n    }\n'''
if old not in s:
    raise AssertionError('repository implementation marker not found')
s = s.replace(old, new, 1)
write(path, s)

# ViewModel: reviewed commit exits; commit-without-review keeps editor open with the new SHA.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorViewModel.kt'
s = read(path)
old = '''internal sealed interface EditorEvent {\n    data object ReturnChapterList : EditorEvent\n}\n'''
new = '''internal sealed interface EditorEvent {\n    data object RefreshChapterList : EditorEvent\n    data object ReturnChapterList : EditorEvent\n}\n'''
if old not in s:
    raise AssertionError('editor event marker not found')
s = s.replace(old, new, 1)
old = '''                val repository = repositoryFactory.create(settings, token)\n                repository.commitChapter(next, markReviewed)\n                draftRepository.delete(next.file.path)\n                latest = null\n                touched = false\n                _uiState.update { it.copy(open = null, actionInProgress = false, notice = null) }\n                eventChannel.send(EditorEvent.ReturnChapterList)\n'''
new = '''                val repository = repositoryFactory.create(settings, token)\n                val committedRemote = repository.commitChapter(next, markReviewed)\n                draftRepository.delete(next.file.path)\n                touched = false\n                if (markReviewed) {\n                    latest = null\n                    _uiState.update { it.copy(open = null, actionInProgress = false, notice = null) }\n                    eventChannel.send(EditorEvent.ReturnChapterList)\n                } else {\n                    val committed = next.copy(\n                        remote = committedRemote,\n                        raw = committedRemote.content,\n                    )\n                    latest = committed\n                    _uiState.update {\n                        it.copy(\n                            open = committed,\n                            actionInProgress = false,\n                            notice = "Committed without review.",\n                        )\n                    }\n                    eventChannel.send(EditorEvent.RefreshChapterList)\n                }\n'''
if old not in s:
    raise AssertionError('commit flow marker not found')
s = s.replace(old, new, 1)
write(path, s)

# Main shell refreshes the list for either commit event; editor visibility still comes from open state.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt'
s = read(path)
old = '''        editorViewModel.events.collect { event ->\n            if (event == EditorEvent.ReturnChapterList) refresh()\n        }\n'''
new = '''        editorViewModel.events.collect { event ->\n            when (event) {\n                EditorEvent.RefreshChapterList,\n                EditorEvent.ReturnChapterList,\n                -> refresh()\n            }\n        }\n'''
if old not in s:
    raise AssertionError('event collector marker not found')
s = s.replace(old, new, 1)
write(path, s)

# Regression tests for both navigation outcomes and fresh commit SHA retention.
path = 'app/src/test/java/cloud/shadowmonarchbooks/intakeedit/EditorViewModelTest.kt'
s = read(path)
s = s.replace('    fun successfulCommitReturnsToChapterList() = runTest(dispatcher) {', '    fun successfulReviewedCommitReturnsToChapterList() = runTest(dispatcher) {', 1)
s = s.replace('        viewModel.commit(remote, false, RepoSettings(), "token")\n', '        viewModel.commit(remote, true, RepoSettings(), "token")\n', 1)
anchor = '''    @Test\n    fun closeEditorFlushesLatestDraftBeforeDebounceFires() = runTest(dispatcher) {\n'''
insert = '''    @Test\n    fun commitWithoutReviewStaysInEditorWithFreshRemoteSha() = runTest(dispatcher) {\n        val remote = sampleChapter(raw = "edited raw", sha = "sha-1")\n        val draftRepository = FakeDraftRepository()\n        val chapterRepository = FakeEditorChapterRepository(remote)\n        val viewModel = EditorViewModel(\n            draftRepository = draftRepository,\n            repositoryFactory = ChapterRepositoryFactory { _, _ -> chapterRepository },\n        )\n        val event = async { viewModel.events.first() }\n\n        viewModel.commit(remote, false, RepoSettings(), "token")\n        advanceUntilIdle()\n\n        assertSame(EditorEvent.RefreshChapterList, event.await())\n        val stillOpen = requireNotNull(viewModel.chapterForDisplay())\n        assertEquals("sha-committed", stillOpen.remote.sha)\n        assertEquals("edited raw", stillOpen.raw)\n        assertEquals("Committed without review.", viewModel.uiState.value.notice)\n        assertEquals(listOf(remote.file.path), draftRepository.deleted)\n        assertEquals(remote, chapterRepository.committedChapter)\n        assertEquals(false, chapterRepository.committedMarkReviewed)\n    }\n\n'''
if anchor not in s:
    raise AssertionError('test insertion marker not found')
s = s.replace(anchor, insert + anchor, 1)
old = '''    var committedChapter: OpenChapter? = null\n        private set\n'''
new = '''    var committedChapter: OpenChapter? = null\n        private set\n    var committedMarkReviewed: Boolean? = null\n        private set\n'''
if old not in s:
    raise AssertionError('fake fields marker not found')
s = s.replace(old, new, 1)
old = '''    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) {\n        committedChapter = chapter\n    }\n'''
new = '''    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean): FileSnapshot {\n        committedChapter = chapter\n        committedMarkReviewed = markReviewed\n        return chapter.remote.copy(sha = "sha-committed", content = chapter.raw)\n    }\n'''
if old not in s:
    raise AssertionError('fake commit marker not found')
s = s.replace(old, new, 1)
write(path, s)

# Bump patch release.
path = 'app/build.gradle.kts'
s = read(path)
if 'versionCode = 26' not in s or 'versionName = "0.8.3"' not in s:
    raise AssertionError('expected v0.8.3 version not found')
s = s.replace('versionCode = 26', 'versionCode = 27', 1)
s = s.replace('versionName = "0.8.3"', 'versionName = "0.8.4"', 1)
write(path, s)
