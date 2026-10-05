package io.github.veritasx1.lical

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A calendar of the phone (Google via DAVx⁵, holidays, offline): shown and edited by LiCal like the
 *  iPhone shows the calendars of its accounts – nothing is copied, Android's calendar store stays
 *  the one truth and DAVx⁵ takes the changes to Google. */
data class DeviceCalendar(val id: Long, val name: String, val color: Int, val account: String, val writable: Boolean)

const val DEVICE = "dev:"

/** The phone's calendars through Android's calendar provider. The conversions are plain functions
 *  (DeviceMapping) so they can be tested without the provider. */
class DeviceCalendars(private val context: Context) {
    private val NOTIFYING = listOf(CalendarContract.Reminders.METHOD_DEFAULT, CalendarContract.Reminders.METHOD_ALERT)

    fun permitted() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun writable() = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<DeviceCalendar> {
        if (!permitted()) return emptyList()
        val result = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, "${CalendarContract.Calendars.VISIBLE} = 1", null, null)?.use { c ->
                while (c.moveToNext()) result.add(DeviceCalendar(c.getLong(0), c.getString(1) ?: "Kalender", c.getInt(2), c.getString(3) ?: "",
                    c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR))
            }
        }
        return result
    }

    /** The occurrences between two days (the provider unfolds repetitions itself). */
    fun occurrences(start: LocalDate, end: LocalDate, calendars: Set<Long>): List<Occurrence> {
        if (!permitted() || calendars.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        // All-day instances are stored in UTC: widen the window a day, filter after mapping.
        val from = start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.RRULE, CalendarContract.Instances.STATUS)
        val result = mutableListOf<Occurrence>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(1) !in calendars || c.getInt(9) == CalendarContract.Events.STATUS_CANCELED) continue
                    val item = DeviceMapping.occurrence(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "",
                        c.getInt(5) == 1, c.getLong(6), c.getLong(7), c.getString(8), zone)
                    val (first, last) = Rules.daysCovered(item)
                    if (last >= start && first < end) result.add(item)
                }
            }
        }
        return result
    }

    /** The stored event (the series, for a repeating one). */
    fun event(id: Long): Event? {
        if (!permitted()) return null
        val projection = arrayOf(CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE, CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION, CalendarContract.Events.ALL_DAY, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION, CalendarContract.Events.RRULE, CalendarContract.Events.EXDATE, CalendarContract.Events.EVENT_TIMEZONE)
        return runCatching {
            context.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) null
                else DeviceMapping.event(id, c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getInt(4) == 1,
                    c.getLong(5), if (c.isNull(6)) null else c.getLong(6), c.getString(7), c.getString(8), c.getString(9), ZoneId.systemDefault())
                    .copy(alerts = reminders(id))
            }
        }.getOrNull()
    }

    /** The event's alerts (notification reminders; e-mail ones are left alone), soonest-before first. */
    fun reminders(id: Long): List<Int> {
        val result = mutableListOf<Int>()
        runCatching {
            context.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
                "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(id.toString()), "${CalendarContract.Reminders.MINUTES} ASC")?.use { c ->
                while (c.moveToNext()) if (c.getInt(1) in NOTIFYING) result.add(c.getInt(0))
            }
        }
        return result.distinct()
    }

    /** Write the alerts – only when they changed, and only the notification ones (DAVx⁵ takes them to Google). */
    private fun writeReminders(id: Long, alerts: List<Int>) {
        if (reminders(id) == alerts) return
        context.contentResolver.delete(CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ? AND ${CalendarContract.Reminders.METHOD} IN (${NOTIFYING.joinToString()})", arrayOf(id.toString()))
        for (minutes in alerts) context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, id)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        })
    }

    /** Write an event to a phone calendar: new (id without a number) or changed. Returns its id. */
    fun save(event: Event): String {
        val calendar = event.calendar.removePrefix(DEVICE).toLong()
        val values = ContentValues()
        DeviceMapping.values(event, calendar, ZoneId.systemDefault()).forEach { (key, value) ->
            when (value) {
                null -> values.putNull(key)
                is Long -> values.put(key, value)
                is Int -> values.put(key, value)
                else -> values.put(key, value.toString())
            }
        }
        val number = event.id.removePrefix(DEVICE).toLongOrNull()
        val id = if (number != null && event.id.startsWith(DEVICE)) {
            context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, number), values, null, null)
            number
        } else {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
        }
        if (id != null) runCatching { writeReminders(id, event.alerts) }
        return DEVICE + (id ?: "")
    }

    fun delete(id: String) {
        val number = id.removePrefix(DEVICE).toLongOrNull() ?: return
        context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, number), null, null)
    }
}

