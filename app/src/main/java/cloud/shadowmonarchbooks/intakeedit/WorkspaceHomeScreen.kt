package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceHomeScreen(
    onOpenChapters: () -> Unit,
    onOpenGlossary: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Intake Edit") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Workspace", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Chapter Intake", fontWeight = FontWeight.Bold)
                    Text("Import rough translations, edit authoritative English, review QA findings, and commit schema-v6 chapter files.")
                    Button(onClick = onOpenChapters) { Text("Open Chapter Intake") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Glossary", fontWeight = FontWeight.Bold)
                    Text("Review whole-volume suggestions, manage approved terminology and QA locks, and export the running glossary in the original file format.")
                    Button(onClick = onOpenGlossary) { Text("Open Glossary") }
                }
            }
        }
    }
}
