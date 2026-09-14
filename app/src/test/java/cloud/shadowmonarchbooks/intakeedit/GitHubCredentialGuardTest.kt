package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCredentialGuardTest {
    @Test
    fun detectsFineGrainedPatShape() {
        val candidate = "github" + "_pat_" + "A".repeat(40)
        assertTrue(GitHubCredentialGuard.containsCredential(candidate))
        var blocked = false
        try {
            GitHubCredentialGuard.requireSafeForCommit("english: $candidate")
        } catch (_: GitHubException) {
            blocked = true
        }
        assertTrue(blocked)
    }

    @Test
    fun detectsLegacyPrefixedTokenShape() {
        val candidate = "gh" + "p_" + "A".repeat(36)
        assertTrue(GitHubCredentialGuard.containsCredential(candidate))
    }

    @Test
    fun ordinaryTranslationTextIsAllowed() {
        assertFalse(GitHubCredentialGuard.containsCredential("A normal translated paragraph with underscores_and words."))
        GitHubCredentialGuard.requireSafeForCommit("A normal translated paragraph.")
    }
}
