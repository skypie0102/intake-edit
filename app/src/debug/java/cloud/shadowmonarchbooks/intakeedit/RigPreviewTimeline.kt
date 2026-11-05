package cloud.shadowmonarchbooks.intakeedit

import kotlin.random.Random

internal fun transitionAnchor(moodIsNew: Boolean) = if (moodIsNew) 36 else 0

/** Advance through authored frames, wrapping at the end, rather than reverse a gesture. */
internal fun nextTransitionFrame(frame: Int, frameCount: Int): Int {
    require(frameCount > 0 && frame in 0 until frameCount)
    return (frame + 1) % frameCount
}

/** Select evenly spaced widget poses; Happy can divide its slots between actions. */
internal fun widgetPose(slot: Int, slots: Int, frameCount: Int, hasPageTurn: Boolean): Pair<Boolean, Int> {
    require(slots > 0 && slot in 0 until slots && frameCount >= slots && frameCount % slots == 0)
    if (!hasPageTurn) return false to (slot * frameCount / slots)
    require(slots % 2 == 0)
    val perAction = slots / 2
    return (slot >= perAction) to ((slot % perAction) * frameCount / perAction)
}

/** Preview-only timing. Idle frames stay ahead of the first scripted action. */
internal class RigPreviewTimeline(
    private val frameCount: Int,
    private val random: Random = Random.Default,
    startWithAction: Boolean = false,
    private val allowIdleBlink: Boolean = false,
    private val allowPageTurn: Boolean = false,
) {
    init { require(frameCount >= 16) }

    private val idleLast = minOf(9, frameCount - 1)
    private var frame = 0
    private var idleStep = 0
    private var idleRemaining = if (startWithAction) 0 else nextIdleLength()
    private var playingAction = startWithAction
    private var blinkCountdown = random.nextInt(16, 52)
    private var blinkTicks = 0
    var blinking: Boolean = false
        private set
    var pageTurn: Boolean = false
        private set
    private var nextPageTurn = false

    private fun nextIdleLength() = random.nextInt(36, 109)

    /** Every call advances one frame interval. The action is never skipped. */
    fun advance(): Int {
        blinking = false
        if (playingAction) {
            frame++
            if (frame >= frameCount) {
                frame = 0
                idleStep = 0
                idleRemaining = nextIdleLength()
                playingAction = false
                pageTurn = false
            }
            return frame
        }
        if (blinkTicks > 0) {
            blinkTicks--
            blinking = true
            return 0
        }
        if (allowIdleBlink && idleStep == 0 && idleRemaining > 0 && blinkCountdown <= 0) {
            blinkCountdown = random.nextInt(16, 52)
            blinkTicks = 1
            blinking = true
            return 0
        }
        blinkCountdown--
        if (idleRemaining-- <= 0 && idleStep == 0) {
            playingAction = true
            pageTurn = allowPageTurn && nextPageTurn
            if (allowPageTurn) nextPageTurn = !nextPageTurn
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
