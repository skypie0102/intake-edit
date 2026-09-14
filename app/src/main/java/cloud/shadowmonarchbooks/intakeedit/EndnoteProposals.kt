package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject

data class EndnoteProposal(
    val id: String,
    val volume: Int,
    val chapter: Int,
    val locator: String,
    val sourceAnchor: String,
    val suggestedEnglishAnchor: String,
    val suggestedContent: String,
    val reason: String,
    val status: String,
)

data class EndnoteProposalDocument(
    val schemaVersion: Int,
    val proposals: List<EndnoteProposal>,
) {
    fun pendingFor(volume: Int, chapter: Int, locator: String): List<EndnoteProposal> =
        proposals.filter {
            it.status == "pending" && it.volume == volume && it.chapter == chapter && it.locator == locator
        }

    fun withStatus(id: String, status: String): EndnoteProposalDocument {
        require(status in EndnoteProposalParser.validStatuses) { "Unsupported endnote proposal status: $status" }
        var found = false
        val updated = proposals.map { proposal ->
            if (proposal.id == id) {
                found = true
                proposal.copy(status = status)
            } else proposal
        }
        require(found) { "Unknown endnote proposal: $id" }
        return copy(proposals = updated)
    }
}

data class EndnoteProposalSnapshot(
    val path: String,
    val remote: FileSnapshot,
    val raw: String,
    val document: EndnoteProposalDocument,
) {
    val changed: Boolean get() = raw != remote.content
}

object EndnoteProposalParser {
    const val PATH = "endnotes/proposals.json"
    internal val validStatuses = setOf("pending", "accepted", "rejected")

    fun parse(raw: String): EndnoteProposalDocument {
        val root = JSONObject(raw)
        require(root.optInt("schema_version", -1) == 1) { "Endnote proposal schema_version must be 1." }
        val array = root.optJSONArray("proposals") ?: error("Endnote proposal document is missing proposals array.")
        val proposals = buildList {
            val seen = linkedSetOf<String>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val proposal = EndnoteProposal(
                    id = item.getString("id"),
                    volume = item.getInt("volume"),
                    chapter = item.getInt("chapter"),
                    locator = item.getString("locator"),
                    sourceAnchor = item.getString("source_anchor"),
                    suggestedEnglishAnchor = item.getString("suggested_english_anchor"),
                    suggestedContent = item.getString("suggested_content"),
                    reason = item.getString("reason"),
                    status = item.getString("status"),
                )
                require(proposal.id.isNotBlank()) { "Endnote proposal $i has a blank id." }
                require(seen.add(proposal.id)) { "Duplicate endnote proposal id: ${proposal.id}" }
                require(proposal.volume >= 0 && proposal.chapter >= 0) { "${proposal.id}: invalid volume/chapter." }
                require(proposal.locator.matches(Regex("P\\d+"))) { "${proposal.id}: invalid locator." }
                require(proposal.sourceAnchor.isNotBlank()) { "${proposal.id}: source_anchor must not be blank." }
                require(proposal.suggestedContent.isNotBlank()) { "${proposal.id}: suggested_content must not be blank." }
                require(proposal.reason.isNotBlank()) { "${proposal.id}: reason must not be blank." }
                require(proposal.status in validStatuses) { "${proposal.id}: invalid status ${proposal.status}." }
                add(proposal)
            }
        }
        return EndnoteProposalDocument(1, proposals)
    }

    fun serialize(document: EndnoteProposalDocument): String {
        require(document.schemaVersion == 1)
        val root = JSONObject().put("schema_version", 1)
        val array = JSONArray()
        document.proposals.forEach { proposal ->
            array.put(
                JSONObject()
                    .put("id", proposal.id)
                    .put("volume", proposal.volume)
                    .put("chapter", proposal.chapter)
                    .put("locator", proposal.locator)
                    .put("source_anchor", proposal.sourceAnchor)
                    .put("suggested_english_anchor", proposal.suggestedEnglishAnchor)
                    .put("suggested_content", proposal.suggestedContent)
                    .put("reason", proposal.reason)
                    .put("status", proposal.status),
            )
        }
        root.put("proposals", array)
        return root.toString(2) + "\n"
    }

    fun stage(snapshot: EndnoteProposalSnapshot, proposalId: String, status: String): EndnoteProposalSnapshot {
        val document = snapshot.document.withStatus(proposalId, status)
        return snapshot.copy(raw = serialize(document), document = document)
    }
}
