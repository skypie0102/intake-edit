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
        if (!isRigOnlyPreview(context)) {
            scheduleNextDay(context)
            schedulePetProjectSync(context)
        }
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
        private fun isRigOnlyPreview(context: Context) = BuildConfig.DEBUG && context.packageName == RIG_PREVIEW_PACKAGE

        internal fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TranslationPetWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
            if (!isRigOnlyPreview(context)) scheduleNextDay(context)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (isRigOnlyPreview(context)) {
                renderMood(context, manager, ids, readRigDemoMood(context) ?: PetMood.WAITING)
                return
            }
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
                        setViewVisibility(R.id.pet_widget_static, android.view.View.INVISIBLE)
                        setContentDescription(R.id.pet_widget_root, "Connect your project in Intake Edit")
                        setOnClickPendingIntent(R.id.pet_widget_root, open)
                    }
                    manager.updateAppWidget(id, views)
                }
                return
            }
            renderMood(context, manager, ids, PetStore(context).status(settings, count).mood)
        }

        private fun renderMood(context: Context, manager: AppWidgetManager, ids: IntArray, mood: PetMood) {
            val message = when (mood) {
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
                val layout = when (mood) {
                    PetMood.NEW -> R.layout.companion_widget_new
                    PetMood.HAPPY -> R.layout.companion_widget_happy
                    PetMood.WAITING -> R.layout.companion_widget_waiting
                    PetMood.HUNGRY -> R.layout.companion_widget_hungry
                    PetMood.SAD -> R.layout.companion_widget_sad
                }
                val views = RemoteViews(context.packageName, layout).apply {
                    setViewVisibility(R.id.pet_widget_portrait, android.view.View.VISIBLE)
                    if (!ProductionWidgetFrames.apply(context, mood, this, id)) {
                        setViewVisibility(R.id.pet_widget_portrait, android.view.View.GONE)
                        setViewVisibility(R.id.pet_widget_static, android.view.View.VISIBLE)
                        setImageViewResource(R.id.pet_widget_static, companionWidgetFrames(mood).first())
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
