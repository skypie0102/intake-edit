package cloud.shadowmonarchbooks.intakeedit

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/** Serves only generated, public companion poses; no project data is exposed. */
class CompanionWidgetFrameProvider : ContentProvider() {
    private val workers = Executors.newFixedThreadPool(2)
    private val images = object : LruCache<String, ByteArray>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val segments = uri.pathSegments
        val mood = PetMood.entries.firstOrNull { segments.size == 2 && it.name == segments[0] }
        val frame = segments.getOrNull(1)?.toIntOrNull()
        if (mode != "r" || mood == null || frame == null || frame !in 0 until 384) throw FileNotFoundException()
        val context = context ?: throw FileNotFoundException()
        val pipes = ParcelFileDescriptor.createPipe()
        workers.execute {
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { output ->
                try {
                    val key = "$mood/$frame"
                    val bytes = images.get(key) ?: run {
                        val picture = CompanionAnimationPack.widgetFrame(context, mood, frame)
                        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.scale(.5f, .5f)
                        canvas.drawPicture(picture)
                        val data = ByteArrayOutputStream().use { buffer ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
                            buffer.toByteArray()
                        }
                        bitmap.recycle()
                        images.put(key, data)
                        data
                    }
                    output.write(bytes)
                } catch (_: Exception) {
                    // The host may stop reading when the widget leaves the screen.
                }
            }
        }
        return pipes[0]
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
