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
        private const val ACTION_REFRESH = "cloud.shadowmonarchbooks.intakeedit.PET_WIDGET_REFRESH"

        internal fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TranslationPetWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
            scheduleNextDay(context)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val status = PetStore(context).status()
            val (portrait, message) = when (status.mood) {
                PetMood.NEW -> R.drawable.pet_widget_waiting to "Ready for a chapter!"
                PetMood.HAPPY -> R.drawable.pet_widget_happy to "Fed today!"
                PetMood.WAITING -> R.drawable.pet_widget_waiting to "Time to feed me!"
                PetMood.HUNGRY -> R.drawable.pet_widget_hungry to "I'm hungry..."
                PetMood.SAD -> R.drawable.pet_widget_sad to "I miss you..."
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.translation_pet_widget).apply {
                    setTextViewText(R.id.pet_widget_level, "Level ${status.level}")
                    setTextViewText(R.id.pet_widget_status, message)
                    setTextViewText(R.id.pet_widget_progress_text, "${status.levelProgress}/5 days to next level")
                    setProgressBar(R.id.pet_widget_progress, 5, status.levelProgress, false)
                    setImageViewResource(R.id.pet_widget_portrait, portrait)
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
