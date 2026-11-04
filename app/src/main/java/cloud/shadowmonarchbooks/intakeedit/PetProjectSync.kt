package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class PetProjectSnapshot(val completed: Int?, val syncedAt: Long)

internal fun petProjectKey(settings: RepoSettings, token: String): String {
    val data = listOf(settings.owner.trim().lowercase(), settings.repo.trim().lowercase(),
        settings.branch.trim(), settings.intakeRoot.trim('/'), token).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal class PetProjectCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pet_project", Context.MODE_PRIVATE)
    fun get(settings: RepoSettings, token: String): PetProjectSnapshot? {
        if (token.isBlank() || prefs.getString("key", null) != petProjectKey(settings, token)) return null
        val count = prefs.getInt("completed", -1).takeIf { it >= 0 }
        return PetProjectSnapshot(count, prefs.getLong("synced_at", 0L))
    }
    fun put(settings: RepoSettings, token: String, completed: Int?, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putString("key", petProjectKey(settings, token))
            .putInt("completed", completed ?: -1).putLong("synced_at", timestamp).apply()
    }
    fun invalidate() { prefs.edit().clear().apply() }
}

/** Uses the same validated status indexes and dirty-chapter refreshes as the chapter list. */
internal suspend fun countProjectCompletions(repository: ChapterRepository, volumes: List<Int>): Int {
    var total = 0
    for (volume in volumes.distinct()) {
        val index = repository.loadVolumeStatus(volume)
            ?: error("Workflow index for volume $volume is unavailable or stale.")
        val dirty = index.refreshFiles.mapTo(hashSetOf()) { it.path }
        total += index.progressByPath.count { (path, progress) -> path !in dirty && progress.complete }
        for (file in index.refreshFiles) {
            if (repository.loadBaseProgress(file).progress.complete) total++
        }
    }
    return total
}

internal suspend fun syncPetProject(context: Context): Boolean {
    val app = context.applicationContext
    val settings = SettingsStore(app).load()
    val token = SecureTokenStore(app).load()
    val cache = PetProjectCache(app)
    if (token.isBlank()) return false
    try {
        GitHubApi(settings, token).verifyUser()
        val repository = DefaultChapterRepositoryFactory.create(settings, token)
        val volumes = repository.listVolumes() // verifies access to the selected project and branch
        val prior = cache.get(settings, token)
        val result = try { countProjectCompletions(repository, volumes) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        val completed = result ?: prior?.completed
        // Discard a result if settings or token changed during the request.
        if (petProjectKey(settings, token) != petProjectKey(SettingsStore(app).load(), SecureTokenStore(app).load())) return false
        cache.put(settings, token, completed, if (result == null) prior?.syncedAt ?: 0L else System.currentTimeMillis())
        TranslationPetWidget.refreshAll(app)
        return completed != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: GitHubException) {
        if (e.statusCode == 401 || e.statusCode == 404) {
            cache.invalidate()
            TranslationPetWidget.refreshAll(app)
        }
        return false
    } catch (_: Exception) {
        return false
    }
}

internal fun schedulePetProjectSync(context: Context) {
    val request = PeriodicWorkRequestBuilder<PetSyncWorker>(1, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("pet-project-sync", ExistingPeriodicWorkPolicy.KEEP, request)
}

class PetSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (syncPetProject(applicationContext)) Result.success() else Result.retry()
}
