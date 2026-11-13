package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Picture
import android.widget.RemoteViews
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val URL_PACK = "https://raw.githubusercontent.com/skypie0102/intake-edit/preview/companion-rig-first-action/rig-preview/animation-pack.json"
private const val MAX_BYTES = 8_000_000
private const val DEMO_PREFS = "rig_preview_demo"
private const val DEMO_KEY = "demo_mood"

private data class RigPack(
    val pictures: Map<PetMood, List<Picture>>,
    val idleBlinks: Map<PetMood, Picture>,
    val pageTurn: List<Picture>,
    val sharedActions: Map<PetMood, Map<String, List<Picture>>>,
    val frameMs: Long,
)

private object RigPackStore {
    @Volatile var current: RigPack? = null
    private fun cache(context: Context) = File(context.filesDir, "rig-preview-pack.json")

    private fun parse(raw: String): RigPack {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Animation pack is too large" }
        val root = JSONObject(raw)
        require(root.getInt("format") == 1) { "This preview app cannot read the animation pack format" }
        val frameMs = root.getLong("frame_ms")
        require(frameMs in 40..500) { "Invalid animation speed" }
        val source = root.getJSONObject("moods")
        fun parsePicture(svg: String): Picture {
            require(svg.length <= 30_000 && !Regex("(?i)<!|<script|<image|<use|href\\s*=").containsMatchIn(svg)) { "Unsupported SVG content" }
            require(!Regex("url\\((?!#(?:paper|behind-book)\\))", RegexOption.IGNORE_CASE).containsMatchIn(svg)) { "External resources are not allowed" }
            return SVG.getFromString(svg).renderToPicture(320, 320)
        }
        val pictures = PetMood.entries.associateWith { mood ->
            val array = source.getJSONArray(mood.name.lowercase())
            require(array.length() in 16..96 && array.length() % 16 == 0) { "Invalid frame count" }
            List(array.length()) { index ->
                parsePicture(array.getString(index))
            }
        }
        val idleSource = root.optJSONObject("idle_blinks")
        val idleBlinks = if (idleSource == null) emptyMap() else PetMood.entries.associateWith {
            parsePicture(idleSource.getString(it.name.lowercase()))
        }
        val pageSource = root.optJSONArray("page_turn")
        val pageTurn = if (pageSource == null) emptyList() else {
            require(pageSource.length() == 96) { "Invalid page turn frame count" }
            List(pageSource.length()) { parsePicture(pageSource.getString(it)) }
        }
        val sharedSource = root.optJSONObject("shared_actions")
        val shared = if (sharedSource == null) emptyMap() else PetMood.entries.associateWith { mood ->
            val entries = sharedSource.getJSONObject(mood.name.lowercase())
            listOf("reading", "writing", "page_turn").associateWith { action ->
                val sequence = entries.getJSONArray(action)
                require(sequence.length() in listOf(32, 48)) { "Invalid shared action frame count" }
                List(sequence.length()) { parsePicture(sequence.getString(it)) }
            }
        }
        return RigPack(pictures, idleBlinks, pageTurn, shared, frameMs)
    }

    fun load(context: Context): RigPack {
        current?.let { return it }
        val pack = runCatching { parse(cache(context).readText()) }
            .getOrElse { parse(context.assets.open("companion/animation-pack.json").bufferedReader().use { it.readText() }) }
        current = pack
        return pack
    }

    fun update(context: Context): RigPack {
        val connection = URL(URL_PACK).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            check(connection.responseCode == 200) { "Update unavailable (HTTP ${connection.responseCode})" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES) { "Animation pack is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val raw = bytes.toString(Charsets.UTF_8)
            val pack = parse(raw) // Validate the entire pack before replacing the working cache.
            val temporary = File(context.filesDir, "rig-preview-pack.tmp")
            temporary.writeText(raw)
            check(temporary.renameTo(cache(context))) { "Unable to save the animation update" }
            current = pack
            return pack
        } finally { connection.disconnect() }
    }
}

internal object RigRemoteFrames {
    fun apply(context: Context, mood: PetMood, views: RemoteViews, frameViews: List<Int>): Boolean {
        val pack = RigPackStore.current ?: runCatching { RigPackStore.load(context) }.getOrNull() ?: return false
        val frames = pack.pictures[mood] ?: return false
        if (frames.size % frameViews.size != 0) return false
        fun bitmap(picture: Picture): Bitmap {
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            canvas.scale(.5f, .5f)
            canvas.drawPicture(picture)
            return bitmap
        }
        val reducedMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
        if (reducedMotion) {
            views.setImageViewBitmap(R.id.pet_widget_static, bitmap(frames.first()))
            views.setContentDescription(R.id.pet_widget_static, "Companion: ${mood.name.lowercase()}")
            views.setViewVisibility(R.id.pet_widget_portrait, android.view.View.GONE)
            views.setViewVisibility(R.id.pet_widget_static, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.pet_widget_static, android.view.View.INVISIBLE)
            views.setViewVisibility(R.id.pet_widget_portrait, android.view.View.VISIBLE)
            val actions = pack.sharedActions[mood]
            val hasShared = actions != null && actions.size == 3 && frameViews.size % 4 == 0
            val hasPageTurn = !hasShared && mood == PetMood.HAPPY && pack.pageTurn.size == frames.size && frameViews.size % 2 == 0
            // The widget has 16 slots. Split them between both Happy actions,
            // retaining a readable pace without allocating additional bitmaps.
            val baseInterval = (pack.frameMs * frames.size / frameViews.size).toInt()
            val interval = if (hasShared) (moodFrameDuration(pack.frameMs, mood.name) * 8).toInt()
                           else if (hasPageTurn) baseInterval * 4 / 3 else baseInterval
            views.setInt(R.id.pet_widget_portrait, "setFlipInterval", interval)
            frameViews.forEachIndexed { index, view ->
                val picture = if (hasShared && actions != null) {
                    val perAction = frameViews.size / 4
                    val actionIndex = index / perAction
                    val sequence = when (actionIndex) {
                        0 -> frames
                        1 -> actions.getValue("reading")
                        2 -> actions.getValue("writing")
                        else -> actions.getValue("page_turn")
                    }
                    val sample = (index % perAction) * sequence.size / perAction
                    val readingOffset = if (actionIndex == 1) sequence.size / (perAction * 2) else 0
                    sequence[(sample + readingOffset).coerceAtMost(sequence.lastIndex)]
                } else {
                    val (page, pose) = widgetPose(index, frameViews.size, frames.size, hasPageTurn)
                    if (page) pack.pageTurn[pose] else frames[pose]
                }
                views.setImageViewBitmap(view, bitmap(picture))
            }
            // The launcher can preserve the previously shown child when the
            // mood is replaced. Always begin the new mood at its first pose.
            views.setDisplayedChild(R.id.pet_widget_portrait, 0)
        }
        return true
    }
}

