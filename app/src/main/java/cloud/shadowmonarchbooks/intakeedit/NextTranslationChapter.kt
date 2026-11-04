package cloud.shadowmonarchbooks.intakeedit

/** Finds the earliest editable, untranslated chapter, using fresh index data when available. */
internal suspend fun findNextTranslationChapter(repository: ChapterRepository): ChapterFile? {
    var firstReview: ChapterFile? = null
    for (volume in repository.listVolumes().sorted()) {
        val index = repository.loadVolumeStatus(volume)
        val files = (index?.files ?: repository.listFiles(volume)).sortedBy { it.chapter }
        val dirty = index?.refreshFiles?.mapTo(hashSetOf()) { it.path }.orEmpty()
        for (file in files) {
            val progress = if (index != null && file.path !in dirty) index.progressByPath[file.path] else null
            val current = progress ?: repository.loadBaseProgress(file).progress
            if (current.workflowState != ChapterWorkflowState.PENDING_REVIEW) continue
            if (firstReview == null) firstReview = file
            if (current.englishSupplied < current.englishTotal) return file
        }
    }
    return firstReview
}
