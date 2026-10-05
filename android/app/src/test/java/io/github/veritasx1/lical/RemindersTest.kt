package io.github.veritasx1.lical

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Card 91adf47e: an alert of LiCal's own event rings as a notification exactly once, missed ones
 *  after a restart too (up to six hours), the next alarm is set – and the lock screen shows only "Termin". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemindersTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun ringsOnceAndSetsTheNextAlarm() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val store = Store(File(app.filesDir, "kalender.json"))  // Robolectric's own temporary folder
        store.put(Event("z", "privat", "Zahnarzt", false, "2026-10-07T08:30", "2026-10-07T09:15", location = "Dr. Weber", notes = "geheim",
            alerts = listOf(15, 1440)))
        store.put(Event("h", "arbeit", "Versteckt", false, "2026-10-07T09:00", "2026-10-07T10:00", alerts = listOf(0)))
        store.setVisible("arbeit", false)  // a hidden calendar does not ring
        store.put(Event("a", "privat", "Abendessen", false, "2026-10-06T20:00", "2026-10-06T21:00", alerts = listOf(0)))

        Reminders.ring(app, LocalDateTime.parse("2026-10-06T08:00"))  // the first time: nothing old rings
        assertTrue(notifications().isEmpty())
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        val next = alarms.nextScheduledAlarm
        assertNotNull(next)
        assertEquals(LocalDateTime.parse("2026-10-06T08:30").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000, next!!.triggerAtTime)

        Reminders.ring(app, LocalDateTime.parse("2026-10-06T08:30:01"))
        assertEquals(1, notifications().size)
        val shown = notifications().single()
        assertEquals("Zahnarzt", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Morgen, 08:30–09:15 · Dr. Weber", shown.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notification.VISIBILITY_PRIVATE, shown.visibility)
        assertEquals("Termin", shown.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertTrue("notes never in a notification", shown.extras.toString().let { "geheim" !in it })

        Reminders.ring(app, LocalDateTime.parse("2026-10-06T08:31"))  // the same alert does not ring twice
        assertEquals(1, notifications().size)

        // Off for a night: on start the missed alert of 08:15 rings – not the one of 20:00 (over six hours
        // late) and not the hidden calendar's.
        Reminders.ring(app, LocalDateTime.parse("2026-10-07T09:05"))
        assertEquals(listOf("Zahnarzt", "Zahnarzt"), notifications().map { it.extras.getString(Notification.EXTRA_TITLE) })
        assertEquals("Heute, 08:30–09:15 · Dr. Weber", notifications().last().extras.getString(Notification.EXTRA_TEXT))
        assertNull(alarms.nextScheduledAlarm)  // nothing more to ring
    }
}
