package cloud.shadowmonarchbooks.intakeedit

import java.time.Instant

internal data class LoadedChapterProgress(
    val progress: ChapterProgress,
    val editorContentSha256: String,
    val document: EditorDocument,
)

internal data class QaProgressCounts(
    val active: Int,
    val total: Int,
    val reusable: Boolean = false,
)

internal data class ChapterCommitResult(
    val remote: FileSnapshot,
    val endnoteProposals: EndnoteProposalSnapshot?,
)

internal interface ChapterRepository {
    suspend fun listVolumes(): List<Int>
    suspend fun listFiles(volume: Int): List<ChapterFile>
    suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress
    suspend fun loadQaCounts(file: ChapterFile, document: EditorDocument, editorContentSha256: String): QaProgressCounts
    suspend fun loadChapter(file: ChapterFile): OpenChapter
    fun restoreRaw(base: OpenChapter, raw: String): OpenChapter
    suspend fun commitChapter(chapter: OpenChapter, tagForQa: Boolean): ChapterCommitResult
    suspend fun approveChapter(chapter: OpenChapter)
    suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter
    suspend fun resolveQa(chapter: OpenChapter, findingId: String): OpenChapter
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

    override suspend fun listVolumes(): List<Int> = client.listIntakeVolumes()

    override suspend fun listFiles(volume: Int): List<ChapterFile> = client.listIntakeFiles(volume)

    override suspend fun loadBaseProgress(file: ChapterFile): LoadedChapterProgress {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val editorContentSha256 = QaFindingsParser.editorContentSha256(remote.content)
        val approved = isApprovalCurrent(file, editorContentSha256, document.readerFile)
        return LoadedChapterProgress(
            progress = ChapterProgress(
                englishSupplied = document.englishSupplied,
                englishTotal = document.englishTotal,
                editorReviewComplete = document.editorReviewComplete,
                approved = approved,
            ),
            editorContentSha256 = editorContentSha256,
            document = document,
        )
    }

    override suspend fun loadQaCounts(
        file: ChapterFile,
        document: EditorDocument,
        editorContentSha256: String,
    ): QaProgressCounts {
        val qa = loadQa(file) ?: return QaProgressCounts(active = 0, total = 0, reusable = false)
        val reusable = qa.document.qaPassReusable(document)
        return QaProgressCounts(
            active = if (reusable) qa.document.active(document, editorContentSha256).size else qa.document.findings.size,
            total = qa.document.findings.size,
            reusable = reusable,
        )
    }

    override suspend fun loadChapter(file: ChapterFile): OpenChapter {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val editorContentSha256 = QaFindingsParser.editorContentSha256(remote.content)
        val qa = runCatching { loadQa(file) }.getOrNull()
        val endnoteProposals = loadEndnoteProposals()
        return OpenChapter(
            file = file,
            remote = remote,
            raw = remote.content,
            document = document,
            qa = qa,
            endnoteProposals = endnoteProposals,
            approved = isApprovalCurrent(file, editorContentSha256, document.readerFile),
        )
    }

    override fun restoreRaw(base: OpenChapter, raw: String): OpenChapter =
        base.copy(raw = raw, document = IntakeParser.parse(raw), approved = false)

