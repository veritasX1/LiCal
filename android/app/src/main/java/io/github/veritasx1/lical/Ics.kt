package io.github.veritasx1.lical

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One event as iCalendar text (RFC 5545) – for passing it on as a QR code (card b483dadf). Even an
 *  iPhone's camera offers "Add to Calendar" for it. Only this event goes into the code – no calendar,
 *  no other people's data (privacy first). Twin of linux/lical/ics.py (shared/cases/ics.json). */
object Ics {
    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val MOMENT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun escape(text: String) = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\n").replace("\n", "\\n")

    fun unescape(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length) {
                val following = text[index + 1]
                result.append(if (following == 'n' || following == 'N') '\n' else following)
                index += 2
                continue
            }
            result.append(char)
            index += 1
        }
        return result.toString()
    }

    private fun moment(value: String, allDay: Boolean): String {
        val at = Rules.parse(value).at
        return if (allDay) at.format(DAY) else at.format(MOMENT)
    }

    /** The event as a VCALENDAR with one VEVENT (times as floating local time, all-day as dates). */
    fun toIcs(event: Event): String {
        val kind = if (event.allDay) ";VALUE=DATE" else event.tz?.let { ";TZID=$it" } ?: ""
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//LiCal//DE", "BEGIN:VEVENT",
            "SUMMARY:${escape(event.title)}",
            "DTSTART$kind:${moment(event.start, event.allDay)}",
            "DTEND$kind:${moment(event.end, event.allDay)}")
        if (event.location.isNotEmpty()) lines += "LOCATION:${escape(event.location)}"
        if (event.notes.isNotEmpty()) lines += "DESCRIPTION:${escape(event.notes)}"
        event.rrule?.let { lines += "RRULE:$it" }
        lines += listOf("END:VEVENT", "END:VCALENDAR")
        return lines.joinToString("\r\n") + "\r\n"
    }

    /** The first VEVENT of iCalendar text as an event without id/calendar (null if there is none).
     *  Times with "Z" (UTC) become local time; floating times stay; TZID times keep their zone (tz). */
    fun fromIcs(text: String, zone: ZoneId = ZoneId.systemDefault()): Event? {
        val unfolded = text.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").replace("\n\t", "")
        var inside = false
        val fields = mutableMapOf<String, Pair<String, List<String>>>()
        for (line in unfolded.replace("\r\n", "\n").split("\n")) {
            if (line.trim() == "BEGIN:VEVENT") { inside = true; continue }
            if (line.trim() == "END:VEVENT") break
            if (!inside || ':' !in line) continue
            val name = line.substringBefore(':')
            val value = line.substringAfter(':')
            val parts = name.split(";")
            fields.putIfAbsent(parts[0].uppercase(), value to parts.drop(1))
        }
        if ("DTSTART" !in fields) return null

        fun at(field: String): Pair<String, Boolean>? {
            val (raw, params) = fields.getValue(field)
            val value = raw.trim()
            return try {
                if (params.any { it.uppercase() == "VALUE=DATE" } || value.length == 8) {
                    "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}" to true
                } else {
                    var moment = LocalDateTime.of(value.substring(0, 4).toInt(), value.substring(4, 6).toInt(), value.substring(6, 8).toInt(),
                        value.substring(9, 11).toInt(), value.substring(11, 13).toInt())
                    if (value.endsWith("Z")) moment = moment.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
                    moment.format(STAMP) to false
                }
            } catch (error: RuntimeException) {
                null
            }
        }

        val (start, allDay) = at("DTSTART") ?: return null
        val end = (if ("DTEND" in fields) at("DTEND")?.first else null)
            ?: if (allDay) Rules.stamp(Rules.parse(start).date.plusDays(1))
            else Rules.parse(start).at.plusHours(1).format(STAMP)
        fun text(key: String) = fields[key]?.first?.let { unescape(it).trim() }.orEmpty()
        val tz = fields.getValue("DTSTART").second.firstOrNull { it.uppercase().startsWith("TZID=") }?.substringAfter("=")?.trim('"')
            ?.takeIf { !allDay && '/' in it && runCatching { ZoneId.of(it) }.isSuccess }
        return Event(id = "", calendar = "", title = text("SUMMARY").ifEmpty { "Termin" }, allDay = allDay, start = start, end = end,
            location = text("LOCATION"), notes = text("DESCRIPTION"), rrule = fields["RRULE"]?.first?.trim()?.ifEmpty { null }, tz = tz)
    }
}
