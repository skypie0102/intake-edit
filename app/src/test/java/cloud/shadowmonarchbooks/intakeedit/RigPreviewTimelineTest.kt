package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RigPreviewTimelineTest {
    @Test fun interruptedActionsReachAnAnchorInAtMostOneCycle() {
        for (moodIsNew in listOf(false, true)) {
            val anchor = transitionAnchor(moodIsNew)
            for (start in 0 until 96) {
                var frame = start
                var steps = 0
                while (frame != anchor && steps < 96) {
                    frame = nextTransitionFrame(frame, 96)
                    steps++
                }
                assertEquals(anchor, frame)
                assertTrue(steps < 96)
            }
        }
    }

    @Test fun idleReturnsToRestBeforePlayingEveryActionFrame() {
        val timeline = RigPreviewTimeline(96, Random(19))
        var previous = 0
        var seenAction = false
        repeat(350) {
            val current = timeline.advance()
            assertTrue(current in 0..95)
            if (current >= 10) {
                seenAction = true
                assertEquals(previous + 1, current)
            }
            if (previous == 95) assertEquals(0, current)
            previous = current
        }
        assertTrue(seenAction)
    }

    @Test fun actionWaitsVaryAndGreetingCanStartImmediately() {
        val greeting = RigPreviewTimeline(96, Random(4), startWithAction = true)
        assertEquals(1, greeting.advance())
        val timeline = RigPreviewTimeline(96, Random(4))
        var previous = 0
        val starts = mutableListOf<Int>()
        repeat(1800) { tick ->
            val current = timeline.advance()
            if (current == 10 && previous == 9) {
                // Idle movement never reaches frame 10.
                starts.add(tick)
            }
            previous = current
        }
        val intervals = starts.zipWithNext { a, b -> b - a }
        assertTrue(intervals.size >= 3)
        assertTrue(intervals.distinct().size > 1)
    }
}
