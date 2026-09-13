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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private const val DRAFT_SAVE_DEBOUNCE_MS = 650L

private enum class AppDestination { HOME, CHAPTERS, GLOSSARY }

private class EditorSession {
    var latest: OpenChapter? = null
    var touched: Boolean = false

    fun open(chapter: OpenChapter) {
        latest = chapter
        touched = false
    }

    fun update(chapter: OpenChapter) {
        latest = chapter
        touched = true
    }

    fun clear() {
        latest = null
        touched = false
    }
}

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
            GlossaryScreen(
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val tokenStore = remember { SecureTokenStore(context) }
    val draftStore = remember { DraftStore(context) }
    val editorSession = remember { EditorSession() }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var token by remember { mutableStateOf(tokenStore.load()) }
    var showSettings by remember { mutableStateOf(token.isBlank()) }
    var files by remember { mutableStateOf<List<ChapterFile>>(emptyList()) }
    var progressByPath by remember { mutableStateOf<Map<String, ChapterProgress>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<OpenChapter?>(null) }
    var draftSaveJob by remember { mutableStateOf<Job?>(null) }
    val api = remember(settings, token) { token.takeIf { it.isNotBlank() }?.let { GitHubApi(settings, it) } }

    suspend fun loadQa(client: GitHubApi, file: ChapterFile): QaFindingsSnapshot? {
        val path = QaFindingsParser.path(file.volume, file.chapter)
        val snapshot = client.getFileOrNull(path) ?: return null
        return QaFindingsSnapshot(path, snapshot.sha, snapshot.content, QaFindingsParser.parse(snapshot.content))
    }

    suspend fun loadChapterProgress(client: GitHubApi, file: ChapterFile): ChapterProgress {
        val remote = client.getFile(file.path)
        val document = IntakeParser.parse(remote.content)
        val qa = runCatching { loadQa(client, file) }.getOrNull()
        val editorSha = QaFindingsParser.sha256(remote.content)
        return ChapterProgress(
            englishSupplied = document.englishSupplied,
            englishTotal = document.englishTotal,
            editorReviewComplete = document.editorReviewComplete,
            qaActive = qa?.document?.active(editorSha)?.size ?: 0,
            qaTotal = qa?.document?.findings?.size ?: 0,
        )
    }

    suspend fun persistDraft(chapter: OpenChapter) {
        withContext(Dispatchers.IO) {
            if (chapter.raw == chapter.remote.content) {
                draftStore.delete(chapter.file.path)
            } else {
                draftStore.save(chapter.file.path, chapter.remote.sha, chapter.raw)
            }
        }
    }

    fun scheduleDraftSave(next: OpenChapter) {
        editorSession.update(next)
        draftSaveJob?.cancel()
        draftSaveJob = scope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            persistDraft(next)
        }
    }

    fun closeEditor() {
        val current = editorSession.latest ?: open
        val touched = editorSession.touched
        val pending = draftSaveJob
        draftSaveJob = null
        editorSession.clear()
        open = null
        if (current != null && touched) {
            scope.launch {
                pending?.cancelAndJoin()
                persistDraft(current)
            }
        } else {
            pending?.cancel()
        }
    }

    fun refresh() {
        val client = api ?: return
        scope.launch {
            busy = true
            try {
                val listed = client.listIntakeFiles()
                files = listed
                progressByPath = emptyMap()
                listed.forEach { file ->
                    launch {
                        runCatching { loadChapterProgress(client, file) }
                            .onSuccess { progress -> progressByPath = progressByPath + (file.path to progress) }
                    }
                }
                notice = null
            } catch (t: Throwable) {
                notice = t.message ?: "Could not refresh chapter list."
            } finally {
                busy = false
            }
        }
    }

    fun openFile(file: ChapterFile) {
        val client = api ?: return
        scope.launch {
            busy = true
            try {
                val remote = client.getFile(file.path)
                val draft = withContext(Dispatchers.IO) { draftStore.load(file.path) }
                val raw = if (draft?.baseSha == remote.sha) draft.raw else remote.content
                val document = IntakeParser.parse(raw)
                val qa = runCatching { loadQa(client, file) }.getOrNull()
                val opened = OpenChapter(file, remote, raw, document, qa)
                editorSession.open(opened)
                open = opened
                if (draft != null && draft.baseSha != remote.sha) {
                    notice = "A local draft exists for an older GitHub revision. The latest remote copy was opened to avoid an unsafe overwrite."
                }
            } catch (t: Throwable) {
                notice = t.message ?: "Could not open ${file.path}."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(api, showSettings) { if (api != null && !showSettings) refresh() }

    if (showSettings) {
        BackHandler(enabled = !busy) {
            if (token.isNotBlank()) showSettings = false else onExitToHome()
        }
        SettingsScreen(
            currentSettings = settings,
            currentToken = token,
            busy = busy,
            canCancel = token.isNotBlank(),
            onCancel = { showSettings = false },
            onSave = { next, nextToken ->
                scope.launch {
                    busy = true
                    try {
                        val candidate = GitHubApi(next, nextToken)
                        val login = candidate.verifyUser()
                        settingsStore.save(next)
                        tokenStore.save(nextToken)
                        settings = next
                        token = nextToken
                        showSettings = false
                        notice = "Connected as $login."
                    } catch (t: Throwable) {
                        notice = t.message ?: "Could not verify GitHub connection."
                    } finally {
                        busy = false
                    }
                }
            },
        )
        return
    }

    val chapter = open
    if (chapter != null) {
        BackHandler(enabled = !busy) { closeEditor() }
        EditorScreen(
            initial = chapter,
            busy = busy,
            notice = notice,
            onBack = ::closeEditor,
            onDraft = ::scheduleDraftSave,
            onCommit = { next, markReviewed ->
                val client = api ?: return@EditorScreen
                scope.launch {
                    busy = true
                    try {
                        draftSaveJob?.cancelAndJoin()
                        draftSaveJob = null
                        withContext(Dispatchers.IO) {
                            draftStore.save(next.file.path, next.remote.sha, next.raw)
                        }
                        val document = if (markReviewed) {
                            require(next.document.englishSupplied == next.document.englishTotal) {
                                "Supply English for every paragraph before marking editor review complete."
                            }
                            next.document.copy(editorReviewComplete = true)
                        } else next.document
                        val raw = IntakeParser.patchDocument(next.raw, document)
                        IntakeParser.validate(raw).getOrThrow()
                        client.updateFile(
                            next.file.path,
                            next.remote.sha,
                            raw,
                            "edit: revise ch_${next.file.chapter.toString().padStart(4, '0')} English",
                        )
                        withContext(Dispatchers.IO) { draftStore.delete(next.file.path) }
                        editorSession.clear()
                        notice = "Committed to ${settings.owner}/${settings.repo}."
                        open = null
                        onExitToHome()
                    } catch (t: Throwable) {
                        notice = t.message ?: "Commit failed."
                    } finally {
                        busy = false
                    }
                }
            },
            onOverride = { next, findingId, reason ->
                val client = api ?: return@EditorScreen
                val snapshot = next.qa ?: return@EditorScreen
                scope.launch {
                    busy = true
                    try {
                        val override = QaOverride(reason, Instant.now().toString(), client.verifyUser(), QaFindingsParser.sha256(next.raw))
                        val document = snapshot.document.withOverride(findingId, override)
                        val raw = QaFindingsParser.serialize(document)
                        val sha = client.updateFile(snapshot.path, snapshot.sha, raw, "qa: override $findingId")
                        val updated = next.copy(qa = snapshot.copy(sha = sha.ifBlank { snapshot.sha }, raw = raw, document = document))
                        editorSession.latest = updated
                        open = updated
                        notice = "QA override committed."
                    } catch (t: Throwable) {
                        notice = t.message ?: "Could not commit QA override."
                    } finally {
                        busy = false
                    }
                }
            },
        )
    } else {
        BackHandler(enabled = !busy) { onExitToHome() }
        ChapterListScreen(
            files = files,
            progressByPath = progressByPath,
            busy = busy,
            notice = notice,
            onBack = onExitToHome,
            onRefresh = ::refresh,
            onSettings = { showSettings = true },
            onOpen = ::openFile,
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
