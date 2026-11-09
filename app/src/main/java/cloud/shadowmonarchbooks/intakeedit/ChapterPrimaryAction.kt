package cloud.shadowmonarchbooks.intakeedit

/**
 * The editor's single source of truth for its primary bottom-bar action.
 * Translation references never count as supplied English: only EditorEntry.english does.
 */
internal enum class ChapterPrimaryAction(val label: String, val tagForQa: Boolean? = null) {
    COMMIT("Commit", false),
    TAG_FOR_QA("Tag for QA", true),
    TAG_FOR_FRESH_QA("Tag for Fresh QA", true),
    COMMIT_FOR_APPROVAL("Commit for Approval", true),
    REVIEW_QA_FINDINGS("Review QA Findings"),
    AWAITING_QA("Awaiting QA"),
    APPROVE_CHAPTER("Approve Chapter"),
    APPROVED("Approved"),
}

internal data class ChapterPrimaryActionState(
    val action: ChapterPrimaryAction,
    val enabled: Boolean,
    val confirmationTitle: String? = null,
    val confirmationMessage: String? = null,
) {
    val label: String get() = action.label

    /** Available as a deliberate secondary action when the chapter is complete but still being edited. */
    fun canCommitAsPendingReview(hasLocalChanges: Boolean): Boolean =
        hasLocalChanges && action in setOf(
            ChapterPrimaryAction.TAG_FOR_QA,
            ChapterPrimaryAction.TAG_FOR_FRESH_QA,
            ChapterPrimaryAction.COMMIT_FOR_APPROVAL,
            ChapterPrimaryAction.REVIEW_QA_FINDINGS,
        )
}

/**
 * Priorities:
 * - Incomplete English can only be committed as Pending Review.
 * - A reusable QA pass with active findings goes to Resolve/Override, never a new QA run.
 * - A closed reusable QA pass can be committed for approval or directly approved.
 * - A stale QA pass requires a fresh run once English is complete.
 * - A fully committed, already tagged chapter waits for the existing QA run.
 *
 * hasLocalChanges includes staged endnote decisions; hasRecordedQaPass means
 * a completed pass exists, not merely a pending QA file.
 */
internal fun resolveChapterPrimaryAction(
    document: EditorDocument,
    approved: Boolean,
    hasLocalChanges: Boolean,
    qaPassReusable: Boolean,
    activeQaFindings: Int,
    hasRecordedQaPass: Boolean,
    hasStagedEndnoteDecisions: Boolean = false,
): ChapterPrimaryActionState {
    val completeEnglish = document.entries.isNotEmpty() &&
        document.englishSupplied == document.englishTotal
    val action = when {
        approved -> ChapterPrimaryAction.APPROVED
        !completeEnglish -> ChapterPrimaryAction.COMMIT
        qaPassReusable && activeQaFindings > 0 -> ChapterPrimaryAction.REVIEW_QA_FINDINGS
        qaPassReusable && document.editorReviewComplete && !hasLocalChanges ->
            ChapterPrimaryAction.APPROVE_CHAPTER
        qaPassReusable -> ChapterPrimaryAction.COMMIT_FOR_APPROVAL
        hasRecordedQaPass -> ChapterPrimaryAction.TAG_FOR_FRESH_QA
        document.editorReviewComplete && !hasLocalChanges -> ChapterPrimaryAction.AWAITING_QA
        else -> ChapterPrimaryAction.TAG_FOR_QA
    }
    val enabled = when (action) {
        ChapterPrimaryAction.COMMIT -> hasLocalChanges
        ChapterPrimaryAction.APPROVED, ChapterPrimaryAction.AWAITING_QA -> false
        else -> true
    }

    val title = when (action) {
        ChapterPrimaryAction.COMMIT -> "Commit current progress?"
        ChapterPrimaryAction.TAG_FOR_QA -> "Tag chapter for QA?"
        ChapterPrimaryAction.TAG_FOR_FRESH_QA -> "Tag for fresh QA?"
        ChapterPrimaryAction.COMMIT_FOR_APPROVAL -> "Commit for approval?"
        else -> null
    }
    val message = when (action) {
        ChapterPrimaryAction.COMMIT ->
            "${document.englishSupplied} of ${document.englishTotal} English entries are supplied. " +
                "Commit these changes as Pending Review and continue translating in this chapter."
        ChapterPrimaryAction.TAG_FOR_QA ->
            "All ${document.englishTotal} English entries are supplied. Confirm you have reviewed the complete " +
                "chapter before committing it to Pending QA for a full semantic QA pass. " +
                "Any QA findings must be resolved or overridden before approval."
        ChapterPrimaryAction.TAG_FOR_FRESH_QA ->
            "The recorded QA pass is stale because protected chapter content changed. " +
                "Commit the complete chapter to Pending QA for a fresh full-chapter QA pass."
        ChapterPrimaryAction.COMMIT_FOR_APPROVAL ->
            "All findings from the recorded semantic QA pass are closed. " +
                "Commit the corrected or accepted chapter as Ready for Approval without running QA again. " +
                "Reader XHTML is produced only after you explicitly approve it."
        else -> null
    }?.let { base ->
        if (hasStagedEndnoteDecisions) {
            "$base Staged endnote suggestion decisions will be committed together with this chapter."
        } else base
    }
    return ChapterPrimaryActionState(action, enabled, title, message)
}
