package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class PetMood { NEW, HAPPY, WAITING, HUNGRY, SAD }

internal data class PetStatus(val mood: PetMood, val fedDays: Int, val fedToday: Boolean) {
    val level: Int get() = fedDays / 5 + 1
    val levelProgress: Int get() = fedDays % 5
}

/** Each chapter can feed the pet once, even if QA sends it back for corrections. */
internal fun petStatus(feedingDates: Collection<LocalDate>, today: LocalDate): PetStatus {
    val dates = feedingDates.filter { !it.isAfter(today) }.toSet()
    val latest = dates.maxOrNull()
    val age = latest?.let { ChronoUnit.DAYS.between(it, today) }
    val mood = when {
        age == null -> PetMood.NEW
        age == 0L -> PetMood.HAPPY
        age == 1L -> PetMood.WAITING
        age < 4L -> PetMood.HUNGRY
        else -> PetMood.SAD
    }
    return PetStatus(mood, dates.size, age == 0L)
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

    fun status(today: LocalDate = LocalDate.now()): PetStatus {
        val entries = feedings()
        val dates = entries.keys().asSequence().mapNotNull { key ->
            runCatching { LocalDate.parse(entries.getString(key)) }.getOrNull()
        }.toList()
        return petStatus(dates, today)
    }
}
