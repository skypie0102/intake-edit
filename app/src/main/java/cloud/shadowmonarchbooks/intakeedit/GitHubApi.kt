package cloud.shadowmonarchbooks.intakeedit

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class GitHubException(message: String, val statusCode: Int? = null) : Exception(message)

class GitHubApi(private val settings: RepoSettings, private val token: String) {
    private val repoBase = "/repos/${encode(settings.owner)}/${encode(settings.repo)}"
    suspend fun verifyUser(): String = request("GET", "/user").optString("login").takeIf { it.isNotBlank() } ?: "authenticated user"
    suspend fun listIntakeFiles(): List<ChapterFile> {
        val branch = request("GET", "$repoBase/branches/${encode(settings.branch)}")
        val treeSha = branch.getJSONObject("commit").getJSONObject("commit").getJSONObject("tree").getString("sha")
        val tree = request("GET", "$repoBase/git/trees/$treeSha?recursive=1")
        if (tree.optBoolean("truncated", false)) throw GitHubException("GitHub returned a truncated repository tree. Narrow the intake root in Settings.")
        val root = settings.intakeRoot.trim('/').let { if (it.isBlank()) "" else "$it/" }
        val regex = Regex(""".*/vol-(\d+)/chapters/ch_(\d+)\.yml$""")
        val array = tree.optJSONArray("tree") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i); if (item.optString("type") != "blob") continue
                val path = item.optString("path"); if (root.isNotEmpty() && !path.startsWith(root)) continue
                val match = regex.matchEntire(path) ?: continue
                add(ChapterFile(path, match.groupValues[1].toInt(), match.groupValues[2].toInt()))
            }
        }.sortedWith(compareBy<ChapterFile> { it.volume }.thenBy { it.chapter })
    }
    suspend fun getFile(path: String): FileSnapshot {
        val response = request("GET", "$repoBase/contents/${encodePath(path)}?ref=${encode(settings.branch)}")
        val bytes = Base64.decode(response.getString("content").replace("\n", ""), Base64.DEFAULT)
        return FileSnapshot(path, response.getString("sha"), String(bytes, StandardCharsets.UTF_8))
    }
    suspend fun getFileOrNull(path: String): FileSnapshot? = try { getFile(path) } catch (e: GitHubException) { if (e.statusCode == 404) null else throw e }
    suspend fun updateFile(path: String, sha: String, content: String, message: String): String {
        val payload = JSONObject().put("message", message).put("content", Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)).put("sha", sha).put("branch", settings.branch)
        return request("PUT", "$repoBase/contents/${encodePath(path)}", payload).optJSONObject("content")?.optString("sha").orEmpty()
    }
    private fun encodePath(path: String) = path.split('/').joinToString("/") { encode(it) }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com$path").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/vnd.github+json"); connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); connection.setRequestProperty("User-Agent", "Intake-Edit-Android")
            if (body != null) { connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) } }
            val status = connection.responseCode; val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val apiMessage = json.optString("message")
                val friendly = when (status) {
                    401 -> "GitHub rejected the token. Check it in Settings."
                    403 -> "GitHub denied this operation. Check repository access and Contents permissions."
                    409 -> "GitHub rejected the update, usually because the file changed after you opened it. Reload the chapter before editing again."
                    else -> if (apiMessage.isNotBlank()) "GitHub request failed with HTTP $status. $apiMessage" else "GitHub request failed with HTTP $status."
                }
                throw GitHubException(friendly, status)
            }
            json
        } finally { connection.disconnect() }
    }
}
