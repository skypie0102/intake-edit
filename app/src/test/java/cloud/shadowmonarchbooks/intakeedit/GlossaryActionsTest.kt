package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryActionsTest {
    @Test
    fun approveAllAppliesBaseLockAndNewEntryTogether() {
        val yukino = GlossaryEntry(
            id = "characters:白坂雪乃",
            section = "characters",
            sourceAliases = listOf("白坂雪乃"),
            translatedName = "Shirasaka Yukino",
        )
        val documents = GlossaryDocuments(
            baseEntries = listOf(yukino),
            additions = emptyList(),
            governance = emptyMap(),
            proposals = listOf(
                GlossaryProposal(
                    id = "lock-yukino",
                    action = "lock_recommendation",
                    reason = "Keep the heroine name stable.",
                    targetId = yukino.id,
                    recommendQaLock = true,
                ),
                GlossaryProposal(
                    id = "new-term",
                    action = "new_entry",
                    reason = "Recurring committee name.",
                    section = "terms",
                    sourceAliases = listOf("美化委員"),
                    translatedName = "Beautification Committee",
                ),
            ),
        )

        val approved = GlossaryActions.approveAllPending(documents)

        assertTrue(approved.governance.getValue(yukino.id).qaLock == true)
        assertEquals("Beautification Committee", approved.additions.single().translatedName)
        assertTrue(approved.proposals.all { it.status == "approved" })
    }

    @Test
    fun approveAllUsesPreviouslyEditedPendingProposalValues() {
        val base = GlossaryEntry(
            id = "characters:森下重義",
            section = "characters",
            sourceAliases = listOf("森下重義"),
            translatedName = "Morishita Shigeyoshi",
        )
        val documents = GlossaryDocuments(
            baseEntries = listOf(base),
            additions = emptyList(),
            governance = emptyMap(),
            proposals = listOf(
                GlossaryProposal(
                    id = "alias-morishita",
                    action = "update_entry",
                    reason = "Add surname-only source alias.",
                    targetId = base.id,
                    sourceAliases = listOf("森下重義", "森下"),
                    translatedName = "Morishita Shigeyoshi",
                ),
            ),
        )

        val approved = GlossaryActions.approveAllPending(documents)
        assertEquals(listOf("森下重義", "森下"), approved.governance.getValue(base.id).sourceAliases)
    }

    @Test
    fun approvePendingOnlyAppliesPooledDrafts() {
        val yukino = GlossaryEntry(
            id = "characters:雪乃",
            section = "characters",
            sourceAliases = listOf("雪乃"),
            translatedName = "Yukino",
        )
        val kenji = GlossaryEntry(
            id = "characters:ケンジ",
            section = "characters",
            sourceAliases = listOf("ケンジ"),
            translatedName = "Kenji",
        )
        val documents = GlossaryDocuments(
            baseEntries = listOf(yukino, kenji),
            additions = emptyList(),
            governance = emptyMap(),
            proposals = listOf(
                GlossaryProposal(
                    id = "lock-yukino",
                    action = "lock_recommendation",
                    reason = "Keep Yukino stable.",
                    targetId = yukino.id,
                    recommendQaLock = true,
                ),
                GlossaryProposal(
                    id = "lock-kenji",
                    action = "lock_recommendation",
                    reason = "Keep Kenji stable.",
                    targetId = kenji.id,
                    recommendQaLock = true,
                ),
            ),
        )

        val approved = GlossaryActions.approvePending(
            documents,
            approvalDrafts = mapOf("lock-yukino" to yukino.copy(qaLock = true)),
            approveAll = false,
        )

        assertTrue(approved.governance.getValue(yukino.id).qaLock == true)
        assertTrue(kenji.id !in approved.governance)
        assertEquals("approved", approved.proposals.first { it.id == "lock-yukino" }.status)
        assertEquals("pending", approved.proposals.first { it.id == "lock-kenji" }.status)
    }
}