    override suspend fun commitChapter(chapter: OpenChapter, tagForQa: Boolean): ChapterCommitResult {
        val currentEditorSha = QaFindingsParser.editorContentSha256(chapter.raw)
        val currentQa = chapter.qa?.document
        val reusablePass = currentQa?.qaPassReusable(chapter.document) == true
        val activeFindings = if (reusablePass && currentQa != null) {
            currentQa.active(chapter.document, currentEditorSha)
        } else emptyList()

        if (tagForQa && reusablePass) {
            require(activeFindings.isEmpty()) {
                "Close all active QA findings with Resolve or Override before finalizing this QA pass."
            }
        }

        val document = if (tagForQa) {
            require(chapter.document.englishSupplied == chapter.document.englishTotal) {
                "Supply English for every paragraph before tagging the chapter for QA."
            }
            chapter.document.copy(editorReviewComplete = true)
        } else {
            chapter.document.copy(editorReviewComplete = false)
        }
        val raw = IntakeParser.patchDocument(chapter.raw, document)
        IntakeParser.validate(raw).getOrThrow()
        val message = when {
            tagForQa && reusablePass -> "edit: finalize ch_${chapter.file.chapter.toString().padStart(4, '0')} for approval"
            tagForQa -> "edit: tag ch_${chapter.file.chapter.toString().padStart(4, '0')} for QA"
            else -> "edit: revise ch_${chapter.file.chapter.toString().padStart(4, '0')} English"
        }
        val proposals = chapter.endnoteProposals
        val approval = client.getFileOrNull(ApprovalParser.path(chapter.file.volume, chapter.file.chapter))
        val updates = mutableListOf(
            GitHubFileUpdate(
                path = chapter.file.path,
                expectedSha = chapter.remote.sha,
                content = raw,
            ),
        )
        if (proposals?.changed == true) {
            updates += GitHubFileUpdate(
                path = proposals.path,
                expectedSha = proposals.remote.sha,
                content = proposals.raw,
            )
        }
        val deletions = approval?.let { listOf(GitHubFileDeletion(it.path, it.sha)) }.orEmpty()

        if (updates.size > 1 || deletions.isNotEmpty()) {
            client.updateFilesAtomically(
                updates = updates,
                deletions = deletions,
                message = if (proposals?.changed == true) "$message and endnote suggestions" else message,
            )
            val committedChapter = client.getFile(chapter.file.path)
            val committedProposals = if (proposals?.changed == true) {
                val committedProposalRemote = client.getFile(proposals.path)
                EndnoteProposalSnapshot(
                    path = proposals.path,
                    remote = committedProposalRemote,
                    raw = committedProposalRemote.content,
                    document = EndnoteProposalParser.parse(committedProposalRemote.content),
                )
            } else proposals
            return ChapterCommitResult(committedChapter, committedProposals)
        }

        val sha = client.updateFile(
            chapter.file.path,
            chapter.remote.sha,
            raw,
            message,
        )
        return ChapterCommitResult(
            remote = FileSnapshot(chapter.file.path, sha.ifBlank { chapter.remote.sha }, raw),
            endnoteProposals = proposals,
        )
    }

    override suspend fun approveChapter(chapter: OpenChapter) {
        require(!chapter.approved) { "This chapter is already approved." }
        require(chapter.raw == chapter.remote.content && chapter.endnoteProposals?.changed != true) {
            "Commit all local chapter and endnote-suggestion changes before approval."
        }
        require(chapter.document.editorReviewComplete) {
            "This chapter is not Ready for Approval."
        }
        require(chapter.document.englishSupplied == chapter.document.englishTotal) {
            "Supply English for every entry before approval."
        }

        val latestEditor = client.getFile(chapter.file.path)
        require(latestEditor.sha == chapter.remote.sha) {
            "This chapter changed on GitHub after it was opened. Refresh before approving."
        }
        val latestDocument = IntakeParser.parse(latestEditor.content)
        val latestEditorHash = QaFindingsParser.editorContentSha256(latestEditor.content)
        val latestQa = loadQa(chapter.file)
            ?: error("No completed semantic QA pass is recorded for this chapter.")
        require(latestQa.document.qaPassReusable(latestDocument)) {
            "The semantic QA pass is stale. Tag the chapter for fresh QA before approving."
        }
        require(latestQa.document.active(latestDocument, latestEditorHash).isEmpty()) {
            "Active QA findings remain. Resolve or Override every finding before approving."
        }

        client.dispatchWorkflow(
            workflowFileName = "finalize-chapter.yml",
            inputs = mapOf(
                "editor_path" to chapter.file.path,
                "expected_editor_content_sha256" to latestEditorHash,
            ),
        )
    }

