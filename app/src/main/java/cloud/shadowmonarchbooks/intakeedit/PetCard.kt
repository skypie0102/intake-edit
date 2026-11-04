package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun TranslationPetCard() {
    val context = LocalContext.current
    val store = remember(context) { PetStore(context) }
    var status by remember { mutableStateOf(store.status()) }
    LaunchedEffect(store) {
        while (true) {
            status = store.status()
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
            PetPortrait(status.mood, message)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Translation companion · Lv. ${status.level}", fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Text(if (status.fedToday) "Daily goal complete" else "Tag a chapter for QA to feed your companion",
                    style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { status.levelProgress / 5f }, modifier = Modifier.fillMaxWidth())
                Text("${status.levelProgress}/5 fed days to next level · ${status.fedDays} total",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PetPortrait(mood: PetMood, description: String) {
    val happy = mood == PetMood.HAPPY
    val sad = mood == PetMood.HUNGRY || mood == PetMood.SAD
    val body = if (sad) Color(0xFF9284AD) else Color(0xFFB59CE7)
    val ink = Color(0xFF3C2C55)
    Canvas(Modifier.size(92.dp).semantics { contentDescription = "Companion: $description" }) {
        val w = size.width
        val h = size.height
        drawOval(Color(0xFFE8DDFB), topLeft = Offset(w * .04f, h * .04f), size = Size(w * .92f, h * .92f))
        drawCircle(body, w * .16f, Offset(w * .26f, h * .26f))
        drawCircle(body, w * .16f, Offset(w * .74f, h * .26f))
        drawOval(body, topLeft = Offset(w * .14f, h * .2f), size = Size(w * .72f, h * .64f))
        drawCircle(ink, w * .035f, Offset(w * .37f, h * .48f))
        drawCircle(ink, w * .035f, Offset(w * .63f, h * .48f))
        val mouthTop = if (sad) .65f else .56f
        val start = if (sad) 200f else 20f
        drawArc(ink, start, if (sad) 140f else 140f, false,
            topLeft = Offset(w * .42f, h * mouthTop), size = Size(w * .16f, h * .1f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .025f, cap = StrokeCap.Round))
        if (happy) {
            drawCircle(Color(0xFFFF9FB6), w * .04f, Offset(w * .27f, h * .59f))
            drawCircle(Color(0xFFFF9FB6), w * .04f, Offset(w * .73f, h * .59f))
        }
    }
}
