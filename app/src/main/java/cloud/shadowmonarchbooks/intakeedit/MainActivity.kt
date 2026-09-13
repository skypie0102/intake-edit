package cloud.shadowmonarchbooks.intakeedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect

private enum class AppDestination { HOME, CHAPTERS, GLOSSARY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IntakeEditTheme { IntakeAppShell() } }
    }
}

@Composable
private fun IntakeEditTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
private fun IntakeAppShell() {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    when (destination) {
        AppDestination.HOME -> WorkspaceHomeScreen(
            onOpenChapters = { destination = AppDestination.CHAPTERS },
            onOpenGlossary = { destination = AppDestination.GLOSSARY },
        )
        AppDestination.CHAPTERS -> ChapterIntakeApp(
            onExitToHome = { destination = AppDestination.HOME },
        )
        AppDestination.GLOSSARY -> {
            BackHandler { destination = AppDestination.HOME }
            GlossaryWorkspaceScreen(
                onBack = { destination = AppDestination.HOME },
                onOpenSettings = { destination = AppDestination.CHAPTERS },
            )
        }
    }
}

private enum class ChapterListFilter(val label: String) { ACTIVE("Active"), COMPLETED("Completed"), ALL("All") }

internal data class ChapterProgress(
    val englishSupplied: Int,
    val englishTotal: Int,
    val editorReviewComplete: Boolean,
    val qaActive: Int = 0,
    val qaTotal: Int = 0,
) {
    val complete: Boolean get() = editorReviewComplete && englishSupplied == englishTotal
}

internal data class OpenChapter(
    val file: ChapterFile,
    val remote: FileSnapshot,
    val raw: String,
    val document: EditorDocument,
    val qa: QaFindingsSnapshot?,
)

@Composable
private fun ChapterIntakeApp(onExitToHome: () -> Unit) {
    val chapterListViewModel: ChapterListViewModel = viewModel()
    val chapterListState by chapterListViewModel.uiState.collectAsStateWithLifecycle()
    val repoSettingsViewModel: RepoSettingsViewModel = viewModel()
    val repoSettingsState by repoSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val editorViewModel: EditorViewModel = viewModel()
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val settings = repoSettingsState.settings
    val token = repoSettingsState.token
    val showSettings = repoSettingsState.showSettings

    fun refresh() {
        chapterListViewModel.refresh(settings, token)
    }

    LaunchedEffect(settings, token, showSettings) {
        if (token.isNotBlank() && !showSettings) refresh()
    }
    LaunchedEffect(editorViewModel) {
        editorViewModel.events.collect { event ->
            if (event == EditorEvent.ReturnHome) onExitToHome()
        }
    }

    if (showSettings) {
        val settingsBusy = repoSettingsState.saving
        BackHandler(enabled = !settingsBusy) {
            if (token.isNotBlank()) repoSettingsViewModel.closeSettings() else onExitToHome()
        }
        SettingsScreen(
            currentSettings = settings,
            currentToken = token,
            busy = settingsBusy,
            notice = repoSettingsState.notice,
            canCancel = token.isNotBlank(),
            onCancel = repoSettingsViewModel::closeSettings,
            onSave = repoSettingsViewModel::save,
        )
        return
    }

    val chapter = editorViewModel.chapterForDisplay()
    if (chapter != null) {
        val editorBusy = editorState.actionInProgress
        BackHandler(enabled = !editorBusy) { editorViewModel.closeEditor() }
        key(chapter.qa?.sha) {
            EditorScreen(
                initial = chapter,
                busy = editorBusy,
                notice = editorState.notice,
                onBack = editorViewModel::closeEditor,
                onDraft = editorViewModel::onDraft,
                onCommit = { next, markReviewed ->
                    editorViewModel.commit(next, markReviewed, settings, token)
                },
                onOverride = { next, findingId, reason ->
                    editorViewModel.overrideQa(next, findingId, reason, settings, token)
                },
            )
        }
    } else {
        val listBusy = chapterListState.refreshing || editorState.loadingPath != null
        BackHandler(enabled = !listBusy) { onExitToHome() }
        ChapterListScreen(
            files = chapterListState.files,
            progressByPath = chapterListState.progressByPath,
            busy = listBusy,
            notice = editorState.notice ?: repoSettingsState.notice ?: chapterListState.notice,
            onBack = onExitToHome,
            onRefresh = ::refresh,
            onSettings = repoSettingsViewModel::openSettings,
            onOpen = { file -> editorViewModel.open(file, settings, token) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListScreen(
    files: List<ChapterFile>,
    progressByPath: Map<String, ChapterProgress>,
    busy: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (ChapterFile) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ChapterListFilter.ACTIVE) }
    val searched = files.filter {
        val q = search.trim()
        q.isBlank() || it.path.contains(q, true) || it.chapter.toString().contains(q)
    }
    val visible = searched.filter { file ->
        val progress = progressByPath[file.path]
        when (filter) {
            ChapterListFilter.ACTIVE -> progress?.complete != true
            ChapterListFilter.COMPLETED -> progress?.complete == true
            ChapterListFilter.ALL -> true
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Chapter Intake") },
            navigationIcon = { IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Refresh, "Refresh") }
                IconButton(onClick = onSettings, enabled = !busy) { Icon(Icons.Default.Settings, "Settings") }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(search, { search = it }, label = { Text("Chapter, path, or filename") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChapterListFilter.entries.forEach { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                }
            }
            if (busy && files.isEmpty()) CircularProgressIndicator()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.path }) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val progress = progressByPath[file.path]
                                Text("Volume ${file.volume} • Chapter ${file.chapter}", fontWeight = FontWeight.Bold)
                                Text(file.path, style = MaterialTheme.typography.bodySmall)
                                if (progress == null) {
                                    Text("English supplied: loading", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("English supplied: ${progress.englishSupplied}/${progress.englishTotal}", style = MaterialTheme.typography.bodySmall)
                                    Text("Review: ${if (progress.editorReviewComplete) "complete" else "pending"}", style = MaterialTheme.typography.bodySmall)
                                    if (progress.qaTotal > 0) Text("QA: ${progress.qaActive} active / ${progress.qaTotal} total", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = { onOpen(file) }, enabled = !busy) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    currentSettings: RepoSettings,
    currentToken: String,
    busy: Boolean,
    notice: String?,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (RepoSettings, String) -> Unit,
) {
    var owner by rememberSaveable { mutableStateOf(currentSettings.owner) }
    var repo by rememberSaveable { mutableStateOf(currentSettings.repo) }
    var branch by rememberSaveable { mutableStateOf(currentSettings.branch) }
    var root by rememberSaveable { mutableStateOf(currentSettings.intakeRoot) }
    var token by rememberSaveable { mutableStateOf(currentToken) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Repository settings") },
            navigationIcon = { if (canCancel) IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Connect directly to GitHub. The token is encrypted with a key held by Android Keystore.")
            OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(root, { root = it }, label = { Text("Editor root") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                token,
                { token = it },
                label = { Text("Fine-grained GitHub token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text("Token permissions: Metadata read + Contents read/write for the translation repository.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { onSave(RepoSettings(owner.trim(), repo.trim(), branch.trim(), root.trim().trim('/')), token.trim()) },
                enabled = !busy && owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator() else Text("Test connection & save")
            }
        }
    }
}
