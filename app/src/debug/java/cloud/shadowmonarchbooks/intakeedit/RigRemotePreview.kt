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
private const val MAX_BYTES = 4_000_000
private const val DEMO_PREFS = "rig_preview_demo"
private const val DEMO_KEY = "demo_mood"

private data class RigPack(
    val pictures: Map<PetMood, List<Picture>>,
    val transitionBlinks: Map<PetMood, Picture>,
    val idleBlinks: Map<PetMood, Picture>,
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
        val blinkSource = root.optJSONObject("transition_blinks")
        val blinks = if (blinkSource == null) emptyMap() else PetMood.entries.associateWith {
            parsePicture(blinkSource.getString(it.name.lowercase()))
        }
        val idleSource = root.optJSONObject("idle_blinks")
        val idleBlinks = if (idleSource == null) emptyMap() else PetMood.entries.associateWith {
            parsePicture(idleSource.getString(it.name.lowercase()))
        }
        return RigPack(pictures, blinks, idleBlinks, frameMs)
    }

    fun load(context: Context): RigPack {
        current?.let { return it }
        val pack = runCatching { parse(cache(context).readText()) }
            .getOrElse { parse(context.assets.open("rig-preview-pack.json").bufferedReader().use { it.readText() }) }
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
            views.setViewVisibility(R.id.pet_widget_portrait, android.view.View.INVISIBLE)
            views.setViewVisibility(R.id.pet_widget_static, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.pet_widget_static, android.view.View.INVISIBLE)
            views.setViewVisibility(R.id.pet_widget_portrait, android.view.View.VISIBLE)
            frameViews.forEachIndexed { index, view ->
                views.setImageViewBitmap(view, bitmap(frames[index * frames.size / frameViews.size]))
            }
        }
        return true
    }
}

@Composable
internal fun RigRemotePreview() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(DEMO_PREFS, Context.MODE_PRIVATE) }
    var mood by remember { mutableStateOf(readRigDemoMood(context) ?: PetMood.WAITING) }
    var pack by remember { mutableStateOf<RigPack?>(null) }
    var message by remember { mutableStateOf("Loading animations…") }
    var refreshing by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var displayedMood by remember { mutableStateOf(mood) }
    var transitionBlink by remember { mutableStateOf(false) }
    var idleBlink by remember { mutableStateOf(false) }
    val requestedMood by rememberUpdatedState(mood)

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
    LaunchedEffect(pack) {
        frame = 0
        displayedMood = requestedMood
        transitionBlink = false
        idleBlink = false
        var timeline: RigPreviewTimeline? = null
        while (true) {
            val currentPack = pack
            if (currentPack == null) { delay(83); continue }
            val count = currentPack.pictures[displayedMood]?.size ?: continue
            // Honor Android's animation setting without fading the character.
            val enabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
            if (!enabled) {
                timeline = null
                displayedMood = requestedMood
                transitionBlink = false
                idleBlink = false
                frame = 0
                delay(currentPack.frameMs)
            } else if (requestedMood != displayedMood) {
                timeline = null
                idleBlink = false
                if (currentPack.pictures.values.all { it.size == 96 } &&
                    currentPack.transitionBlinks.size == PetMood.entries.size) {
                    // Finish the current gesture quickly, preserving its drawing order.
                    // New's raised-head pose precedes its surprise and wave.
                    val anchor = transitionAnchor(displayedMood == PetMood.NEW)
                    while (frame != anchor) {
                        frame = nextTransitionFrame(frame, count)
                        delay(12)
                    }
                    if (requestedMood != displayedMood) {
                        transitionBlink = true
                        delay(80)
                        displayedMood = requestedMood // Use the latest request, even if interrupted.
                        frame = transitionAnchor(displayedMood == PetMood.NEW)
                        delay(80)
                        transitionBlink = false
                    }
                    // The new companion lowers his head to read after the change.
                    while (displayedMood == PetMood.NEW && frame > 0 && requestedMood == displayedMood) {
                        frame--
                        delay(18)
                    }
                } else {
                    displayedMood = requestedMood
                    frame = 0
                }
            } else if (count != 96) {
                // Older packs have different pose timing; play them as authored.
                timeline = null
                idleBlink = false
                frame = (frame + 1) % count
                delay(currentPack.frameMs)
            } else {
                val active = timeline ?: RigPreviewTimeline(count,
                    startWithAction = displayedMood == PetMood.NEW,
                    allowIdleBlink = currentPack.idleBlinks.containsKey(displayedMood)).also { timeline = it }
                frame = active.advance()
                idleBlink = active.blinking
                delay(currentPack.frameMs)
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rig preview", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                val picture = if (transitionBlink) pack?.transitionBlinks?.get(displayedMood)
                              else if (idleBlink) pack?.idleBlinks?.get(displayedMood)
                              else pack?.pictures?.get(displayedMood)?.getOrNull(frame)
                if (picture != null) Canvas(Modifier.size(220.dp)) {
                    val canvas = drawContext.canvas.nativeCanvas
                    val save = canvas.save()
                    canvas.scale(size.width / 320f, size.height / 320f)
                    canvas.drawPicture(picture)
                    canvas.restoreToCount(save)
                } else CircularProgressIndicator()
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

