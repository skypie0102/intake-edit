package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RigPreviewTimelineTest {
    @Test fun productionWidgetCoversEveryPoseInEachAction() {
        val frames = (0 until 384).map(::widgetFrameIndex)
        for (action in 0..3) {
            val actionFrames = frames.filter { it.first == action }.map { it.second }
            assertEquals((0 until 96).toList(), actionFrames)
            if (action > 0) assertEquals((0 until 48).toList(), actionFrames.map { it / 2 }.distinct())
        }
    }

    @Test fun changingToAnyMoodNeverShowsThePreviousMoodsFrame() {
        val moods = listOf("NEW", "WAITING", "HAPPY", "HUNGRY", "SAD")
        for (selected in moods) for (displayed in moods) {
            val shown = selectedPreviewFrame(selected, displayed, "$selected-rest", "$displayed-action")
            assertEquals(if (selected == displayed) "$displayed-action" else "$selected-rest", shown)
        }
    }

    @Test fun sharedActionsCycleAndMoodChangesTheirPace() {
        assertTrue(moodFrameDuration(83, "HAPPY") < moodFrameDuration(83, "WAITING"))
        assertTrue(moodFrameDuration(83, "WAITING") < moodFrameDuration(83, "HUNGRY"))
        assertTrue(moodFrameDuration(83, "HUNGRY") < moodFrameDuration(83, "SAD"))
        val timeline = RigPreviewTimeline(96, Random(12), allowSharedActions = true)
        val actions = mutableListOf<String?>()
        var previous = 0
        repeat(2400) {
            val frame = timeline.advance()
            if (frame == 10 && previous == 9) actions.add(timeline.sharedAction)
            previous = frame
        }
        assertTrue(actions.size >= 4)
        assertEquals(listOf(null, "reading", "writing", "page_turn"), actions.take(4))
    }

    @Test fun widgetIncludesBothActionsWithoutAddingSlots() {
        val normal = (0 until 16).map { widgetPose(it, 16, 96, false) }
        assertTrue(normal.all { !it.first })
        assertEquals(listOf(0, 6, 12, 18), normal.take(4).map { it.second })
        val mixed = (0 until 16).map { widgetPose(it, 16, 96, true) }
        assertTrue(mixed.take(8).all { !it.first })
        assertTrue(mixed.drop(8).all { it.first })
        assertEquals(listOf(0, 12, 24, 36, 48, 60, 72, 84), mixed.take(8).map { it.second })
        assertEquals(mixed.take(8).map { it.second }, mixed.drop(8).map { it.second })
    }

    @Test fun happyAlternatesWritingAndPageTurningWithoutLosingIdle() {
        val timeline = RigPreviewTimeline(96, Random(14), allowPageTurn = true)
        val actions = mutableListOf<Boolean>()
        var previous = 0
        repeat(1800) {
            val frame = timeline.advance()
            if (frame == 10 && previous == 9) actions.add(timeline.pageTurn)
            if (frame == 0) assertFalse(timeline.pageTurn)
            previous = frame
        }
        assertTrue(actions.size >= 4)
        assertEquals(listOf(false, true, false, true), actions.take(4))
    }

    @Test fun idleBlinksOccurAtRestAndDoNotSkipActions() {
        val timeline = RigPreviewTimeline(96, Random(8), allowIdleBlink = true)
        var blinkTicks = 0
        var actions = 0
        repeat(1600) {
            val frame = timeline.advance()
            if (timeline.blinking) {
                blinkTicks++
                assertEquals(0, frame)
            }
            if (frame == 95) actions++
        }
        assertTrue(blinkTicks >= 2)
        assertTrue(actions > 0)
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
