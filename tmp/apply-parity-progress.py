from pathlib import Path

path = Path('app/src/main/java/cloud/shadowmonarchbooks/intakeedit/MainActivity.kt')
text = path.read_text(encoding='utf-8')

needle = '''private enum class EntryFilter(val label: String) {
    NEEDS_ATTENTION("Needs Attention"), ALL("All"), RESTRICTED("Restricted"), REVISED("Revised"), UNREVISED("Unrevised")
}
'''
replacement = needle + '''
private data class ChapterProgress(
    val restrictedSupplied: Int,
    val restrictedTotal: Int,
    val safeRevised: Int,
    val editorReviewComplete: Boolean,
    val qaActive: Int = 0,
    val qaTotal: Int = 0,
) {
    val complete: Boolean get() = editorReviewComplete && restrictedSupplied == restrictedTotal
}
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''    var files by remember { mutableStateOf<List<ChapterFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
'''
replacement = '''    var files by remember { mutableStateOf<List<ChapterFile>>(emptyList()) }
    var progressByPath by remember { mutableStateOf<Map<String, ChapterProgress>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''    suspend fun loadQa(client: GitHubApi, file: ChapterFile): QaFindingsSnapshot? {
        val path = QaFindingsParser.path(file.volume, file.chapter)
        val snapshot = client.getFileOrNull(path) ?: return null
        return QaFindingsSnapshot(path, snapshot.sha, snapshot.content, QaFindingsParser.parse(snapshot.content))
    }
'''
replacement = needle + '''
    suspend fun loadChapterProgress(client: GitHubApi, file: ChapterFile): ChapterProgress {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val qa = runCatching { loadQa(client, file) }.getOrNull()
        val editorSha = QaFindingsParser.sha256(remote.content)
        return ChapterProgress(
            restrictedSupplied = document.restrictedSupplied,
            restrictedTotal = document.restrictedTotal,
            safeRevised = document.safeRevised,
            editorReviewComplete = document.editorReviewComplete,
            qaActive = qa?.document?.active(editorSha)?.size ?: 0,
            qaTotal = qa?.document?.findings?.size ?: 0,
        )
    }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''            try { files = client.listIntakeFiles(); notice = null }
            catch (t: Throwable) { notice = t.message ?: "Could not refresh chapter list." }
'''
replacement = '''            try {
                val listed = client.listIntakeFiles()
                files = listed
                progressByPath = emptyMap()
                listed.forEach { file ->
                    launch {
                        runCatching { loadChapterProgress(client, file) }
                            .onSuccess { progress -> progressByPath = progressByPath + (file.path to progress) }
                    }
                }
                notice = null
            }
            catch (t: Throwable) { notice = t.message ?: "Could not refresh chapter list." }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''                        open = next.copy(remote = remote, raw = raw, document = document)
                        notice = "Committed to ${settings.owner}/${settings.repo}."
'''
replacement = '''                        notice = "Committed to ${settings.owner}/${settings.repo}."
                        open = null
                        refresh()
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''        ChapterListScreen(files, busy, notice, ::refresh, { showSettings = true }, ::openFile)
'''
replacement = '''        ChapterListScreen(files, progressByPath, busy, notice, ::refresh, { showSettings = true }, ::openFile)
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''private fun ChapterListScreen(
    files: List<ChapterFile>, busy: Boolean, notice: String?, onRefresh: () -> Unit,
    onSettings: () -> Unit, onOpen: (ChapterFile) -> Unit,
) {
'''
replacement = '''private fun ChapterListScreen(
    files: List<ChapterFile>, progressByPath: Map<String, ChapterProgress>, busy: Boolean, notice: String?, onRefresh: () -> Unit,
    onSettings: () -> Unit, onOpen: (ChapterFile) -> Unit,
) {
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''    val visible = files.filter { val q = search.trim(); q.isBlank() || it.path.contains(q, true) || it.chapter.toString().contains(q) }
'''
replacement = '''    val searched = files.filter { val q = search.trim(); q.isBlank() || it.path.contains(q, true) || it.chapter.toString().contains(q) }
    val visible = searched.filter { file ->
        val progress = progressByPath[file.path]
        when (filter) {
            ChapterListFilter.ACTIVE -> progress?.complete != true
            ChapterListFilter.COMPLETED -> progress?.complete == true
            ChapterListFilter.ALL -> true
        }
    }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

needle = '''                            Column(Modifier.weight(1f)) {
                                Text("Volume ${file.volume} • Chapter ${file.chapter}", fontWeight = FontWeight.Bold)
                                Text(file.path, style = MaterialTheme.typography.bodySmall)
                            }
'''
replacement = '''                            Column(Modifier.weight(1f)) {
                                val progress = progressByPath[file.path]
                                Text("Volume ${file.volume} • Chapter ${file.chapter}", fontWeight = FontWeight.Bold)
                                Text(file.path, style = MaterialTheme.typography.bodySmall)
                                if (progress == null) {
                                    Text("English supplied: loading", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("English supplied: ${progress.restrictedSupplied}/${progress.restrictedTotal}", style = MaterialTheme.typography.bodySmall)
                                    Text("Safe revisions: ${progress.safeRevised} • Review: ${if (progress.editorReviewComplete) "complete" else "pending"}", style = MaterialTheme.typography.bodySmall)
                                    if (progress.qaTotal > 0) Text("QA: ${progress.qaActive} active / ${progress.qaTotal} total", style = MaterialTheme.typography.bodySmall)
                                }
                            }
'''
assert needle in text
text = text.replace(needle, replacement, 1)

path.write_text(text, encoding='utf-8')
print('Applied progress/homepage parity patch')
