package cloud.shadowmonarchbooks.intakeedit

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/** Keeps all home-screen instances in step with the same on-device companion. */
class TranslationPetWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
        scheduleNextDay(context)
        schedulePetProjectSync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> refreshAll(context)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    companion object {
        internal const val ACTION_OPEN_CHAPTER = "cloud.shadowmonarchbooks.intakeedit.OPEN_NEXT_CHAPTER"
        private const val ACTION_REFRESH = "cloud.shadowmonarchbooks.intakeedit.PET_WIDGET_REFRESH"

        internal fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TranslationPetWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
            scheduleNextDay(context)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val settings = SettingsStore(context).load()
            val token = SecureTokenStore(context).load()
            val snapshot = PetProjectCache(context).get(settings, token)
            val count = snapshot?.completed
            if (count == null) {
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_CHAPTER),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.translation_pet_widget).apply {
                        setViewVisibility(R.id.pet_widget_portrait, android.view.View.INVISIBLE)
                        setContentDescription(R.id.pet_widget_root, "Connect your project in Intake Edit")
                        setOnClickPendingIntent(R.id.pet_widget_root, open)
                    }
                    manager.updateAppWidget(id, views)
                }
                return
            }
            val status = PetStore(context).status(settings, count)
            val message = when (status.mood) {
                PetMood.NEW -> "Ready for a chapter!"
                PetMood.HAPPY -> "Fed today!"
                PetMood.WAITING -> "Time to feed me!"
                PetMood.HUNGRY -> "I'm hungry..."
                PetMood.SAD -> "I miss you..."
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_CHAPTER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.translation_pet_widget).apply {
                    setViewVisibility(R.id.pet_widget_portrait, android.view.View.VISIBLE)
                    val frameViews = listOf(R.id.pet_widget_frame_0, R.id.pet_widget_frame_1, R.id.pet_widget_frame_2,
                        R.id.pet_widget_frame_3, R.id.pet_widget_frame_4, R.id.pet_widget_frame_5,
                        R.id.pet_widget_frame_6, R.id.pet_widget_frame_7, R.id.pet_widget_frame_8,
                        R.id.pet_widget_frame_9, R.id.pet_widget_frame_10, R.id.pet_widget_frame_11)
                    frameViews.zip(companionFrames(status.mood).asIterable()).forEach { (view, frame) ->
                        setImageViewResource(view, frame)
                    }
                    setContentDescription(R.id.pet_widget_portrait, "Companion: $message")
                    setOnClickPendingIntent(R.id.pet_widget_root, open)
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, TranslationPetWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun scheduleNextDay(context: Context) {
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, nextMidnight, refreshIntent(context),
            )
        }
    }
}
