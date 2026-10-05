package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Alerts of LiCal's own events (card 91adf47e) as notifications. One exact alarm for the next alert;
 *  when it rings, every alert since the last check is shown (also missed ones after a restart, up to
 *  six hours late) and the next alarm is set. The phone's calendars (Google via DAVx⁵) keep their
 *  alerts in Android's calendar store – the phone's calendar app rings those, not LiCal (no doubles).
 *  Privacy: on the lock screen a notification shows only "Termin", nothing of the event. */
object Reminders {
    private const val CHANNEL = "termine"
    private const val PREFS = "lical-alerts"
    private const val CHECKED = "checked"
    private const val LATEST_MISSED_HOURS = 6L
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun events(context: Context): List<Event> = Store(File(context.filesDir, "kalender.json")).let { store ->
        val shown = store.calendars.filter { it.visible }.map { it.id }.toSet()
        store.events.filter { it.calendar in shown }
    }

    fun mayNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Show what is due, then set the alarm for the next alert. Safe to call any time (start, change, boot). */
    @Synchronized
    fun ring(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val checked = prefs.getString(CHECKED, null)?.let { runCatching { LocalDateTime.parse(it, STAMP) }.getOrNull() }
        val events = events(context)
        if (checked != null && checked < now) {
            val oldest = now.minusHours(LATEST_MISSED_HOURS)
            for (alert in Alerts.due(events, maxOf(checked, oldest), now)) show(context, alert, now)
        }
        prefs.edit().putString(CHECKED, now.format(STAMP)).apply()
        schedule(context, Alerts.nextTime(events, now))
    }

    private fun schedule(context: Context, at: LocalDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(context, 0, Intent(context, AlertReceiver::class.java).setAction(AlertReceiver.RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(intent)
        if (at == null) return
        // Ring at the minute (+1 s, so the alert is inside the window when it rings).
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
    }

    private fun channel(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Hinweise zu Terminen"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Erinnerungen vor Terminen in LiCal")
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
        return manager
    }

    private fun show(context: Context, alert: Alert, now: LocalDateTime) {
        if (!mayNotify(context)) return
        val manager = channel(context)
        val open = PendingIntent.getActivity(context, alert.key.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.DAY, alert.start.take(10))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hidden = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(tr("Termin")).build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title.ifEmpty { tr("Termin") })
            .setContentText(Alerts.text(alert, now))
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(LocalDateTime.parse(alert.at, STAMP).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setShowWhen(true)
            .build()
        manager.notify(alert.key.hashCode(), notification)
    }
}

/** The alarm rang, the phone started, the time or zone changed, LiCal was updated: see Reminders.ring. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try { Reminders.ring(context.applicationContext) } finally { result.finish() }
        }.start()
    }

    companion object {
        const val RING = "io.github.veritasx1.lical.RING"
    }
}
