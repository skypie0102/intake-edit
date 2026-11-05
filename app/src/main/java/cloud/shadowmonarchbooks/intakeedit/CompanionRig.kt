package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val armRest = floatArrayOf(303f,269f, 303f,255f,310f,245f,322f,247f, 330f,248f,335f,253f,334f,262f, 334f,266f,332f,270f,330f,272f)
private val armThink = floatArrayOf(303f,269f, 293f,263f,282f,256f,277f,248f, 272f,241f,265f,239f,262f,245f, 259f,251f,266f,264f,282f,273f)

internal fun thinkingArmCoordinates(progress: Float): FloatArray {
    val amount = progress.coerceIn(0f, 1f)
    return FloatArray(armRest.size) { i -> armRest[i] + (armThink[i] - armRest[i]) * amount }
}

@Composable
internal fun AnimatedBookSpirit(mood: PetMood, description: String, size: Dp = 110.dp) {
    val assets = companionRigAssets(mood)
    val thought = remember(mood) { Animatable(0f) }
    var blinking by remember(mood) { mutableStateOf(false) }
    LaunchedEffect(mood) {
        if (mood == PetMood.WAITING) {
            while (true) {
                delay(Random.nextLong(8000, 13000))
                thought.animateTo(1f, tween(4000, easing = LinearEasing))
                thought.snapTo(0f)
            }
        }
    }
    LaunchedEffect(mood) {
        while (true) {
            delay(Random.nextLong(3800, 6500))
            blinking = true
            delay(140)
            blinking = false
        }
    }
    val phase = thought.value
    val armAmount = when {
        phase < .29f -> (phase / .29f).coerceIn(0f, 1f)
        phase < .8f -> 1f
        else -> ((1f - phase) / .2f).coerceIn(0f, 1f)
    }
    Box(Modifier.size(size).semantics { contentDescription = "Companion: $description" }) {
        // The face, eyes and monocle share one pivot. The book never moves.
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(.5f, 178f / 310f)
            rotationZ = armAmount * 1.5f
        }) {
            Image(painterResource(assets.head), contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(painterResource(if (blinking) assets.blink else assets.eyes), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Image(painterResource(assets.leftArm), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (mood == PetMood.WAITING) ThinkingArm(armAmount)
        else Image(painterResource(assets.rightArm), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painterResource(R.drawable.companion_rig_book), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (mood == PetMood.WAITING) {
            ThinkingSymbol(phase)
        }
    }
}

@Composable
private fun ThinkingArm(progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val v = thinkingArmCoordinates(progress)
        val path = Path().apply {
            moveTo(v[0], v[1]); cubicTo(v[2], v[3], v[4], v[5], v[6], v[7])
            cubicTo(v[8], v[9], v[10], v[11], v[12], v[13])
            cubicTo(v[14], v[15], v[16], v[17], v[18], v[19])
            close()
        }
        val sx = size.width / 310f
        val sy = size.height / 310f
        withTransform({
            scale(sx, sy, pivot = Offset.Zero)
            translate(-97f, -92f)
            rotate(-6f, pivot = Offset(252f, 270f))
            translate(0f, 10f)
        }) {
            drawPath(path, Color(0xffdfd2f1))
            drawPath(path, Color(0xff705c87), style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun ThinkingSymbol(phase: Float) {
    Canvas(Modifier.fillMaxSize()) {
        if (phase >= .85f) return@Canvas
        withTransform({
            scale(size.width / 310f, size.height / 310f, pivot = Offset.Zero)
            translate(-97f, -92f)
        }) {
            val ink = Color(0xffdcc2ec)
            val dots = listOf(Offset(329f, 207f), Offset(339f, 195f), Offset(348f, 181f))
            val starts = listOf(.14f, .24f, .34f)
            dots.forEachIndexed { i, dot -> if (phase >= starts[i]) drawCircle(ink, 2.5f, dot) }
            val amount = ((phase - .40f) / .24f).coerceIn(0f, 1f)
            if (amount > 0f) {
                val curve = Path().apply {
                    moveTo(352f, 161f)
                    quadraticTo(348f, 152f, 352f, 144f)
                    quadraticTo(356f, 136f, 367f, 137f)
                    quadraticTo(381f, 138f, 382f, 150f)
                    quadraticTo(382f, 160f, 369f, 166f)
                    quadraticTo(363f, 169f, 364f, 176f)
                }
                val measure = PathMeasure().apply { setPath(curve, false) }
                val visible = Path()
                measure.getSegment(0f, measure.length * amount, visible, true)
                drawPath(visible, ink, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
            if (phase >= .64f) drawCircle(ink, 2.5f, Offset(364f, 186.5f))
        }
    }
}
