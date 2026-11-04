package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class PetMood { NEW, HAPPY, WAITING, HUNGRY, SAD }

internal data class PetStatus(val mood: PetMood, val fedDays: Int, val fedToday: Boolean, val completed: Int = 0) {
    val level: Int get() = completed / 5 + 1
    val levelProgress: Int get() = completed % 5
}

/** Each chapter can feed the pet once, even if QA sends it back for corrections. */
internal fun petStatus(feedingDates: Collection<LocalDate>, today: LocalDate, started: LocalDate = today, completed: Int = 0): PetStatus {
    val dates = feedingDates.filter { !it.isAfter(today) }.toSet()
    val latest = dates.maxOrNull()
    val age = ChronoUnit.DAYS.between(latest ?: minOf(started, today), today)
    val mood = when {
        latest == null && age == 0L -> PetMood.NEW
        latest != null && age == 0L -> PetMood.HAPPY
        age == 1L -> PetMood.WAITING
        age < 4L -> PetMood.HUNGRY
        else -> PetMood.SAD
    }
    return PetStatus(mood, dates.size, latest != null && age == 0L, completed.coerceAtLeast(0))
}

internal class PetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("translation_pet", Context.MODE_PRIVATE)

    private fun feedings(): JSONObject = runCatching {
        JSONObject(prefs.getString("feedings", "{}").orEmpty())
    }.getOrElse { JSONObject() }

    fun feed(settings: RepoSettings, file: ChapterFile, today: LocalDate = LocalDate.now()) {
        val entries = feedings()
        val key = listOf(settings.owner, settings.repo, settings.branch, file.path).joinToString("/")
        if (!entries.has(key)) {
            entries.put(key, today.toString())
            prefs.edit().putString("feedings", entries.toString()).apply()
        }
    }

    fun status(settings: RepoSettings, completed: Int, today: LocalDate = LocalDate.now()): PetStatus {
        val entries = feedings()
        val prefix = listOf(settings.owner, settings.repo, settings.branch).joinToString("/") + "/"
        val dates = entries.keys().asSequence().filter { it.startsWith(prefix) }.mapNotNull { key ->
            runCatching { LocalDate.parse(entries.getString(key)) }.getOrNull()
        }.toList()
        val startedKey = "started_" + petProjectKey(settings, "")
        val started = runCatching { LocalDate.parse(prefs.getString(startedKey, null)) }.getOrElse {
            today.also { prefs.edit().putString(startedKey, it.toString()).apply() }
        }
        return petStatus(dates, today, started, completed)
    }
}
