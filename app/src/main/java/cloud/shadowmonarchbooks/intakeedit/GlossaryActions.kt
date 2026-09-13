package cloud.shadowmonarchbooks.intakeedit

object GlossaryActions {
    fun proposalEntry(proposal: GlossaryProposal, documents: GlossaryDocuments): GlossaryEntry {
        val target = proposal.targetId?.let { id -> documents.effectiveEntries.firstOrNull { it.id == id } }
        return when (proposal.action) {
            "new_entry" -> {
                val section = proposal.section.orEmpty().ifBlank { "terms" }
                val aliases = proposal.sourceAliases
                GlossaryEntry(
                    id = GlossaryParser.entryId(section, aliases),
                    section = section,
                    sourceAliases = aliases,
                    translatedName = proposal.translatedName.orEmpty(),
                    gender = proposal.gender,
                    description = proposal.description,
                    qaLock = proposal.recommendQaLock,
                    origin = "editor-approved",
                )
            }
            "lock_recommendation" -> requireNotNull(target) { "Proposal target not found: ${proposal.targetId}" }.copy(qaLock = true)
            else -> {
                val current = requireNotNull(target) { "Proposal target not found: ${proposal.targetId}" }
                current.copy(
                    sourceAliases = proposal.sourceAliases.takeIf { it.isNotEmpty() } ?: current.sourceAliases,
                    translatedName = proposal.translatedName?.takeIf { it.isNotBlank() } ?: current.translatedName,
                    gender = proposal.gender ?: current.gender,
                    description = proposal.description.takeIf { it.isNotBlank() } ?: current.description,
                    qaLock = current.qaLock || proposal.recommendQaLock,
                )
            }
        }
    }

    fun approvePending(
        documents: GlossaryDocuments,
        approvalDrafts: Map<String, GlossaryEntry>,
        approveAll: Boolean,
    ): GlossaryDocuments {
        var additions = documents.additions
        var governance = documents.governance
        var proposals = documents.proposals
        val selected = documents.pendingProposals.filter { approveAll || it.id in approvalDrafts }
        require(selected.isNotEmpty()) { "There are no glossary approvals to commit." }

        selected.forEach { proposal ->
            val currentDocuments = GlossaryDocuments(
                baseEntries = documents.baseEntries,
                additions = additions,
                governance = governance,
                proposals = proposals,
            )
            val entry = approvalDrafts[proposal.id] ?: proposalEntry(proposal, currentDocuments)
            val additionIndex = additions.indexOfFirst { it.id == entry.id }
            val baseExists = documents.baseEntries.any { it.id == entry.id }
            additions = when {
                additionIndex >= 0 -> additions.toMutableList().also {
                    it[additionIndex] = entry.copy(origin = "editor-approved")
                }
                baseExists -> additions
                proposal.action == "new_entry" -> {
                    GlossaryParser.ensureNewEntryAbsent(entry, currentDocuments.effectiveEntries)
                    additions + entry.copy(origin = "editor-approved")
                }
                else -> error("Glossary entry no longer exists: ${entry.id}")
            }
            if (baseExists) {
                governance = governance + (entry.id to GlossaryParser.fullOverride(entry))
            }
            proposals = proposals.map {
                if (it.id == proposal.id) proposal.copy(status = "approved") else it
            }
        }

        return GlossaryDocuments(
            baseEntries = documents.baseEntries,
            additions = additions,
            governance = governance,
            proposals = proposals,
        )
    }

    fun applyPendingDecisions(
        documents: GlossaryDocuments,
        approvalDrafts: Map<String, GlossaryEntry>,
        rejectionDraftIds: Set<String>,
    ): GlossaryDocuments {
        require(approvalDrafts.keys.intersect(rejectionDraftIds).isEmpty()) {
            "A glossary suggestion cannot be both approved and rejected in the same batch."
        }
        val pendingIds = documents.pendingProposals.mapTo(mutableSetOf()) { it.id }
        val decisionIds = approvalDrafts.keys + rejectionDraftIds
        require(decisionIds.isNotEmpty()) { "There are no pooled glossary decisions to commit." }
        require(decisionIds.all { it in pendingIds }) {
            "One or more pooled glossary decisions are no longer pending."
        }

        var result = if (approvalDrafts.isNotEmpty()) {
            approvePending(documents, approvalDrafts, approveAll = false)
        } else {
            documents
        }
        if (rejectionDraftIds.isNotEmpty()) {
            result = result.copy(
                proposals = result.proposals.map { proposal ->
                    if (proposal.id in rejectionDraftIds) proposal.copy(status = "rejected") else proposal
                },
            )
        }
        return result
    }

    fun approveAllPending(documents: GlossaryDocuments): GlossaryDocuments =
        approvePending(documents, approvalDrafts = emptyMap(), approveAll = true)
}
