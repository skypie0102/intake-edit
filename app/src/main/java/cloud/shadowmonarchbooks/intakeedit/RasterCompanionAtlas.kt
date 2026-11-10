package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONObject

/**
 * Dormant raster companion pipeline; no production Book Spirit integration yet.
 * Draws independently painted sprites out of a compact lossless WebP atlas.
 * The 320-unit logical canvas matches the existing companion drawing area.
 */
internal data class RasterSprite(
    val id: String,
    val atlas: Int,
    val sourceX: Int, val sourceY: Int,
    val width: Int, val height: Int,
    val x: Float, val y: Float,
    val logicalWidth: Float, val logicalHeight: Float,
    val pivotX: Float, val pivotY: Float,
    val parent: String?, val z: Int,
)

internal data class RasterPose(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

internal data class RasterAtlasSpecification(
    val atlasNames: List<String>,
    val sprites: List<RasterSprite>,
) {
    /** All inherited transforms from root to this sprite, including its own. */
    fun poseChain(sprite: RasterSprite): List<RasterSprite> {
        val byId = sprites.associateBy { it.id }
        val reversed = mutableListOf<RasterSprite>()
        val seen = mutableSetOf<String>()
        var current: RasterSprite? = sprite
        while (current != null) {
            require(seen.add(current.id)) { "Circular raster bone hierarchy" }
            reversed += current
            current = current.parent?.let { requireNotNull(byId[it]) { "Missing parent part $it" } }
        }
        return reversed.asReversed()
    }

    companion object {
        fun parse(raw: String): RasterAtlasSpecification {
            val doc = JSONObject(raw)
            require(doc.getInt("format") == 2 && doc.getString("kind") == "raster-rig") {
                "Only genuine layered raster rig packs are supported"
            }
            val canvas = doc.getJSONArray("canvas")
            require(canvas.length() == 2 && canvas.getInt(0) == 320 && canvas.getInt(1) == 320)
            val sheets = doc.getJSONArray("atlases")
            require(sheets.length() in 1..4)
            val atlasNames = List(sheets.length()) { index ->
                val item = sheets.getJSONObject(index)
                val path = item.getString("file")
                require(Regex("^texture-[0-9]+\\.webp$").matches(path)) { "Unsafe atlas filename" }
                require(item.getInt("width") in 1..2048 && item.getInt("height") in 1..2048)
                path
            }
            val array = doc.getJSONArray("sprites")
            require(array.length() in 1..128) { "Art is not independently layered" }
            val sprites = List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val rect = item.getJSONArray("src")
                val location = item.getJSONArray("position")
                val pivot = item.getJSONArray("pivot")
                val drawSize = item.optJSONArray("size")
                require(rect.length() == 4 && location.length() == 2 && pivot.length() == 2)
                require(drawSize == null || drawSize.length() == 2)
                val atlas = item.getInt("atlas")
                val w = rect.getInt(2)
                val h = rect.getInt(3)
                require(atlas in atlasNames.indices && w > 0 && h > 0)
                val spr = RasterSprite(
                    id = item.getString("id"), atlas = atlas,
                    sourceX = rect.getInt(0), sourceY = rect.getInt(1),
                    width = w, height = h,
                    x = location.getDouble(0).toFloat(), y = location.getDouble(1).toFloat(),
                    logicalWidth = drawSize?.getDouble(0)?.toFloat() ?: w.toFloat(),
                    logicalHeight = drawSize?.getDouble(1)?.toFloat() ?: h.toFloat(),
                    pivotX = pivot.getDouble(0).toFloat(), pivotY = pivot.getDouble(1).toFloat(),
                    parent = if (item.isNull("parent")) null else item.getString("parent"),
                    z = item.getInt("z")
                )
                val sheet = sheets.getJSONObject(atlas)
                require(spr.sourceX >= 0 && spr.sourceY >= 0)
                require(spr.sourceX + spr.width <= sheet.getInt("width"))
                require(spr.sourceY + spr.height <= sheet.getInt("height"))
                require(Regex("^[a-z][a-z0-9_]*$").matches(spr.id))
                require(listOf(spr.x, spr.y, spr.pivotX, spr.pivotY, spr.logicalWidth, spr.logicalHeight).all { it.isFinite() })
                require(spr.logicalWidth > 0 && spr.logicalHeight > 0)
                spr
            }
            require(sprites.map { it.id }.distinct().size == sprites.size)
            require(sprites.map { it.z }.distinct().size == sprites.size)
            val result = RasterAtlasSpecification(atlasNames, sprites.sortedBy { it.z })
            result.sprites.forEach { result.poseChain(it) }
            return result
        }
    }
}

internal class RasterCompanionAtlas private constructor(
    private val atlasBitmaps: List<Bitmap>,
    private val specification: RasterAtlasSpecification,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val poseChains = specification.sprites.associate { it.id to specification.poseChain(it) }

    /**
     * Render with part-relative keyframes. No synthesized whole-character frames
     * or stale mood states. A complete rig pack must be art-approved first.
     */
    fun render(canvas: Canvas, displayWidth: Float, displayHeight: Float,
               poses: Map<String, RasterPose> = emptyMap()) {
        val saved = canvas.save()
        canvas.scale(displayWidth / 320f, displayHeight / 320f)
        for (layer in specification.sprites) {
            val local = canvas.save()
            for (bone in poseChains.getValue(layer.id)) {
                val pose = poses[bone.id] ?: continue
                canvas.rotate(pose.rotationDegrees, bone.pivotX, bone.pivotY)
                canvas.scale(pose.scaleX, pose.scaleY, bone.pivotX, bone.pivotY)
                canvas.translate(pose.offsetX, pose.offsetY)
            }
            canvas.drawBitmap(
                atlasBitmaps[layer.atlas],
                Rect(layer.sourceX, layer.sourceY, layer.sourceX + layer.width, layer.sourceY + layer.height),
                RectF(layer.x, layer.y, layer.x + layer.logicalWidth, layer.y + layer.logicalHeight),
                paint,
            )
            canvas.restoreToCount(local)
        }
        canvas.restoreToCount(saved)
    }

    companion object {
        fun load(context: Context, basePath: String): RasterCompanionAtlas {
            require(!basePath.contains("..") && !basePath.startsWith("/"))
            val raw = context.assets.open("$basePath/atlas-manifest.json")
                .bufferedReader().use { it.readText() }
            val spec = RasterAtlasSpecification.parse(raw)
            val bitmaps = spec.atlasNames.map { file ->
                context.assets.open("$basePath/$file").use { stream ->
                    requireNotNull(BitmapFactory.decodeStream(stream)) { "Invalid WebP texture: $file" }
                }
            }
            return RasterCompanionAtlas(bitmaps, spec)
        }
    }
}
