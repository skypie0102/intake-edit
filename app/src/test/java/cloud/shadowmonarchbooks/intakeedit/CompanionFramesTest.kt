package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionFramesTest {
    @Test fun eachMoodHasACompleteIdleLoop() {
        PetMood.entries.forEach { mood ->
            val frames = companionFrames(mood)
            assertEquals(24, frames.size)
            assertEquals(24, frames.toSet().size)
            assertTrue(frames.all { it != 0 })
        }
        assertTrue(companionFrames(PetMood.NEW).contentEquals(companionFrames(PetMood.WAITING)))
    }
}
