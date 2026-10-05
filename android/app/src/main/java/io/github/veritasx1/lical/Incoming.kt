package io.github.veritasx1.lical

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What another app asks LiCal as the phone's calendar app: open the calendar or a day, show an event,
 *  "Termin hinzufügen" (browser, mail, booking apps), open an .ics file. Nothing is saved without ✓. */
data class Incoming(val screen: Screen = Screen.Month, val day: LocalDate = LocalDate.now(), val request: EditRequest? = null)

object IncomingIntents {
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private const val MAX_ICS = 512 * 1024

    fun read(context: Context, store: Store, intent: Intent?): Incoming? {
        intent ?: return null
        intent.getStringExtra(MainActivity.DAY)?.let { text ->
            return runCatching { Incoming(Screen.Day, LocalDate.parse(text)) }.getOrNull()
        }
        val data = intent.data
        val type = intent.type ?: data?.let { runCatching { context.contentResolver.getType(it) }.getOrNull() }
        return when (intent.action) {
            Intent.ACTION_INSERT, Intent.ACTION_EDIT -> if (data?.authority == CalendarContract.AUTHORITY && data.pathSegments.firstOrNull() == "events"
                && data.lastPathSegment?.toLongOrNull() != null && intent.action == Intent.ACTION_EDIT) viewEvent(store, data.lastPathSegment!!.toLong())
                else insert(store, intent)
            Intent.ACTION_VIEW -> when {
                data == null -> null
                type == "text/calendar" || data.lastPathSegment?.endsWith(".ics", ignoreCase = true) == true -> file(context, store, intent)
                data.authority == CalendarContract.AUTHORITY && data.pathSegments.firstOrNull() == "time" ->
                    data.lastPathSegment?.toLongOrNull()?.let { Incoming(Screen.Day, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                data.authority == CalendarContract.AUTHORITY && data.pathSegments.firstOrNull() == "events" ->
                    data.lastPathSegment?.toLongOrNull()?.let { viewEvent(store, it) }
                else -> null
            }
            else -> null
        }
    }

    /** CalendarContract's "insert" extras – the way apps hand over an event. */
    private fun insert(store: Store, intent: Intent): Incoming {
        val zone = ZoneId.systemDefault()
        val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        val beginMillis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1)
        val endMillis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1)
        val base = Editing.newEvent(LocalDate.now(), LocalDateTime.now(), store.defaultCalendar(), java.util.UUID.randomUUID().toString().replace("-", ""))
        var event = base
        if (beginMillis > 0) {
            event = if (allDay) {
                val first = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
                val last = if (endMillis > beginMillis) Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate() else first.plusDays(1)
                base.copy(allDay = true, start = Rules.stamp(first), end = Rules.stamp(maxOf(last, first.plusDays(1))))
            } else {
                val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(beginMillis), zone)
                val end = if (endMillis > beginMillis) LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone) else start.plusHours(1)
                base.copy(start = start.format(STAMP), end = end.format(STAMP))
            }
        }
        event = event.copy(
            title = intent.getStringExtra(CalendarContract.Events.TITLE)?.trim().orEmpty().ifEmpty { "Neuer Termin" },
            location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION)?.trim().orEmpty(),
            notes = intent.getStringExtra(CalendarContract.Events.DESCRIPTION)?.trim().orEmpty(),
            rrule = intent.getStringExtra(CalendarContract.Events.RRULE)?.ifEmpty { null },
        )
        return Incoming(Screen.Day, Rules.parse(event.start).date, EditRequest(event, null, true))
    }

    private fun viewEvent(store: Store, id: Long): Incoming? {
        val event = store.event(DEVICE + id) ?: return null
        return Incoming(Screen.Day, Rules.parse(event.start).date, EditRequest(event, if (event.rrule != null) event.start else null, false))
    }

    /** An .ics file (mail attachment, download): its first event, ready to be checked and saved. */
    private fun file(context: Context, store: Store, intent: Intent): Incoming? {
        val text = runCatching {
            context.contentResolver.openInputStream(intent.data!!)?.use { input -> input.readNBytesCompat(MAX_ICS).decodeToString() }
        }.getOrNull() ?: return null
        val found = Ics.fromIcs(text) ?: return null
        val event = found.copy(id = java.util.UUID.randomUUID().toString().replace("-", ""), calendar = store.defaultCalendar())
        return Incoming(Screen.Day, Rules.parse(event.start).date, EditRequest(event, null, true))
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
