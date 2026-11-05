package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
internal fun RigPreviewPanel() { RigRemotePreview() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RigOnlyPreviewScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Companion rig preview") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RigPreviewPanel()
            Text("Select a mood to compare the preview with the production transition.")
            Text("Add Companion — Rig Preview from your launcher to check the widget.")
        }
    }
}
