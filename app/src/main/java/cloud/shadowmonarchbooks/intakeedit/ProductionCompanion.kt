package cloud.shadowmonarchbooks.intakeedit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Picture
import android.view.View
import android.widget.RemoteViews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The production pack is bundled with the release; no preview or network cache is used. */
internal object CompanionAnimationPack {
    private var source: JSONObject? = null
    @Volatile private var cached: MoodAnimation? = null

    private fun json(context: Context): JSONObject = synchronized(this) {
        source ?: JSONObject(context.applicationContext.assets.open("companion/animation-pack.json")
            .bufferedReader().use { it.readText() }).also { source = it }
    }

    private fun picture(svg: String): Picture = SVG.getFromString(svg).renderToPicture(320, 320)

    /** Small resting poses load independently of the longer action sequences. */
    fun loadRest(context: Context, mood: PetMood): MoodRest {
        val root = json(context)
        val key = mood.name.lowercase()
        return MoodRest(mood,
            picture(root.getJSONObject("moods").getJSONArray(key).getString(0)),
            picture(root.getJSONObject("idle_blinks").getString(key)))
    }

    fun load(context: Context, mood: PetMood): MoodAnimation {
        cached?.takeIf { it.mood == mood }?.let { return it }
        val root = json(context)
        val key = mood.name.lowercase()
        fun frames(array: org.json.JSONArray) = List(array.length()) { picture(array.getString(it)) }
        val actions = root.getJSONObject("shared_actions").getJSONObject(key)
        val result = MoodAnimation(mood, root.getLong("frame_ms"),
            frames(root.getJSONObject("moods").getJSONArray(key)),
            picture(root.getJSONObject("idle_blinks").getString(key)),
            listOf("reading", "writing", "page_turn").associateWith { frames(actions.getJSONArray(it)) }
        )
        cached = result
        return result
    }

    fun widgetFrame(context: Context, mood: PetMood, position: Int): Picture {
        require(position in 0 until 384)
        val root = json(context)
        val key = mood.name.lowercase()
        val (action, index) = widgetFrameIndex(position)
        val frames = if (action == 0) root.getJSONObject("moods").getJSONArray(key) else {
            val name = listOf("reading", "writing", "page_turn")[action - 1]
            root.getJSONObject("shared_actions").getJSONObject(key).getJSONArray(name)
        }
        val frame = index * frames.length() / 96
        return picture(frames.getString(frame))
    }

}

internal data class MoodRest(val mood: PetMood, val open: Picture, val blink: Picture)

internal data class MoodAnimation(
    val mood: PetMood,
    val frameMs: Long,
    val frames: List<Picture>,
    val blink: Picture,
    val actions: Map<String, List<Picture>>,
)

/** Whether a new selection should start with a short blink, without hiding the character. */
internal fun shouldBlinkForMoodChange(previous: PetMood?, selected: PetMood, animationsEnabled: Boolean): Boolean =
    animationsEnabled && previous != null && previous != selected

@Composable
internal fun ProductionBookSpirit(mood: PetMood, description: String, size: Dp = 110.dp) {
    val context = LocalContext.current
    // Resting poses for all moods are small compared with the full action pack.
    // Keep the selected mood visible while its complete sequence loads.
    val resting by produceState<Map<PetMood, MoodRest>>(emptyMap(), context) {
        value = withContext(Dispatchers.IO) {
            PetMood.entries.mapNotNull { selected ->
                runCatching { selected to CompanionAnimationPack.loadRest(context, selected) }.getOrNull()
            }.toMap()
        }
    }
    val animation by produceState<MoodAnimation?>(null, mood) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { CompanionAnimationPack.load(context, mood) }.getOrNull() }
    }
    val current = animation?.takeIf { it.mood == mood }
    val rest = resting[mood]
    var previousMood by remember { mutableStateOf<PetMood?>(null) }
    var transition by remember(mood) {
        mutableStateOf(shouldBlinkForMoodChange(previousMood, mood, ValueAnimator.areAnimatorsEnabled()))
    }
    LaunchedEffect(mood) {
        previousMood = mood
        if (transition) delay(150)
        transition = false
    }
    var frame by remember(mood, current) { mutableIntStateOf(0) }
    var action by remember(mood, current) { mutableStateOf<String?>(null) }
    var blink by remember(mood, current) { mutableStateOf(false) }
    LaunchedEffect(mood, current) {
        if (current == null) return@LaunchedEffect
        if (transition) delay(150)
        val timeline = RigPreviewTimeline(current.frames.size, startWithAction = mood == PetMood.NEW,
            allowIdleBlink = true, allowSharedActions = true)
        while (true) {
            if (ValueAnimator.areAnimatorsEnabled()) {
                frame = timeline.advance()
                action = timeline.sharedAction
                blink = timeline.blinking
            } else {
                frame = 0
                action = null
                blink = false
            }
            delay(moodFrameDuration(current.frameMs, mood.name))
        }
    }
    if (rest == null && current == null) {
        AnimatedBookSpirit(mood, description, size)
        return
    }
    val sequence = action?.let { current?.actions?.get(it) }
    val picture = when {
        transition -> rest?.blink ?: current?.blink
        current == null -> rest?.open
        blink -> current.blink
        sequence != null -> sequence[(frame * sequence.size / current.frames.size).coerceIn(sequence.indices)]
        else -> current.frames[frame.coerceIn(current.frames.indices)]
    } ?: return
    Canvas(Modifier.size(size).semantics { contentDescription = "Companion: $description" }) {
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        canvas.scale(size.width / 320f, size.height / 320f)
        canvas.drawPicture(picture)
        canvas.restoreToCount(save)
    }
}

