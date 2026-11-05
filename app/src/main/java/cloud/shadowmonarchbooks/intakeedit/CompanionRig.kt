package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val armRest = floatArrayOf(303f,269f, 303f,255f,310f,245f,322f,247f, 330f,248f,335f,253f,334f,262f, 334f,266f,332f,270f,330f,272f)
private val armThink = floatArrayOf(303f,269f, 293f,263f,282f,256f,277f,248f, 272f,241f,265f,239f,262f,245f, 259f,251f,266f,264f,282f,273f)

internal fun thinkingArmCoordinates(progress: Float): FloatArray {
    val amount = progress.coerceIn(0f, 1f)
    return FloatArray(armRest.size) { i -> armRest[i] + (armThink[i] - armRest[i]) * amount }
}

@Composable
internal fun AnimatedBookSpirit(mood: PetMood, description: String) {
    val assets = companionRigAssets(mood)
    val thought = remember(mood) { Animatable(0f) }
    var blinking by remember(mood) { mutableStateOf(false) }
    LaunchedEffect(mood) {
        if (mood == PetMood.WAITING) {
            while (true) {
                delay(Random.nextLong(8000, 13000))
                thought.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
                delay(1300)
                thought.animateTo(0f, tween(750, easing = FastOutSlowInEasing))
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
    Box(Modifier.size(110.dp).semantics { contentDescription = "Companion: $description" }) {
        // The face, eyes and monocle share one pivot. The book never moves.
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(.5f, 178f / 310f)
            rotationZ = thought.value * 1.5f
        }) {
            Image(painterResource(assets.head), contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(painterResource(if (blinking) assets.blink else assets.eyes), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Image(painterResource(assets.leftArm), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (mood == PetMood.WAITING) ThinkingArm(thought.value)
        else Image(painterResource(assets.rightArm), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painterResource(R.drawable.companion_rig_book), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painterResource(assets.symbol), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ThinkingArm(progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val v = thinkingArmCoordinates(progress)
        val path = Path().apply {
            moveTo(v[0], v[1]); cubicTo(v[2], v[3], v[4], v[5], v[6], v[7])
            cubicTo(v[8], v[9], v[10], v[11], v[12], v[13])
            cubicTo(v[14], v[15], v[16], v[17], v[18], v[19]); close()
        }
        val sx = size.width / 310f
        val sy = size.height / 310f
        withTransform({
            scale(sx, sy, pivot = Offset.Zero)
            translate(-97f, -92f)
            rotate(-6f, pivot = Offset(252f, 270f))
        }) {
            drawPath(path, Color(0xffdfd2f1))
            drawPath(path, Color(0xff705c87), style = Stroke(width = 4f))
        }
    }
}
