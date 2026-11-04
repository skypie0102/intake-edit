package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun TranslationPetCard() {
    val context = LocalContext.current
    val store = remember(context) { PetStore(context) }
    var snapshot by remember { mutableStateOf<PetProjectSnapshot?>(null) }
    var settings by remember { mutableStateOf(SettingsStore(context).load()) }
    var token by remember { mutableStateOf(SecureTokenStore(context).load()) }
    LaunchedEffect(Unit) {
        snapshot = PetProjectCache(context).get(settings, token)
        schedulePetProjectSync(context)
        syncPetProject(context)
        while (true) {
            settings = SettingsStore(context).load()
            token = SecureTokenStore(context).load()
            snapshot = PetProjectCache(context).get(settings, token)
            delay(10_000)
        }
    }
    // Until the token and selected repository are verified, no companion is shown.
    val project = snapshot
    if (project == null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Translation companion", fontWeight = FontWeight.Bold)
                Text("Connect your project repository and GitHub token to meet your companion.")
            }
        }
        return
    }
    val completed = project.completed
    if (completed == null) {
        Card(Modifier.fillMaxWidth()) {
            Text("Connected. Waiting for a current chapter progress index…", Modifier.padding(16.dp))
        }
        return
    }
    var status by remember(settings, completed) { mutableStateOf(store.status(settings, completed)) }
    LaunchedEffect(settings, completed) {
        while (true) {
            status = store.status(settings, completed)
            delay(60_000)
        }
    }
    val message = when (status.mood) {
        PetMood.NEW -> "Ready for your first chapter!"
        PetMood.HAPPY -> "Fed and happy today!"
        PetMood.WAITING -> "A new day, a new chapter!"
        PetMood.HUNGRY -> "Getting hungry for a chapter."
        PetMood.SAD -> "Missing you and your chapters."
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedBookSpirit(status.mood, message)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Translation companion · Lv. ${status.level}", fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Text(if (status.fedToday) "Daily goal complete" else "Tag a chapter for QA to feed your companion", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { status.levelProgress / 5f }, modifier = Modifier.fillMaxWidth())
                Text("${status.levelProgress}/5 completed chapters to next level · $completed total", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun petPortrait(mood: PetMood): Int = when (mood) {
    PetMood.NEW, PetMood.WAITING -> R.drawable.book_spirit_waiting
    PetMood.HAPPY -> R.drawable.book_spirit_happy
    PetMood.HUNGRY -> R.drawable.book_spirit_hungry
    PetMood.SAD -> R.drawable.book_spirit_sad
}

@Composable
private fun AnimatedBookSpirit(mood: PetMood, description: String) {
    val transition = rememberInfiniteTransition(label = "companion movement")
    val duration = when (mood) { PetMood.HAPPY -> 1100; PetMood.WAITING, PetMood.NEW -> 1800; else -> 2800 }
    val bob by transition.animateFloat(initialValue = 0f, targetValue = if (mood == PetMood.HAPPY) -7f else -3f,
        animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse), label = "float")
    val tilt by transition.animateFloat(initialValue = -1.8f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(duration + 500), RepeatMode.Reverse), label = "tilt")
    val breathe by transition.animateFloat(initialValue = .98f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(duration + 250), RepeatMode.Reverse), label = "breathe")
    var blinking by remember(mood) { mutableStateOf(false) }
    LaunchedEffect(mood) {
        while (true) {
            delay(kotlin.random.Random.nextLong(3200L, 5400L))
            blinking = true
            delay(150)
            blinking = false
            if (mood == PetMood.HAPPY && kotlin.random.Random.nextInt(3) == 0) {
                delay(130)
                blinking = true
                delay(150)
                blinking = false
            }
        }
    }
    val image = when {
        blinking && mood == PetMood.HAPPY -> R.drawable.book_spirit_happy_blink
        blinking && (mood == PetMood.NEW || mood == PetMood.WAITING) -> R.drawable.book_spirit_waiting_blink
        else -> petPortrait(mood)
    }
    Image(painterResource(image), contentDescription = "Companion: $description",
        modifier = Modifier.size(110.dp).graphicsLayer {
            translationY = bob * density
            rotationZ = tilt
            scaleX = breathe
            scaleY = breathe
        })
}