/** Indexes every authored pose; the 48-pose shared actions hold each pose for two ticks. */
internal fun widgetFrameIndex(position: Int): Pair<Int, Int> {
    require(position in 0 until 384)
    return position / 96 to position % 96
}

/** The launcher asks the adapter for individual poses, avoiding a 384-bitmap widget update. */
class CompanionWidgetFrameService : android.widget.RemoteViewsService() {
    override fun onGetViewFactory(intent: android.content.Intent): RemoteViewsFactory {
        val mood = PetMood.entries.firstOrNull { it.name == intent.getStringExtra("mood") } ?: PetMood.WAITING
        return CompanionWidgetFrameFactory(applicationContext, mood)
    }
}

private class CompanionWidgetFrameFactory(private val context: Context, private val mood: PetMood) :
    android.widget.RemoteViewsService.RemoteViewsFactory {
    private val cache = object : android.util.LruCache<Int, Bitmap>(20) {}
    override fun onCreate() = Unit
    override fun onDataSetChanged() = Unit
    override fun onDestroy() { cache.evictAll() }
    override fun getCount() = 384
    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.companion_widget_frame)
        val bitmap = cache.get(position) ?: runCatching {
            companionWidgetBitmap(CompanionAnimationPack.widgetFrame(context, mood, position))
        }.getOrNull()?.also { cache.put(position, it) }
        if (bitmap != null) views.setImageViewBitmap(R.id.companion_widget_image, bitmap)
        views.setOnClickFillInIntent(R.id.companion_widget_image, android.content.Intent())
        return views
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
}

private fun companionWidgetBitmap(picture: Picture): Bitmap =
    Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also {
        val canvas = AndroidCanvas(it)
        canvas.scale(.5f, .5f)
        canvas.drawPicture(picture)
    }

internal object ProductionWidgetFrames {
    fun apply(context: Context, mood: PetMood, views: RemoteViews, widgetId: Int): Boolean = runCatching {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            views.setImageViewBitmap(R.id.pet_widget_static, companionWidgetBitmap(CompanionAnimationPack.widgetFrame(context, mood, 0)))
            views.setViewVisibility(R.id.pet_widget_portrait, View.GONE)
            views.setViewVisibility(R.id.pet_widget_static, View.VISIBLE)
        } else {
            val adapter = android.content.Intent(context, CompanionWidgetFrameService::class.java).apply {
                putExtra("mood", mood.name)
                data = android.net.Uri.parse("intake-edit://companion/$widgetId/${mood.name}")
            }
            views.setViewVisibility(R.id.pet_widget_static, View.INVISIBLE)
            views.setViewVisibility(R.id.pet_widget_portrait, View.VISIBLE)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
                repeat(384) { index ->
                    val item = RemoteViews(context.packageName, R.layout.companion_widget_frame)
                    item.setImageViewUri(R.id.companion_widget_image,
                        android.net.Uri.parse("content://${context.packageName}.companionframes/${mood.name}/$index"))
                    item.setOnClickFillInIntent(R.id.companion_widget_image, android.content.Intent())
                    items.addItem(index.toLong(), item)
                }
                views.setRemoteAdapter(R.id.pet_widget_portrait, items.build())
            } else {
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.pet_widget_portrait, adapter)
            }
            val open = android.app.PendingIntent.getActivity(context, 0,
                android.content.Intent(context, MainActivity::class.java).setAction(TranslationPetWidget.ACTION_OPEN_CHAPTER),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setPendingIntentTemplate(R.id.pet_widget_portrait, open)
            views.setDisplayedChild(R.id.pet_widget_portrait, 0)
        }
        true
    }.getOrDefault(false)
}