/** Conversions between Android's calendar rows and LiCal events – no provider needed (DeviceMappingTest). */
object DeviceMapping {
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val EXDATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val EXDATE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** A moment of the provider as LiCal text: all-day in UTC (the provider's rule), else local time. */
    fun stamp(millis: Long, allDay: Boolean, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(millis)
        return if (allDay) Rules.stamp(LocalDate.ofInstant(instant, ZoneOffset.UTC))
        else LocalDateTime.ofInstant(instant, zone).format(DATE_TIME)
    }

    fun occurrence(eventId: Long, calendarId: Long, title: String, location: String, notes: String, allDay: Boolean,
                   begin: Long, end: Long, rrule: String?, zone: ZoneId): Occurrence {
        val start = stamp(begin, allDay, zone)
        val stop = stamp(maxOf(end, begin), allDay, zone).let { if (allDay && it == start) Rules.stamp(Rules.parse(start).date.plusDays(1)) else it }
        val event = Event(DEVICE + eventId, DEVICE + calendarId, title, allDay, start, stop, location, notes, rrule?.ifEmpty { null })
        return Occurrence(event, start, stop, "${event.id}@$start")
    }

    /** RFC 2445 durations as the provider stores them: P3600S, PT1H30M, P1D, P1W. */
    fun duration(text: String?): Duration? {
        if (text.isNullOrBlank()) return null
        // Android writes seconds without "T" (P3600S); RFC 2445 also has PT1H30M, P1D, P1W.
        val match = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").find(text.trim()) ?: return null
        val (sign, weeks, days, hours, minutes, seconds) = match.destructured
        val total = Duration.ofDays((weeks.toLongOrNull() ?: 0) * 7 + (days.toLongOrNull() ?: 0))
            .plusHours(hours.toLongOrNull() ?: 0).plusMinutes(minutes.toLongOrNull() ?: 0).plusSeconds(seconds.toLongOrNull() ?: 0)
        return if (sign == "-") total.negated() else total
    }

    fun event(id: Long, calendarId: Long, title: String, location: String, notes: String, allDay: Boolean, dtStart: Long, dtEnd: Long?,
              durationText: String?, rrule: String?, exdate: String?, zone: ZoneId): Event {
        val end = dtEnd ?: (dtStart + (duration(durationText)?.toMillis() ?: if (allDay) 86_400_000L else 3_600_000L))
        val start = stamp(dtStart, allDay, zone)
        val stop = stamp(maxOf(end, dtStart), allDay, zone).let { if (allDay && it == start) Rules.stamp(Rules.parse(start).date.plusDays(1)) else it }
        return Event(DEVICE + id, DEVICE + calendarId, title, allDay, start, stop, location, notes, rrule?.ifEmpty { null }, exdates(exdate, allDay, zone))
    }

    /** EXDATE "20261012T070000Z,20261019T070000Z" (or dates) → LiCal's exception days in local time. */
    fun exdates(text: String?, allDay: Boolean, zone: ZoneId): List<String> =
        (text ?: "").split(",", ";").map { it.trim().substringAfterLast(":") }.filter { it.length >= 8 }.map { value ->
            if (value.length >= 15 && value.endsWith("Z")) LocalDateTime.parse(value, EXDATE_TIME).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate().toString()
            else LocalDate.parse(value.take(8), EXDATE_DAY).toString()
        }.distinct().sorted()

    /** The provider's columns for an event (a repeating one gets DURATION instead of DTEND, as Android requires). */
    fun values(event: Event, calendarId: Long, zone: ZoneId): Map<String, Any?> {
        val start = Rules.parse(event.start)
        val end = Rules.parse(event.end)
        // An event with its own zone (card 7a9187d6) is stored in that zone – the phone's calendar app shows it right.
        val own = event.tz?.takeIf { !event.allDay }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        val startMillis = if (event.allDay) start.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else start.at.atZone(own).toInstant().toEpochMilli()
        val endMillis = if (event.allDay) end.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else end.at.atZone(own).toInstant().toEpochMilli()
        val values = linkedMapOf<String, Any?>(
            CalendarContract.Events.CALENDAR_ID to calendarId,
            CalendarContract.Events.TITLE to event.title,
            CalendarContract.Events.EVENT_LOCATION to event.location,
            CalendarContract.Events.DESCRIPTION to event.notes,
            CalendarContract.Events.ALL_DAY to if (event.allDay) 1 else 0,
            CalendarContract.Events.DTSTART to startMillis,
            CalendarContract.Events.EVENT_TIMEZONE to if (event.allDay) "UTC" else own.id,
            CalendarContract.Events.RRULE to event.rrule,
        )
        if (event.rrule != null) {
            values[CalendarContract.Events.DURATION] = if (event.allDay) "P${maxOf(1L, (endMillis - startMillis) / 86_400_000L)}D" else "P${(endMillis - startMillis) / 1000}S"
            values[CalendarContract.Events.DTEND] = null
            values[CalendarContract.Events.EXDATE] = event.exdates.takeIf { it.isNotEmpty() }?.joinToString(",") { day ->
                if (event.allDay) LocalDate.parse(day).format(EXDATE_DAY)
                else LocalDate.parse(day).atTime(start.at.toLocalTime()).atZone(own).withZoneSameInstant(ZoneOffset.UTC).format(EXDATE_TIME)
            }
        } else {
            values[CalendarContract.Events.DTEND] = endMillis
            values[CalendarContract.Events.DURATION] = null
        }
        return values
    }
}
