package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRigTest {
    @Test fun eachMoodHasRigPartsAndCompleteWidgetLoop() {
        PetMood.entries.forEach { mood ->
            val rig = companionRigAssets(mood)
            assertTrue(listOf(rig.head, rig.eyes, rig.blink, rig.leftArm, rig.rightArm, rig.symbol).all { it != 0 })
            assertEquals(16, companionWidgetFrames(mood).size)
        }
        assertNotEquals(companionRigAssets(PetMood.NEW).symbol, companionRigAssets(PetMood.WAITING).symbol)
    }

    @Test fun thinkingUsesOneContinuousArmWithStableAttachment() {
        val rest = thinkingArmCoordinates(0f)
        val halfway = thinkingArmCoordinates(.5f)
        val thinking = thinkingArmCoordinates(1f)
        assertEquals(20, rest.size)
        assertEquals(rest[0], thinking[0], 0f)
        assertEquals(rest[1], thinking[1], 0f)
        for (i in rest.indices) assertEquals((rest[i] + thinking[i]) / 2, halfway[i], .001f)
        assertTrue(thinkingArmCoordinates(-1f).contentEquals(rest))
        assertTrue(thinkingArmCoordinates(2f).contentEquals(thinking))
    }
}
