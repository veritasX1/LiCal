package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/** "LiCal – Datum": a 1×1 widget that looks like Apple's calendar icon with today's date (Android lets
 *  only the launcher change app icons; this one cannot). Turns over at midnight, a tap opens LiCal. */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onEnabled(context: Context) = refresh(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(midnight(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(TURN, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED)) {
            if (intent.action == TURN) CalendarWidgets.refresh(context) else refresh(context)  // midnight: all widgets turn over
        }
    }

    companion object {
        const val TURN = "io.github.veritasx1.lical.DATE_TURN"

        private fun midnight(context: Context) = PendingIntent.getBroadcast(context, 1,
            Intent(context, DateWidget::class.java).setAction(TURN), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        /** Draw today's date into every placed widget and set the alarm for the next midnight. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DateWidget::class.java))
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.date_widget).apply {
                setImageViewBitmap(R.id.date_icon, DateIcon.draw(context, today, 256))
                setContentDescription(R.id.date_icon, tr("LiCal, {value} {dayOfMonth}.", "value" to (DateIcon.WEEKDAYS[today.dayOfWeek.value - 1]), "dayOfMonth" to today.dayOfMonth))
                setOnClickPendingIntent(R.id.date_root, open)
            }
            manager.updateAppWidget(ids, views)
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2000
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at, midnight(context))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, midnight(context))
        }

        /** "Widgets auf den Startbildschirm": asks the launcher to place the widget (it shows its own dialog). */
        fun pin(context: Context, type: Class<*> = DateWidget::class.java): Boolean {
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
            return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, type), null, null)
        }
    }
}
