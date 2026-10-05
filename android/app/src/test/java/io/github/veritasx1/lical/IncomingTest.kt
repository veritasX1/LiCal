package io.github.veritasx1.lical

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** LiCal as the phone's calendar app: other apps' "Termin hinzufügen", a day, an .ics file – into the editor, unsaved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val store = Store(null)

    @Test
    fun insertFromAnotherApp() {
        val begin = LocalDateTime.of(2026, 10, 20, 18, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 2 * 3_600_000L)
            .putExtra(CalendarContract.Events.TITLE, "Konzert").putExtra(CalendarContract.Events.EVENT_LOCATION, "Kiel")
        val incoming = IncomingIntents.read(context, store, intent)!!
        val event = incoming.request!!.event
        assertTrue(incoming.request!!.isNew)
        assertEquals(listOf("Konzert", "Kiel", "2026-10-20T18:30", "2026-10-20T20:30", store.defaultCalendar()),
            listOf(event.title, event.location, event.start, event.end, event.calendar))
        assertEquals(LocalDate.of(2026, 10, 20), incoming.day)
        assertTrue(store.events.isEmpty())  // nothing saved without ✓
    }

    @Test
    fun icsFileAndDay() {
        val file = File.createTempFile("einladung", ".ics").apply {
            writeText("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:Elternabend\r\nDTSTART:20261022T190000\r\nDTEND:20261022T203000\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
        }
        val incoming = IncomingIntents.read(context, store, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), "text/calendar"))!!
        assertEquals("Elternabend" to "2026-10-22T19:00", incoming.request!!.event.title to incoming.request!!.event.start)
        val millis = LocalDate.of(2026, 12, 24).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 3_600_000
        val day = IncomingIntents.read(context, store, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$millis")))!!
        assertEquals(Screen.Day to LocalDate.of(2026, 12, 24), day.screen to day.day)
        assertNull(IncomingIntents.read(context, store, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org"))))
    }
}
