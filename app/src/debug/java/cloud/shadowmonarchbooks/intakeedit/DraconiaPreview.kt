package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val DRACONIA_URL = "https://raw.githubusercontent.com/skypie0102/intake-edit/preview/draconia-companion/rig-preview/draconia/pack.json"
private const val DRACONIA_MAX = 8_000_000
private data class PreviewClip(val id: String, val label: String, val frameMs: Long, val frames: List<Bitmap>)
private data class PreviewPack(val revision: String, val description: String, val clips: List<PreviewClip>)

private object DraconiaStore {
    private fun cache(context: Context) = File(context.filesDir, "draconia-preview.json")
    private fun parse(bytes: ByteArray): PreviewPack {
        require(bytes.size in 1..DRACONIA_MAX) { "Invalid pack size" }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("format") == 1) { "Unsupported pack format" }
        val revision = root.getString("revision").take(100)
        val description = root.getString("description").take(500)
        val items = root.getJSONArray("clips")
        require(items.length() in 1..12) { "Invalid clip count" }
        var total = 0
        val ids = mutableSetOf<String>()
        val clips = (0 until items.length()).map { n ->
            val item = items.getJSONObject(n)
            val id = item.getString("id")
            require(id.matches(Regex("[a-z0-9_-]{1,40}")) && ids.add(id)) { "Invalid or duplicate clip ID" }
            val label = item.getString("label")
            require(label.length in 1..80) { "Invalid clip label" }
            val ms = item.getLong("frame_ms")
            require(ms in 40..1000) { "Invalid frame duration" }
            val encoded = item.getJSONArray("frames")
            require(encoded.length() in 1..96) { "Invalid frame count" }
            total += encoded.length()
            require(total <= 240) { "Too many frames" }
            val frames = (0 until encoded.length()).map { i ->
                val text = encoded.getString(i)
                require(text.length <= 180_000) { "Frame too large" }
                val data = Base64.decode(text, Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                require(bounds.outWidth in 1..256 && bounds.outHeight in 1..256 && bounds.outMimeType == "image/webp") { "Invalid frame image" }
                BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Could not decode frame")
            }
            PreviewClip(id, label, ms, frames)
        }
        return PreviewPack(revision, description, clips)
    }
    fun load(context: Context): PreviewPack = runCatching { parse(cache(context).readBytes()) }
        .getOrElse { parse(context.assets.open("draconia/pack.json").use { it.readBytes() }) }
    fun update(context: Context): PreviewPack {
        val connection = URL(DRACONIA_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { input ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(result.size() + n <= DRACONIA_MAX) { "Pack too large" }
                    result.write(buffer, 0, n)
                }
                result.toByteArray()
            }
            val pack = parse(bytes)
            val tmp = File(context.filesDir, "draconia-preview.tmp")
            tmp.writeBytes(bytes)
            check(tmp.renameTo(cache(context))) { "Unable to save update" }
            return pack
        } finally { connection.disconnect() }
    }
}

@Composable
internal fun RigRemotePreview() {
    val context = LocalContext.current
    var pack by remember { mutableStateOf<PreviewPack?>(null) }
    var selected by remember { mutableStateOf("") }
    var frame by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Loading previews…") }
    LaunchedEffect(refresh) {
        refreshing = true
        if (pack == null) {
            withContext(Dispatchers.IO) { runCatching { DraconiaStore.load(context) } }
                .onSuccess { pack = it; selected = it.clips.first().id; status = "Showing saved preview." }
                .onFailure { status = "Could not load preview: ${it.message}" }
        }
        withContext(Dispatchers.IO) { runCatching { DraconiaStore.update(context) } }
            .onSuccess {
                pack = it
                if (it.clips.none { clip -> clip.id == selected }) selected = it.clips.first().id
                frame = 0
                status = "Updated from repository."
            }.onFailure { status = "Using saved preview. Update failed: ${it.message}" }
        refreshing = false
    }
    val clip = pack?.clips?.firstOrNull { it.id == selected }
    LaunchedEffect(pack, selected, playing) {
        frame = 0
        if (clip != null && playing) while (true) {
            delay(clip.frameMs)
            frame = (frame + 1) % clip.frames.size
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Draconia animation preview", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
            pack?.let { Text("${it.revision} • ${it.description}", style = MaterialTheme.typography.bodySmall) }
            Box(Modifier.fillMaxWidth().height(245.dp).background(if (dark) Color(0xFF222731) else Color(0xFFF3F3F3)), contentAlignment = Alignment.Center) {
                val image = clip?.frames?.getOrNull(frame)
                if (image != null) Image(image.asImageBitmap(), contentDescription = clip.label, modifier = Modifier.size(220.dp))
                else if (refreshing) CircularProgressIndicator() else Text("No preview available")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pack?.clips?.forEach { item -> FilterChip(selected = selected == item.id, onClick = { selected = item.id }, label = { Text(item.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { playing = !playing }, enabled = clip != null) { Text(if (playing) "Pause" else "Play") }
                OutlinedButton(onClick = { dark = !dark }) { Text(if (dark) "Light background" else "Dark background") }
            }
            Button(onClick = { refresh++ }, enabled = !refreshing) { Text(if (refreshing) "Checking…" else "Refresh animations") }
        }
    }
}
