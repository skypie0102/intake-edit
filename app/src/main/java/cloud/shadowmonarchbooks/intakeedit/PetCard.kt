package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
private fun AnimatedBookSpirit(mood: PetMood, description: String) {
    val frames = companionFrames(mood)
    var frame by remember(mood) { mutableIntStateOf(0) }
    val interval = when (mood) {
        PetMood.HAPPY -> 280L
        PetMood.NEW, PetMood.WAITING -> 350L
        PetMood.HUNGRY -> 450L
        PetMood.SAD -> 500L
    }
    LaunchedEffect(mood) {
        while (true) {
            delay(interval)
            frame = (frame + 1) % frames.size
        }
    }
    Crossfade(targetState = frame, animationSpec = tween(100), label = "companion idle") { index ->
        Image(painterResource(frames[index]), contentDescription = "Companion: $description", modifier = Modifier.size(110.dp))
    }
}
