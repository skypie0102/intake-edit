package cloud.shadowmonarchbooks.intakeedit

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class GitHubException(message: String, val statusCode: Int? = null) : Exception(message)

data class GitHubFileUpdate(
    val path: String,
    val expectedSha: String,
    val content: String,
)

class GitHubApi(private val settings: RepoSettings, private val token: String) {
    private val repoBase = "/repos/${encode(settings.owner)}/${encode(settings.repo)}"

    suspend fun verifyUser(): String =
        request("GET", "/user").optString("login").takeIf { it.isNotBlank() } ?: "authenticated user"

    suspend fun listIntakeVolumes(): List<Int> {
        val root = settings.intakeRoot.trim('/')
        val path = if (root.isBlank()) {
            "$repoBase/contents?ref=${encode(settings.branch)}"
        } else {
            "$repoBase/contents/${encodePath(root)}?ref=${encode(settings.branch)}"
        }
        val entries = requestArray("GET", path)
        val volumeRegex = Regex("""vol-(\d+)""")
        return buildList {
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                if (item.optString("type") != "dir") continue
                val match = volumeRegex.matchEntire(item.optString("name")) ?: continue
                add(match.groupValues[1].toInt())
            }
        }.distinct().sorted()
    }

    suspend fun listIntakeFiles(volume: Int): List<ChapterFile> {
        require(volume >= 0) { "Volume must be non-negative." }
        val root = settings.intakeRoot.trim('/')
        val volumeDir = "vol-${volume.toString().padStart(2, '0')}"
        val relative = listOf(root, volumeDir, "chapters").filter { it.isNotBlank() }.joinToString("/")
        val entries = requestArray(
            "GET",
            "$repoBase/contents/${encodePath(relative)}?ref=${encode(settings.branch)}",
        )
        val chapterRegex = Regex("""ch_(\d+)\.yml""")
        return buildList {
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                if (item.optString("type") != "file") continue
                val match = chapterRegex.matchEntire(item.optString("name")) ?: continue
                add(
                    ChapterFile(
                        path = item.getString("path"),
                        volume = volume,
                        chapter = match.groupValues[1].toInt(),
                    ),
                )
            }
        }.sortedBy { it.chapter }
    }

    suspend fun getFile(path: String): FileSnapshot = getFileAtRef(path, settings.branch)

    suspend fun getFileOrNull(path: String): FileSnapshot? = try {
        getFile(path)
    } catch (e: GitHubException) {
        if (e.statusCode == 404) null else throw e
    }

    suspend fun updateFile(path: String, sha: String, content: String, message: String): String {
        GitHubCredentialGuard.requireSafeForCommit(content)
        val payload = JSONObject()
            .put("message", message)
            .put(
                "content",
                Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP),
            )
            .put("sha", sha)
            .put("branch", settings.branch)
        return request("PUT", "$repoBase/contents/${encodePath(path)}", payload)
            .optJSONObject("content")
            ?.optString("sha")
            .orEmpty()
    }

    suspend fun updateFilesAtomically(updates: List<GitHubFileUpdate>, message: String): String {
        require(updates.isNotEmpty()) { "At least one file update is required." }
        require(updates.map { it.path }.distinct().size == updates.size) { "Atomic update contains duplicate file paths." }
        updates.forEach { GitHubCredentialGuard.requireSafeForCommit(it.content) }

        val branch = request("GET", "$repoBase/branches/${encode(settings.branch)}")
        val headSha = branch.getJSONObject("commit").getString("sha")
        updates.forEach { update ->
            val current = getFileAtRef(update.path, headSha)
            if (current.sha != update.expectedSha) {
                throw GitHubException("${update.path} changed after the glossary was loaded. Refresh before committing.", 409)
            }
        }

        val additions = JSONArray()
        updates.forEach { update ->
            additions.put(
                JSONObject()
                    .put("path", update.path)
                    .put(
                        "contents",
                        Base64.encodeToString(update.content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP),
                    ),
            )
        }
        val input = JSONObject()
            .put(
                "branch",
                JSONObject()
                    .put("repositoryNameWithOwner", "${settings.owner}/${settings.repo}")
                    .put("branchName", settings.branch),
            )
            .put("message", JSONObject().put("headline", message))
            .put("fileChanges", JSONObject().put("additions", additions))
            .put("expectedHeadOid", headSha)
        val payload = JSONObject()
            .put(
                "query",
                "mutation(\$input: CreateCommitOnBranchInput!) { createCommitOnBranch(input: \$input) { commit { oid } } }",
            )
            .put("variables", JSONObject().put("input", input))
        val response = request("POST", "/graphql", payload)
        response.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let { errors ->
            val messages = buildList {
                for (i in 0 until errors.length()) add(errors.getJSONObject(i).optString("message"))
            }.filter { it.isNotBlank() }
            throw GitHubException(
                messages.joinToString("; ").ifBlank { "GitHub rejected the atomic glossary commit." },
                409,
            )
        }
        return response
            .optJSONObject("data")
            ?.optJSONObject("createCommitOnBranch")
            ?.optJSONObject("commit")
            ?.optString("oid")
            ?.takeIf { it.isNotBlank() }
            ?: throw GitHubException("GitHub did not return a commit id for the glossary update.")
    }

    private suspend fun getFileAtRef(path: String, ref: String): FileSnapshot {
        val response = request("GET", "$repoBase/contents/${encodePath(path)}?ref=${encode(ref)}")
        val bytes = Base64.decode(response.getString("content").replace("\n", ""), Base64.DEFAULT)
        return FileSnapshot(path, response.getString("sha"), String(bytes, StandardCharsets.UTF_8))
    }

    private fun encodePath(path: String) = path.split('/').joinToString("/") { encode(it) }

    private fun encode(value: String) =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val text = requestText(method, path, body)
        if (text.isBlank()) return JSONObject()
        return runCatching { JSONObject(text) }
            .getOrElse { throw GitHubException("GitHub returned an unexpected response.") }
    }

    private suspend fun requestArray(method: String, path: String): JSONArray {
        val text = requestText(method, path)
        if (text.isBlank()) return JSONArray()
        return runCatching { JSONArray(text) }
            .getOrElse { throw GitHubException("GitHub returned an unexpected directory response.") }
    }

    private suspend fun requestText(method: String, path: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val connection = URL("https://api.github.com$path").openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Intake-Edit-Android")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = if (stream == null) {
                    ""
                } else {
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
                }
                if (status !in 200..299) {
                    val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                    val apiMessage = json.optString("message")
                    val friendly = when (status) {
                        401 -> "GitHub returned 401 Bad credentials. This token is invalid, expired, or revoked on GitHub; reinstalling Intake Edit does not consume or rotate personal access tokens."
                        403 -> "GitHub denied this operation. Check repository access and Contents permissions."
                        409, 422 -> "GitHub rejected the update, usually because the repository changed after you opened it. Refresh before editing again."
                        else -> if (apiMessage.isNotBlank()) {
                            "GitHub request failed with HTTP $status. $apiMessage"
                        } else {
                            "GitHub request failed with HTTP $status."
                        }
                    }
                    throw GitHubException(friendly, status)
                }
                text
            } finally {
                connection.disconnect()
            }
        }
}
