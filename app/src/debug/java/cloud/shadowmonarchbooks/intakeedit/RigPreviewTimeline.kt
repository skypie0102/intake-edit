package cloud.shadowmonarchbooks.intakeedit

import kotlin.random.Random

internal fun transitionAnchor(moodIsNew: Boolean) = if (moodIsNew) 36 else 0

/** Advance through authored frames, wrapping at the end, rather than reverse a gesture. */
internal fun nextTransitionFrame(frame: Int, frameCount: Int): Int {
    require(frameCount > 0 && frame in 0 until frameCount)
    return (frame + 1) % frameCount
}

/** Preview-only timing. Idle frames stay ahead of the first scripted action. */
internal class RigPreviewTimeline(
    private val frameCount: Int,
    private val random: Random = Random.Default,
    startWithAction: Boolean = false,
) {
    init { require(frameCount >= 16) }

    private val idleLast = minOf(9, frameCount - 1)
    private var frame = 0
    private var idleStep = 0
    private var idleRemaining = if (startWithAction) 0 else nextIdleLength()
    private var playingAction = startWithAction

    private fun nextIdleLength() = random.nextInt(36, 109)

    /** Every call advances one frame interval. The action is never skipped. */
    fun advance(): Int {
        if (playingAction) {
            frame++
            if (frame >= frameCount) {
                frame = 0
                idleStep = 0
                idleRemaining = nextIdleLength()
                playingAction = false
            }
            return frame
        }
        if (idleRemaining-- <= 0 && idleStep == 0) {
            playingAction = true
            frame = 1
            return frame
        }
        // Walk back to the neutral pose before starting an action, avoiding a
        // jump from an arbitrary idle pose into the beginning of the script.
        val period = 2 * idleLast
        idleStep = (idleStep + 1) % period
        frame = if (idleStep <= idleLast) idleStep else period - idleStep
        return frame
    }
}
