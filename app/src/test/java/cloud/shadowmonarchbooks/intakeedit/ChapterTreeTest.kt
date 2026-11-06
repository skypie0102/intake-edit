package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChapterTreeTest {
    @Test fun listsMoreThanOneThousandChaptersWithoutDroppingEntries() {
        val items = JSONArray()
        (1..1501).reversed().forEach { number ->
            items.put(JSONObject().put("path", "ch_${number.toString().padStart(4, '0')}.yml").put("type", "blob"))
        }
        items.put(JSONObject().put("path", "notes.txt").put("type", "blob"))
        val result = chapterFilesFromTree(JSONObject().put("tree", items).put("truncated", false), "editor_input/vol-01/chapters", 1)
        assertEquals(1501, result.size)
        assertEquals(1, result.first().chapter)
        assertEquals(1501, result.last().chapter)
    }

    @Test fun rejectsTruncatedTreeInsteadOfShowingAnIncompleteList() {
        val tree = JSONObject().put("tree", JSONArray()).put("truncated", true)
        assertThrows(IllegalArgumentException::class.java) { chapterFilesFromTree(tree, "chapters", 1) }
    }
}
