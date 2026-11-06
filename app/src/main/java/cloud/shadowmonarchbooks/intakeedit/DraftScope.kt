package cloud.shadowmonarchbooks.intakeedit

import java.security.MessageDigest
import java.util.Locale

/** Repository and owner names are case-insensitive on GitHub; branch and path are not. */
internal fun draftScopeKey(settings: RepoSettings, path: String): String {
    val identity = listOf(settings.owner.trim().lowercase(Locale.ROOT), settings.repo.trim().lowercase(Locale.ROOT),
        settings.branch.trim(), path).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
