package io.github.veritasx1.lical

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Card 6e2dc488: the phone's calendar rows ↔ LiCal events, without the provider (it does not run in
 *  Robolectric): times in Berlin time, all-day in UTC, RFC 2445 durations, exceptions both ways. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceMappingTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun millis(at: LocalDateTime) = at.atZone(berlin).toInstant().toEpochMilli()
    private fun utcDay(day: LocalDate) = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun instances() {
        // A timed instance: local wall-clock time (summer time on 4 Oct, winter time on 26 Oct).
        val dentist = DeviceMapping.occurrence(7, 3, "Zahnarzt", "Dr. Weber", "", false,
            millis(LocalDateTime.of(2026, 10, 26, 8, 30)), millis(LocalDateTime.of(2026, 10, 26, 9, 15)), null, berlin)
        assertEquals("2026-10-26T08:30", dentist.start)
        assertEquals("2026-10-26T09:15", dentist.end)
        assertEquals("dev:7@2026-10-26T08:30", dentist.key)
        assertEquals("dev:3", dentist.event.calendar)
        // All-day: stored as UTC midnight – must stay on its day also east of Greenwich.
        val holiday = DeviceMapping.occurrence(9, 2, "Tag der Deutschen Einheit", "", "", true,
            utcDay(LocalDate.of(2026, 10, 3)), utcDay(LocalDate.of(2026, 10, 4)), null, berlin)
        assertEquals("2026-10-03", holiday.start)
        assertEquals("2026-10-04", holiday.end)
        assertEquals(LocalDate.of(2026, 10, 3) to LocalDate.of(2026, 10, 3), Rules.daysCovered(holiday))
        // An all-day instance with begin == end still covers its day.
        val zero = DeviceMapping.occurrence(9, 2, "x", "", "", true, utcDay(LocalDate.of(2026, 12, 24)), utcDay(LocalDate.of(2026, 12, 24)), null, berlin)
        assertEquals("2026-12-25", zero.end)
    }

    @Test
    fun durations() {
        assertEquals(3600L, DeviceMapping.duration("P3600S")!!.seconds)
        assertEquals(5400L, DeviceMapping.duration("PT1H30M")!!.seconds)
        assertEquals(86400L, DeviceMapping.duration("P1D")!!.seconds)
        assertEquals(7 * 86400L, DeviceMapping.duration("P1W")!!.seconds)
        assertNull(DeviceMapping.duration("quatsch"))
        // A repeating event in the provider has DURATION instead of DTEND.
        val series = DeviceMapping.event(5, 3, "Jour fixe", "", "", false, millis(LocalDateTime.of(2026, 10, 5, 9, 0)), null, "P3600S",
            "FREQ=WEEKLY;BYDAY=MO", "20261012T070000Z", berlin)
        assertEquals("2026-10-05T09:00", series.start)
        assertEquals("2026-10-05T10:00", series.end)
        assertEquals(listOf("2026-10-12"), series.exdates)  // 07:00 UTC = 09:00 in Berlin
    }

    @Test
    fun valuesBothWays() {
        val event = Event("dev:5", "dev:3", "Jour fixe", false, "2026-10-05T09:00", "2026-10-05T10:00", "Raum 2", "Agenda",
            "FREQ=WEEKLY;BYDAY=MO", listOf("2026-10-12", "2026-10-26"))
        val values = DeviceMapping.values(event, 3, berlin)
        assertEquals(3L, values[CalendarContract.Events.CALENDAR_ID])
        assertEquals("P3600S", values[CalendarContract.Events.DURATION])
        assertNull(values[CalendarContract.Events.DTEND])
        assertEquals("Europe/Berlin", values[CalendarContract.Events.EVENT_TIMEZONE])
        // 12 Oct 09:00 summer time = 07:00 UTC, 26 Oct 09:00 winter time = 08:00 UTC.
        assertEquals("20261012T070000Z,20261026T080000Z", values[CalendarContract.Events.EXDATE])
        val back = DeviceMapping.event(5, 3, event.title, event.location, event.notes, false, values[CalendarContract.Events.DTSTART] as Long,
            null, values[CalendarContract.Events.DURATION] as String, event.rrule, values[CalendarContract.Events.EXDATE] as String, berlin)
        assertEquals(event, back)
        // All-day, not repeating: UTC times, DTEND, no DURATION.
        val trip = Event("x", "dev:3", "Urlaub", true, "2026-10-12", "2026-10-17")
        val tripValues = DeviceMapping.values(trip, 3, berlin)
        assertEquals("UTC", tripValues[CalendarContract.Events.EVENT_TIMEZONE])
        assertEquals(utcDay(LocalDate.of(2026, 10, 17)), tripValues[CalendarContract.Events.DTEND])
        assertNull(tripValues[CalendarContract.Events.DURATION])
    }
}
