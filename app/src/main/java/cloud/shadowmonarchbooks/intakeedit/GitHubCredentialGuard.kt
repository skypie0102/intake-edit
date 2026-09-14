package cloud.shadowmonarchbooks.intakeedit

internal object GitHubCredentialGuard {
    private val fineGrainedPat = Regex("github_pat_[A-Za-z0-9_]{20,}")
    private val prefixedToken = Regex("gh[pousr]_[A-Za-z0-9]{20,}")

    fun containsCredential(text: String): Boolean =
        fineGrainedPat.containsMatchIn(text) || prefixedToken.containsMatchIn(text)

    fun requireSafeForCommit(text: String) {
        if (containsCredential(text)) {
            throw GitHubException(
                "Blocked a GitHub credential from being committed. Remove the token from the editor or clipboard and try again.",
            )
        }
    }
}
