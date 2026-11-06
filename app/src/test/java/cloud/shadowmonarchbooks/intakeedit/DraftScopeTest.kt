package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DraftScopeTest {
    @Test fun identitySeparatesProjectsBranchesAndPaths() {
        val first = RepoSettings(owner = "Owner", repo = "Repo", branch = "main")
        val path = "editor_input/vol-01/ch_0001.yml"
        val key = draftScopeKey(first, path)
        assertEquals(key, draftScopeKey(first.copy(owner = "owner", repo = "REPO"), path))
        assertNotEquals(key, draftScopeKey(first.copy(owner = "someone"), path))
        assertNotEquals(key, draftScopeKey(first.copy(repo = "another"), path))
        assertNotEquals(key, draftScopeKey(first.copy(branch = "Main"), path))
        assertNotEquals(key, draftScopeKey(first, "editor_input/vol-02/ch_0001.yml"))
    }
}
