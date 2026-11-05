package cloud.shadowmonarchbooks.intakeedit

/** Native vector pieces and widget poses are generated from the approved SVG. */
internal data class CompanionRigAssets(
    val head: Int, val eyes: Int, val blink: Int,
    val leftArm: Int, val rightArm: Int, val symbol: Int,
)

private val newRig = CompanionRigAssets(
    head = R.drawable.companion_rig_new_head,
    eyes = R.drawable.companion_rig_new_eyes,
    blink = R.drawable.companion_rig_new_blink,
    leftArm = R.drawable.companion_rig_new_left_arm,
    rightArm = R.drawable.companion_rig_new_right_arm,
    symbol = R.drawable.companion_rig_new_symbol,
)

private val newWidget = intArrayOf(
    R.drawable.companion_widget_new_00, R.drawable.companion_widget_new_01, R.drawable.companion_widget_new_02, R.drawable.companion_widget_new_03,
    R.drawable.companion_widget_new_04, R.drawable.companion_widget_new_05, R.drawable.companion_widget_new_06, R.drawable.companion_widget_new_07,
    R.drawable.companion_widget_new_08, R.drawable.companion_widget_new_09, R.drawable.companion_widget_new_10, R.drawable.companion_widget_new_11,
    R.drawable.companion_widget_new_12, R.drawable.companion_widget_new_13, R.drawable.companion_widget_new_14, R.drawable.companion_widget_new_15,
)

private val happyRig = CompanionRigAssets(
    head = R.drawable.companion_rig_happy_head,
    eyes = R.drawable.companion_rig_happy_eyes,
    blink = R.drawable.companion_rig_happy_blink,
    leftArm = R.drawable.companion_rig_happy_left_arm,
    rightArm = R.drawable.companion_rig_happy_right_arm,
    symbol = R.drawable.companion_rig_happy_symbol,
)

private val happyWidget = intArrayOf(
    R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_03,
    R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00,
    R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00,
    R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00, R.drawable.companion_widget_happy_00,
)

private val waitingRig = CompanionRigAssets(
    head = R.drawable.companion_rig_waiting_head,
    eyes = R.drawable.companion_rig_waiting_eyes,
    blink = R.drawable.companion_rig_waiting_blink,
    leftArm = R.drawable.companion_rig_waiting_left_arm,
    rightArm = R.drawable.companion_rig_waiting_right_arm,
    symbol = R.drawable.companion_rig_waiting_symbol,
)

private val waitingWidget = intArrayOf(
    R.drawable.companion_widget_waiting_00, R.drawable.companion_widget_waiting_01, R.drawable.companion_widget_waiting_02, R.drawable.companion_widget_waiting_03,
    R.drawable.companion_widget_waiting_04, R.drawable.companion_widget_waiting_05, R.drawable.companion_widget_waiting_06, R.drawable.companion_widget_waiting_07,
    R.drawable.companion_widget_waiting_08, R.drawable.companion_widget_waiting_09, R.drawable.companion_widget_waiting_10, R.drawable.companion_widget_waiting_11,
    R.drawable.companion_widget_waiting_12, R.drawable.companion_widget_waiting_13, R.drawable.companion_widget_waiting_14, R.drawable.companion_widget_waiting_15,
)

private val hungryRig = CompanionRigAssets(
    head = R.drawable.companion_rig_hungry_head,
    eyes = R.drawable.companion_rig_hungry_eyes,
    blink = R.drawable.companion_rig_hungry_blink,
    leftArm = R.drawable.companion_rig_hungry_left_arm,
    rightArm = R.drawable.companion_rig_hungry_right_arm,
    symbol = R.drawable.companion_rig_hungry_symbol,
)

private val hungryWidget = intArrayOf(
    R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_03,
    R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00,
    R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00,
    R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00, R.drawable.companion_widget_hungry_00,
)

private val sadRig = CompanionRigAssets(
    head = R.drawable.companion_rig_sad_head,
    eyes = R.drawable.companion_rig_sad_eyes,
    blink = R.drawable.companion_rig_sad_blink,
    leftArm = R.drawable.companion_rig_sad_left_arm,
    rightArm = R.drawable.companion_rig_sad_right_arm,
    symbol = R.drawable.companion_rig_sad_symbol,
)

private val sadWidget = intArrayOf(
    R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_03,
    R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00,
    R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00,
    R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00, R.drawable.companion_widget_sad_00,
)

internal fun companionRigAssets(mood: PetMood): CompanionRigAssets = when (mood) {
    PetMood.NEW -> newRig
    PetMood.HAPPY -> happyRig
    PetMood.WAITING -> waitingRig
    PetMood.HUNGRY -> hungryRig
    PetMood.SAD -> sadRig
}

internal fun companionWidgetFrames(mood: PetMood): IntArray = when (mood) {
    PetMood.NEW -> newWidget
    PetMood.HAPPY -> happyWidget
    PetMood.WAITING -> waitingWidget
    PetMood.HUNGRY -> hungryWidget
    PetMood.SAD -> sadWidget
}
