package cloud.shadowmonarchbooks.intakeedit

/** Frames are generated from the editable companion SVG. */
private val happyFrames = intArrayOf(
    R.drawable.book_spirit_happy_idle_00, R.drawable.book_spirit_happy_idle_01, R.drawable.book_spirit_happy_idle_02,
    R.drawable.book_spirit_happy_idle_03, R.drawable.book_spirit_happy_idle_04, R.drawable.book_spirit_happy_idle_05,
    R.drawable.book_spirit_happy_idle_06, R.drawable.book_spirit_happy_idle_07, R.drawable.book_spirit_happy_idle_08,
    R.drawable.book_spirit_happy_idle_09, R.drawable.book_spirit_happy_idle_10, R.drawable.book_spirit_happy_idle_11,
)

private val waitingFrames = intArrayOf(
    R.drawable.book_spirit_waiting_idle_00, R.drawable.book_spirit_waiting_idle_01, R.drawable.book_spirit_waiting_idle_02,
    R.drawable.book_spirit_waiting_idle_03, R.drawable.book_spirit_waiting_idle_04, R.drawable.book_spirit_waiting_idle_05,
    R.drawable.book_spirit_waiting_idle_06, R.drawable.book_spirit_waiting_idle_07, R.drawable.book_spirit_waiting_idle_08,
    R.drawable.book_spirit_waiting_idle_09, R.drawable.book_spirit_waiting_idle_10, R.drawable.book_spirit_waiting_idle_11,
)

private val hungryFrames = intArrayOf(
    R.drawable.book_spirit_hungry_idle_00, R.drawable.book_spirit_hungry_idle_01, R.drawable.book_spirit_hungry_idle_02,
    R.drawable.book_spirit_hungry_idle_03, R.drawable.book_spirit_hungry_idle_04, R.drawable.book_spirit_hungry_idle_05,
    R.drawable.book_spirit_hungry_idle_06, R.drawable.book_spirit_hungry_idle_07, R.drawable.book_spirit_hungry_idle_08,
    R.drawable.book_spirit_hungry_idle_09, R.drawable.book_spirit_hungry_idle_10, R.drawable.book_spirit_hungry_idle_11,
)

private val sadFrames = intArrayOf(
    R.drawable.book_spirit_sad_idle_00, R.drawable.book_spirit_sad_idle_01, R.drawable.book_spirit_sad_idle_02,
    R.drawable.book_spirit_sad_idle_03, R.drawable.book_spirit_sad_idle_04, R.drawable.book_spirit_sad_idle_05,
    R.drawable.book_spirit_sad_idle_06, R.drawable.book_spirit_sad_idle_07, R.drawable.book_spirit_sad_idle_08,
    R.drawable.book_spirit_sad_idle_09, R.drawable.book_spirit_sad_idle_10, R.drawable.book_spirit_sad_idle_11,
)

internal fun companionFrames(mood: PetMood): IntArray = when (mood) {
    PetMood.NEW, PetMood.WAITING -> waitingFrames
    PetMood.HAPPY -> happyFrames
    PetMood.HUNGRY -> hungryFrames
    PetMood.SAD -> sadFrames
}
