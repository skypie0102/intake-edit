package cloud.shadowmonarchbooks.intakeedit

internal data class AttentionState(
    val unresolvedCount: Int,
    val unresolvedLocators: List<String>,
    val hasUnlocatedActiveQa: Boolean,
    val activeQaByLocator: Map<String, List<QaFinding>>,
    val allQaByLocator: Map<String, List<QaFinding>>,
    val qaDispositionById: Map<String, QaFindingDisposition>,
)

internal fun buildAttentionState(
    document: EditorDocument,
    qa: QaFindingsDocument?,
    proposals: EndnoteProposalDocument?,
    volume: Int,
    chapter: Int,
    editorContentSha256: String,
): AttentionState {
    val allQa = qa?.findings.orEmpty()
    val dispositions = allQa.associate { finding ->
        finding.id to requireNotNull(qa).disposition(finding, document, editorContentSha256)
    }
    val activeQa = allQa.filter { dispositions[it.id] == QaFindingDisposition.ACTIVE }
    val pendingProposals = proposals?.proposals.orEmpty().filter {
        it.status == "pending" && it.volume == volume && it.chapter == chapter
    }

    val unresolvedLocatorSet = buildSet {
        activeQa.mapTo(this) { it.locator }
        pendingProposals.mapTo(this) { it.locator }
        remove("")
    }
    val unresolvedLocators = document.entries
        .map { it.locator }
        .filter { it in unresolvedLocatorSet }

    return AttentionState(
        unresolvedCount = activeQa.size + pendingProposals.size,
        unresolvedLocators = unresolvedLocators,
        hasUnlocatedActiveQa = activeQa.any { it.locator.isBlank() },
        activeQaByLocator = activeQa.filter { it.locator.isNotBlank() }.groupBy { it.locator },
        allQaByLocator = allQa.filter { it.locator.isNotBlank() }.groupBy { it.locator },
        qaDispositionById = dispositions,
    )
}
