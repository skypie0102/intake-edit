package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val DRACONIA_URL = "https://raw.githubusercontent.com/skypie0102/intake-edit/preview/draconia-companion/rig-preview/draconia/pack.json"
private object DraconiaStore {
    private fun cache(context: Context) = File(context.filesDir, "draconia-preview.json")
    private fun parse(text: String): PreviewPack = parsePreviewPack(text) { encoded ->
        val data = Base64.decode(encoded, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == "image/webp") { "Invalid frame image" }
    }
    fun load(context: Context): PreviewPack = runCatching { parse(cache(context).readText()) }
        .getOrElse { parse(context.assets.open("draconia/pack.json").bufferedReader().use { it.readText() }) }
    fun update(context: Context): PreviewPack {
        val connection = URL(DRACONIA_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val tmp = File.createTempFile("draconia-preview-", ".tmp", context.filesDir)
            try {
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val pack = parse(tmp.readText())
                check(tmp.renameTo(cache(context))) { "Unable to save update" }
                return pack
            } finally { tmp.delete() }
        } finally { connection.disconnect() }
    }
}

@Composable
internal fun RigRemotePreview() {
    val context = LocalContext.current
    var pack by remember { mutableStateOf<PreviewPack?>(null) }
    var selected by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(false) }
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
                val changed = pack?.revision != it.revision
                pack = it
                if (changed || it.clips.none { clip -> clip.id == selected }) selected = it.clips.first().id
                status = "Updated from repository."
            }.onFailure { status = "Using saved preview. Update failed: ${it.message}" }
        refreshing = false
    }
    val clip = pack?.clips?.firstOrNull { it.id == selected }
    var frame by remember(clip) { mutableIntStateOf(0) }
    var image by remember(clip) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(clip, playing) {
        if (clip != null) {
            val start = SystemClock.elapsedRealtimeNanos()
            var displayed = 0L
            do {
                val encoded = clip.frames[frame]
                image = withContext(Dispatchers.Default) {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (!playing) break
                displayed++
                val deadline = start + (displayed * (1_000_000_000.0 / clip.framesPerSecond)).toLong()
                val remainingMs = (deadline - SystemClock.elapsedRealtimeNanos() + 999_999) / 1_000_000
                delay(remainingMs.coerceAtLeast(1))
                frame = (frame + 1) % clip.frames.size
            } while (true)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Draconia animation preview", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
            pack?.let { Text("${it.revision} • ${it.description}", style = MaterialTheme.typography.bodySmall) }
            Box(Modifier.fillMaxWidth().height(245.dp).background(if (dark) Color(0xFF222731) else Color(0xFFF3F3F3)), contentAlignment = Alignment.Center) {
                val displayedImage = image
                if (displayedImage != null) Image(displayedImage.asImageBitmap(), contentDescription = clip?.label, modifier = Modifier.size(220.dp))
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
