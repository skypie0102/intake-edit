from pathlib import Path

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    (ROOT / path).write_text(text)

# Add reusable GitHub credential detector. Keep test tokens assembled dynamically so
# no token-shaped fixture is ever committed literally.
guard_path = ROOT / 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GitHubCredentialGuard.kt'
guard_path.write_text('''package cloud.shadowmonarchbooks.intakeedit\n\ninternal object GitHubCredentialGuard {\n    private val fineGrainedPat = Regex("github_pat_[A-Za-z0-9_]{20,}")\n    private val prefixedToken = Regex("gh[pousr]_[A-Za-z0-9]{20,}")\n\n    fun containsCredential(text: String): Boolean =\n        fineGrainedPat.containsMatchIn(text) || prefixedToken.containsMatchIn(text)\n\n    fun requireSafeForCommit(text: String) {\n        if (containsCredential(text)) {\n            throw GitHubException(\n                "Blocked a GitHub credential from being committed. Remove the token from the editor or clipboard and try again.",\n            )\n        }\n    }\n}\n''')

# Block GitHub-token-shaped data at both GitHub write paths and make 401 diagnostic precise.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/GitHubApi.kt'
s = read(path)
old = '''    suspend fun updateFile(path: String, sha: String, content: String, message: String): String {\n        val payload = JSONObject()\n'''
new = '''    suspend fun updateFile(path: String, sha: String, content: String, message: String): String {\n        GitHubCredentialGuard.requireSafeForCommit(content)\n        val payload = JSONObject()\n'''
if old not in s:
    raise AssertionError('updateFile marker not found')
s = s.replace(old, new, 1)
old = '''    suspend fun updateFilesAtomically(updates: List<GitHubFileUpdate>, message: String): String {\n        require(updates.isNotEmpty()) { "At least one file update is required." }\n        require(updates.map { it.path }.distinct().size == updates.size) { "Atomic update contains duplicate file paths." }\n\n        val branch = request("GET", "$repoBase/branches/${encode(settings.branch)}")\n'''
new = '''    suspend fun updateFilesAtomically(updates: List<GitHubFileUpdate>, message: String): String {\n        require(updates.isNotEmpty()) { "At least one file update is required." }\n        require(updates.map { it.path }.distinct().size == updates.size) { "Atomic update contains duplicate file paths." }\n        updates.forEach { GitHubCredentialGuard.requireSafeForCommit(it.content) }\n\n        val branch = request("GET", "$repoBase/branches/${encode(settings.branch)}")\n'''
if old not in s:
    raise AssertionError('atomic marker not found')
s = s.replace(old, new, 1)
old = '''                        401 -> "GitHub rejected the token. Check it in Settings."\n'''
new = '''                        401 -> "GitHub returned 401 Bad credentials. This token is invalid, expired, or revoked on GitHub; reinstalling Intake Edit does not consume or rotate personal access tokens."\n'''
if old not in s:
    raise AssertionError('401 message marker not found')
s = s.replace(old, new, 1)
write(path, s)

# Block token-shaped clipboard content immediately in the editor, before it can enter a draft.
path = 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt'
s = read(path)
old = '''                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()\n                    if (text.isNotEmpty()) {\n                        focusManager.clearFocus(force = true)\n                        rich = RichInlineState.fromClipboard(text)\n                        onEnglishChange(rich.toMarkup())\n                    }\n'''
new = '''                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()\n                    if (GitHubCredentialGuard.containsCredential(text)) {\n                        focusManager.clearFocus(force = true)\n                        Toast.makeText(\n                            context,\n                            "Paste blocked: clipboard looks like a GitHub credential.",\n                            Toast.LENGTH_LONG,\n                        ).show()\n                    } else if (text.isNotEmpty()) {\n                        focusManager.clearFocus(force = true)\n                        rich = RichInlineState.fromClipboard(text)\n                        onEnglishChange(rich.toMarkup())\n                    }\n'''
if old not in s:
    raise AssertionError('paste marker not found')
s = s.replace(old, new, 1)
write(path, s)

# Unit coverage for the pure credential detector without storing literal token-shaped fixtures.
test_path = ROOT / 'app/src/test/java/cloud/shadowmonarchbooks/intakeedit/GitHubCredentialGuardTest.kt'
test_path.write_text('''package cloud.shadowmonarchbooks.intakeedit\n\nimport kotlin.test.Test\nimport kotlin.test.assertFalse\nimport kotlin.test.assertFailsWith\nimport kotlin.test.assertTrue\n\nclass GitHubCredentialGuardTest {\n    @Test\n    fun detectsFineGrainedPatShape() {\n        val candidate = "github" + "_pat_" + "A".repeat(40)\n        assertTrue(GitHubCredentialGuard.containsCredential(candidate))\n        assertFailsWith<GitHubException> { GitHubCredentialGuard.requireSafeForCommit("english: $candidate") }\n    }\n\n    @Test\n    fun detectsLegacyPrefixedTokenShape() {\n        val candidate = "gh" + "p_" + "A".repeat(36)\n        assertTrue(GitHubCredentialGuard.containsCredential(candidate))\n    }\n\n    @Test\n    fun ordinaryTranslationTextIsAllowed() {\n        assertFalse(GitHubCredentialGuard.containsCredential("A normal translated paragraph with underscores_and words."))\n        GitHubCredentialGuard.requireSafeForCommit("A normal translated paragraph.")\n    }\n}\n''')

# Patch release.
path = 'app/build.gradle.kts'
s = read(path)
if 'versionCode = 25' not in s or 'versionName = "0.8.2"' not in s:
    raise AssertionError('expected v0.8.2 version not found')
s = s.replace('versionCode = 25', 'versionCode = 26', 1)
s = s.replace('versionName = "0.8.2"', 'versionName = "0.8.3"', 1)
write(path, s)
