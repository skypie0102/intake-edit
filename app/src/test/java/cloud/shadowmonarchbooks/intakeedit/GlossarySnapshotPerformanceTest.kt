package cloud.shadowmonarchbooks.intakeedit

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GlossarySnapshotPerformanceTest {
    @Test
    fun loadPinsAllFilesToOneRevisionAndBoundsConcurrentReads() = runTest {
        var active = 0
        var peak = 0
        val refs = mutableListOf<String>()
        val snapshot = loadGlossarySnapshot(
            readHead = { "revision" },
            readFile = { path, ref ->
                refs += ref
                active++
                peak = maxOf(peak, active)
                delay(100)
                active--
                FileSnapshot(path, "file-sha", if (path.endsWith(".txt")) "=== terms ===\n* 原 = Original\n" else "{\"schema_version\":1}")
            },
            parseDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertEquals(8, refs.size)
        assertEquals(setOf("revision"), refs.toSet())
        assertEquals(4, peak)
        assertEquals(200L, testScheduler.currentTime)
        assertEquals(5, snapshot.base.size)
        assertEquals(5, snapshot.documents.baseEntries.size)
    }

    @Test
    fun pendingSuggestionsRetainDuplicateAndUpdateSemanticsAcrossCopies() {
        val entry = GlossaryEntry("term", "terms", listOf("名前"), "Some Name")
        fun proposal(id: String, action: String = "new_entry", source: String = "別", english: String = "Other") =
            GlossaryProposal(id = id, action = action, reason = "test", sourceAliases = listOf(source), translatedName = english)
        val documents = GlossaryDocuments(
            baseEntries = listOf(entry), additions = emptyList(), governance = emptyMap(),
            proposals = listOf(
                proposal("source", source = "名 前"),
                proposal("english", english = "  ＳＯＭＥ   NAME "),
                proposal("update", action = "update_entry", source = "名前"),
                proposal("new"),
                proposal("done").copy(status = "approved"),
            ),
        )
        assertEquals(listOf("update", "new"), documents.pendingProposals.map { it.id })
        assertSame(documents.effectiveEntries, documents.effectiveEntries)
        assertEquals(listOf("update", "new", "done"), documents.copy(proposals = documents.proposals.map { it.copy(status = "pending") }).pendingProposals.map { it.id })
    }
}
