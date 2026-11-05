package cloud.shadowmonarchbooks.intakeedit

/** Frames are generated from the editable companion SVG. */
private val happyFrames = intArrayOf(
    R.drawable.book_spirit_happy_idle_00, R.drawable.book_spirit_happy_idle_01, R.drawable.book_spirit_happy_idle_02,
    R.drawable.book_spirit_happy_idle_03, R.drawable.book_spirit_happy_idle_04, R.drawable.book_spirit_happy_idle_05,
    R.drawable.book_spirit_happy_idle_06, R.drawable.book_spirit_happy_idle_07, R.drawable.book_spirit_happy_idle_08,
    R.drawable.book_spirit_happy_idle_09, R.drawable.book_spirit_happy_idle_10, R.drawable.book_spirit_happy_idle_11,
    R.drawable.book_spirit_happy_idle_12, R.drawable.book_spirit_happy_idle_13, R.drawable.book_spirit_happy_idle_14,
    R.drawable.book_spirit_happy_idle_15, R.drawable.book_spirit_happy_idle_16, R.drawable.book_spirit_happy_idle_17,
    R.drawable.book_spirit_happy_idle_18, R.drawable.book_spirit_happy_idle_19, R.drawable.book_spirit_happy_idle_20,
    R.drawable.book_spirit_happy_idle_21, R.drawable.book_spirit_happy_idle_22, R.drawable.book_spirit_happy_idle_23,
)

private val waitingFrames = intArrayOf(
    R.drawable.book_spirit_waiting_idle_00, R.drawable.book_spirit_waiting_idle_01, R.drawable.book_spirit_waiting_idle_02,
    R.drawable.book_spirit_waiting_idle_03, R.drawable.book_spirit_waiting_idle_04, R.drawable.book_spirit_waiting_idle_05,
    R.drawable.book_spirit_waiting_idle_06, R.drawable.book_spirit_waiting_idle_07, R.drawable.book_spirit_waiting_idle_08,
    R.drawable.book_spirit_waiting_idle_09, R.drawable.book_spirit_waiting_idle_10, R.drawable.book_spirit_waiting_idle_11,
    R.drawable.book_spirit_waiting_idle_12, R.drawable.book_spirit_waiting_idle_13, R.drawable.book_spirit_waiting_idle_14,
    R.drawable.book_spirit_waiting_idle_15, R.drawable.book_spirit_waiting_idle_16, R.drawable.book_spirit_waiting_idle_17,
    R.drawable.book_spirit_waiting_idle_18, R.drawable.book_spirit_waiting_idle_19, R.drawable.book_spirit_waiting_idle_20,
    R.drawable.book_spirit_waiting_idle_21, R.drawable.book_spirit_waiting_idle_22, R.drawable.book_spirit_waiting_idle_23,
)

private val hungryFrames = intArrayOf(
    R.drawable.book_spirit_hungry_idle_00, R.drawable.book_spirit_hungry_idle_01, R.drawable.book_spirit_hungry_idle_02,
    R.drawable.book_spirit_hungry_idle_03, R.drawable.book_spirit_hungry_idle_04, R.drawable.book_spirit_hungry_idle_05,
    R.drawable.book_spirit_hungry_idle_06, R.drawable.book_spirit_hungry_idle_07, R.drawable.book_spirit_hungry_idle_08,
    R.drawable.book_spirit_hungry_idle_09, R.drawable.book_spirit_hungry_idle_10, R.drawable.book_spirit_hungry_idle_11,
    R.drawable.book_spirit_hungry_idle_12, R.drawable.book_spirit_hungry_idle_13, R.drawable.book_spirit_hungry_idle_14,
    R.drawable.book_spirit_hungry_idle_15, R.drawable.book_spirit_hungry_idle_16, R.drawable.book_spirit_hungry_idle_17,
    R.drawable.book_spirit_hungry_idle_18, R.drawable.book_spirit_hungry_idle_19, R.drawable.book_spirit_hungry_idle_20,
    R.drawable.book_spirit_hungry_idle_21, R.drawable.book_spirit_hungry_idle_22, R.drawable.book_spirit_hungry_idle_23,
)

private val sadFrames = intArrayOf(
    R.drawable.book_spirit_sad_idle_00, R.drawable.book_spirit_sad_idle_01, R.drawable.book_spirit_sad_idle_02,
    R.drawable.book_spirit_sad_idle_03, R.drawable.book_spirit_sad_idle_04, R.drawable.book_spirit_sad_idle_05,
    R.drawable.book_spirit_sad_idle_06, R.drawable.book_spirit_sad_idle_07, R.drawable.book_spirit_sad_idle_08,
    R.drawable.book_spirit_sad_idle_09, R.drawable.book_spirit_sad_idle_10, R.drawable.book_spirit_sad_idle_11,
    R.drawable.book_spirit_sad_idle_12, R.drawable.book_spirit_sad_idle_13, R.drawable.book_spirit_sad_idle_14,
    R.drawable.book_spirit_sad_idle_15, R.drawable.book_spirit_sad_idle_16, R.drawable.book_spirit_sad_idle_17,
    R.drawable.book_spirit_sad_idle_18, R.drawable.book_spirit_sad_idle_19, R.drawable.book_spirit_sad_idle_20,
    R.drawable.book_spirit_sad_idle_21, R.drawable.book_spirit_sad_idle_22, R.drawable.book_spirit_sad_idle_23,
)

internal fun companionFrames(mood: PetMood): IntArray = when (mood) {
    PetMood.NEW, PetMood.WAITING -> waitingFrames
    PetMood.HAPPY -> happyFrames
    PetMood.HUNGRY -> hungryFrames
    PetMood.SAD -> sadFrames
}
