package cloud.shadowmonarchbooks.intakeedit

import java.time.Instant

internal data class LoadedChapterProgress(
    val progress: ChapterProgress,
    val editorContentSha256: String,
)

internal data class QaProgressCounts(
    val active: Int,
    val total: Int,
)

internal interface ChapterRepository {
    suspend fun listFiles(): List<ChapterFile>
    suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress
    suspend fun loadQaCounts(file: ChapterFile, editorContentSha256: String): QaProgressCounts
    suspend fun loadChapter(file: ChapterFile): OpenChapter
    fun restoreRaw(base: OpenChapter, raw: String): OpenChapter
    suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean)
    suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter
}

internal fun interface ChapterRepositoryFactory {
    fun create(settings: RepoSettings, token: String): ChapterRepository
}

internal object DefaultChapterRepositoryFactory : ChapterRepositoryFactory {
    override fun create(settings: RepoSettings, token: String): ChapterRepository =
        GitHubChapterRepository(settings, token)
}

internal class GitHubChapterRepository(
    settings: RepoSettings,
    token: String,
) : ChapterRepository {
    private val client = GitHubApi(settings, token)

    override suspend fun listFiles(): List<ChapterFile> = client.listIntakeFiles()

    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        return LoadedChapterProgress(
            progress = ChapterProgress(
                englishSupplied = document.englishSupplied,
                englishTotal = document.englishTotal,
                editorReviewComplete = document.editorReviewComplete,
            ),
            editorContentSha256 = QaFindingsParser.sha256(remote.content),
        )
    }

    override suspend fun loadQaCounts(file: ChapterFile, editorContentSha256: String): QaProgressCounts {
        val qa = loadQa(file) ?: return QaProgressCounts(active = 0, total = 0)
        return QaProgressCounts(
            active = qa.document.active(editorContentSha256).size,
            total = qa.document.findings.size,
        )
    }

    override suspend fun loadChapter(file: ChapterFile): OpenChapter {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val qa = runCatching { loadQa(file) }.getOrNull()
        return OpenChapter(file, remote, remote.content, document, qa)
    }

    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter =
        base.copy(raw = raw, document = IntakeParser.parse(raw))

    override suspend fun commitChapter(chapter: OpenChapter, markReviewed: Boolean) {
        val document = if (markReviewed) {
            require(chapter.document.englishSupplied == chapter.document.englishTotal) {
                "Supply English for every paragraph before marking editor review complete."
            }
            chapter.document.copy(editorReviewComplete = true)
        } else chapter.document
        val raw = IntakeParser.patchDocument(chapter.raw, document)
        IntakeParser.validate(raw).getOrThrow()
        client.updateFile(
            chapter.file.path,
            chapter.remote.sha,
            raw,
            "edit: revise ch_${chapter.file.chapter.toString().padStart(4, '0')} English",
        )
    }

    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter {
        val snapshot = requireNotNull(chapter.qa) { "No QA findings are loaded for this chapter." }
        val override = QaOverride(
            reason,
            Instant.now().toString(),
            client.verifyUser(),
            QaFindingsParser.sha256(chapter.raw),
        )
        val document = snapshot.document.withOverride(findingId, override)
        val raw = QaFindingsParser.serialize(document)
        val sha = client.updateFile(snapshot.path, snapshot.sha, raw, "qa: override $findingId")
        return chapter.copy(
            qa = snapshot.copy(
                sha = sha.ifBlank { snapshot.sha },
                raw = raw,
                document = document,
            ),
        )
    }

    private suspend fun loadQa(file: ChapterFile): QaFindingsSnapshot? {
        val path = QaFindingsParser.path(file.volume, file.chapter)
        val snapshot = client.getFileOrNull(path) ?: return null
        return QaFindingsSnapshot(path, snapshot.sha, snapshot.content, QaFindingsParser.parse(snapshot.content))
    }
}
