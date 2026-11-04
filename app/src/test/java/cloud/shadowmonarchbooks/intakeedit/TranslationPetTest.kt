package cloud.shadowmonarchbooks.intakeedit

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPetTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun moodsFollowLocalCalendarDays() {
        assertEquals(PetMood.NEW, petStatus(emptyList(), today).mood)
        assertEquals(PetMood.HAPPY, petStatus(listOf(today), today).mood)
        assertEquals(PetMood.WAITING, petStatus(listOf(today.minusDays(1)), today).mood)
        assertEquals(PetMood.HUNGRY, petStatus(listOf(today.minusDays(2)), today).mood)
        assertEquals(PetMood.SAD, petStatus(listOf(today.minusDays(4)), today).mood)
        assertTrue(petStatus(listOf(today), today).fedToday)
        assertFalse(petStatus(listOf(today.minusDays(1)), today).fedToday)
    }

    @Test fun levelCountsDistinctDaysAndIgnoresFutureDates() {
        val days = (0L..4L).map(today::minusDays) + listOf(today, today.plusDays(1))
        val status = petStatus(days, today)
        assertEquals(5, status.fedDays)
        assertEquals(2, status.level)
        assertEquals(0, status.levelProgress)
    }

    @Test fun companionGetsHungryEvenIfNeverFed() {
        val started = today.minusDays(4)
        assertEquals(PetMood.NEW, petStatus(emptyList(), started, started).mood)
        assertEquals(PetMood.WAITING, petStatus(emptyList(), started.plusDays(1), started).mood)
        assertEquals(PetMood.HUNGRY, petStatus(emptyList(), started.plusDays(2), started).mood)
        assertEquals(PetMood.SAD, petStatus(emptyList(), today, started).mood)
        assertFalse(petStatus(emptyList(), today, started).fedToday)
    }
}