@Composable
internal fun LegacyRigRemotePreview() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(DEMO_PREFS, Context.MODE_PRIVATE) }
    var mood by remember { mutableStateOf(readRigDemoMood(context) ?: PetMood.WAITING) }
    var pack by remember { mutableStateOf<RigPack?>(null) }
    var message by remember { mutableStateOf("Loading animations…") }
    var refreshing by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var displayedMood by remember { mutableStateOf(mood) }
    var idleBlink by remember { mutableStateOf(false) }
    var turningPage by remember { mutableStateOf(false) }
    var sharedAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshCount) {
        refreshing = true
        val local = withContext(Dispatchers.IO) { runCatching { RigPackStore.load(context) } }
        local.onSuccess { pack = it; TranslationPetWidget.refreshAll(context) }
            .onFailure { message = "Could not load bundled animations: ${it.message}" }
        val updated = withContext(Dispatchers.IO) { runCatching { RigPackStore.update(context) } }
        updated.onSuccess {
            pack = it
            frame = 0
            message = "Animations are up to date."
            TranslationPetWidget.refreshAll(context)
        }.onFailure { message = "Using saved animations. Update failed: ${it.message}" }
        refreshing = false
    }
    LaunchedEffect(pack, mood) {
        frame = 0
        displayedMood = mood
        idleBlink = false
        turningPage = false
        sharedAction = null
        var timeline: RigPreviewTimeline? = null
        while (true) {
            val currentPack = pack
            if (currentPack == null) { delay(83); continue }
            val count = currentPack.pictures[displayedMood]?.size ?: continue
            // Honor Android's animation setting without fading the character.
            val enabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
            if (!enabled) {
                timeline = null
                displayedMood = mood
                idleBlink = false
                turningPage = false
                sharedAction = null
                frame = 0
                delay(currentPack.frameMs)
            } else if (count != 96) {
                // Older packs have different pose timing; play them as authored.
                timeline = null
                idleBlink = false
                turningPage = false
                sharedAction = null
                frame = (frame + 1) % count
                delay(currentPack.frameMs)
            } else {
                val active = timeline ?: RigPreviewTimeline(count,
                    startWithAction = displayedMood == PetMood.NEW,
                    allowIdleBlink = currentPack.idleBlinks.containsKey(displayedMood),
                    allowPageTurn = displayedMood == PetMood.HAPPY && currentPack.pageTurn.size == count,
                    allowSharedActions = currentPack.sharedActions[displayedMood]?.size == 3).also { timeline = it }
                frame = active.advance()
                idleBlink = active.blinking
                turningPage = active.pageTurn
                sharedAction = active.sharedAction
                delay(moodFrameDuration(currentPack.frameMs, displayedMood.name))
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rig preview", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                val action = sharedAction
                // A selected mood must never show the outgoing mood, even in
                // the composition before its new playback effect starts.
                val currentPicture = if (idleBlink) pack?.idleBlinks?.get(displayedMood)
                              else if (action != null) {
                                  val sequence = pack?.sharedActions?.get(displayedMood)?.get(action)
                                  sequence?.let { it.getOrNull(frame * it.size / (pack?.pictures?.get(displayedMood)?.size ?: 96)) }
                              }
                              else if (turningPage && displayedMood == PetMood.HAPPY) pack?.pageTurn?.getOrNull(frame)
                              else pack?.pictures?.get(displayedMood)?.getOrNull(frame)
                val picture = selectedPreviewFrame(mood, displayedMood,
                    pack?.pictures?.get(mood)?.firstOrNull(), currentPicture)
                if (picture != null) Canvas(Modifier.size(220.dp)) {
                    val canvas = drawContext.canvas.nativeCanvas
                    val save = canvas.save()
                    canvas.scale(size.width / 320f, size.height / 320f)
                    canvas.drawPicture(picture)
                    canvas.restoreToCount(save)
                } else CircularProgressIndicator()
            }
            Text("Production mood transition", style = MaterialTheme.typography.titleSmall)
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                ProductionBookSpirit(mood, mood.name.lowercase(), 150.dp)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PetMood.entries.forEach { item ->
                    FilterChip(selected = mood == item, onClick = {
                        mood = item
                        prefs.edit().putString(DEMO_KEY, item.name).apply()
                        TranslationPetWidget.refreshAll(context)
                    }, label = { Text(item.name.lowercase().replaceFirstChar { it.titlecase() }) })
                }
            }
            OutlinedButton(onClick = { refreshCount++ }, enabled = !refreshing) { Text(if (refreshing) "Checking…" else "Refresh animations") }
        }
    }
}

