package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal const val RIG_PREVIEW_PACKAGE = "cloud.shadowmonarchbooks.intakeedit.rigpreview"
private const val PREFS = "rig_preview_demo"
private const val KEY = "demo_mood"

internal fun rigDemoMood(applicationId: String, value: String?): PetMood? {
    if (applicationId != RIG_PREVIEW_PACKAGE) return null
    return PetMood.entries.firstOrNull { it.name == value }
}

internal fun readRigDemoMood(context: Context): PetMood? =
    if (BuildConfig.DEBUG) rigDemoMood(context.packageName, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) else null

@Composable
internal fun RigPreviewPanel() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var mood by remember { mutableStateOf(readRigDemoMood(context) ?: PetMood.WAITING) }
    fun select(next: PetMood) {
        mood = next
        prefs.edit().putString(KEY, next.name).apply()
        TranslationPetWidget.refreshAll(context)
    }
    LaunchedEffect(Unit) { if (readRigDemoMood(context) == null) select(PetMood.WAITING) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rig preview", style = MaterialTheme.typography.titleMedium)
            Text("Demo moods also update the preview widget. They do not change chapters or companion progress.")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedBookSpirit(mood, "${mood.name.lowercase()} preview", size = 220.dp)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PetMood.entries.forEach { item ->
                    FilterChip(selected = mood == item, onClick = { select(item) }, label = { Text(item.name.lowercase().replaceFirstChar { it.titlecase() }) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RigOnlyPreviewScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Companion rig preview") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RigPreviewPanel()
            Text("New includes a greeting; Waiting includes a thinking action. Other actions are still being developed.")
            Text("Add Companion — Rig Preview from your launcher to check the widget.")
        }
    }
}