    override suspend fun overrideQa(chapter: OpenChapter, findingId: String, reason: String): OpenChapter {
        val snapshot = requireNotNull(chapter.qa) { "No QA findings are loaded for this chapter." }
        require(snapshot.document.qaPassReusable(chapter.document)) {
            "The previous semantic QA pass is stale. Tag this chapter for a fresh QA run."
        }
        val finding = snapshot.document.findings.firstOrNull { it.id == findingId }
            ?: error("QA finding not found: $findingId")
        require(finding.overridable) { "This QA finding cannot be overridden." }
        require(reason.isNotBlank()) { "Override reason is required." }
        val editorSha = QaFindingsParser.editorContentSha256(chapter.raw)
        require(snapshot.document.disposition(finding, chapter.document, editorSha) == QaFindingDisposition.ACTIVE) {
            "This QA finding is already closed."
        }
        val override = QaOverride(
            reason = reason.trim(),
            overriddenAt = Instant.now().toString(),
            overriddenBy = client.verifyUser(),
            editorContentSha256 = editorSha,
            contentSha256 = QaFindingsParser.findingContentSha256(chapter.document, finding.locator),
        )
        val document = snapshot.document.withOverride(findingId, override)
        return commitQaSnapshot(chapter, snapshot, document, "qa: override $findingId")
    }

    override suspend fun resolveQa(chapter: OpenChapter, findingId: String): OpenChapter {
        val snapshot = requireNotNull(chapter.qa) { "No QA findings are loaded for this chapter." }
        require(snapshot.document.qaPassReusable(chapter.document)) {
            "The previous semantic QA pass is stale. Tag this chapter for a fresh QA run."
        }
        val finding = snapshot.document.findings.firstOrNull { it.id == findingId }
            ?: error("QA finding not found: $findingId")
        val editorSha = QaFindingsParser.editorContentSha256(chapter.raw)
        require(snapshot.document.disposition(finding, chapter.document, editorSha) == QaFindingDisposition.ACTIVE) {
            "This QA finding is already closed."
        }
        require(snapshot.document.canResolve(finding, chapter.document)) {
            val scope = finding.locator.ifBlank { "chapter" }
            "Edit $scope before resolving this finding."
        }
        val resolution = QaResolution(
            resolvedAt = Instant.now().toString(),
            resolvedBy = client.verifyUser(),
            contentSha256 = QaFindingsParser.findingContentSha256(chapter.document, finding.locator),
        )
        val document = snapshot.document.withResolution(findingId, resolution)
        return commitQaSnapshot(chapter, snapshot, document, "qa: resolve $findingId")
    }

    private suspend fun commitQaSnapshot(
        chapter: OpenChapter,
        snapshot: QaFindingsSnapshot,
        document: QaFindingsDocument,
        message: String,
    ): OpenChapter {
        val raw = QaFindingsParser.serialize(document)
        val sha = client.updateFile(snapshot.path, snapshot.sha, raw, message)
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

    private suspend fun loadEndnoteProposals(): EndnoteProposalSnapshot? {
        val remote = client.getFileOrNull(EndnoteProposalParser.PATH) ?: return null
        return EndnoteProposalSnapshot(
            path = EndnoteProposalParser.PATH,
            remote = remote,
            raw = remote.content,
            document = EndnoteProposalParser.parse(remote.content),
        )
    }

    private suspend fun isApprovalCurrent(
        file: ChapterFile,
        editorContentSha256: String,
        readerFile: String,
    ): Boolean {
        val approvalRemote = client.getFileOrNull(ApprovalParser.path(file.volume, file.chapter)) ?: return false
        val approval = runCatching { ApprovalParser.parse(approvalRemote.content) }.getOrNull() ?: return false
        if (
            approval.schemaVersion != 2 ||
            approval.volume != file.volume ||
            approval.chapter != file.chapter ||
            approval.editorContentSha256 != editorContentSha256
        ) return false
        val reader = client.getFileOrNull(readerFile) ?: return false
        return approval.readerContentSha256 == QaFindingsParser.sha256(reader.content)
    }
}
