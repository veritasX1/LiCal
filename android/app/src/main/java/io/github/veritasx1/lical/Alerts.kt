package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One alert that rings: which occurrence, when, and what the notification shows. */
data class Alert(val key: String, val at: String, val minutes: Int, val id: String, val title: String, val start: String,
                 val end: String, val allDay: Boolean, val location: String)

/** Alerts before events (card 91adf47e) – Apple's choices and when they ring. Twin of
 *  linux/lical/alerts.py (shared/cases/alerts.json). An event keeps up to two numbers, minutes before
 *  its start; all-day events count from midnight of their first day ("Am Tag (9:00)" = -540). */
object Alerts {
    val TIMED get() = listOf(0 to tr("Zum Zeitpunkt des Ereignisses"), 5 to tr("5 Minuten vorher"), 10 to tr("10 Minuten vorher"),
        15 to tr("15 Minuten vorher"), 30 to tr("30 Minuten vorher"), 60 to tr("1 Stunde vorher"), 120 to tr("2 Stunden vorher"),
        1440 to tr("1 Tag vorher"), 2880 to tr("2 Tage vorher"), 10080 to tr("1 Woche vorher"))
    val ALL_DAY get() = listOf(-540 to tr("Am Tag des Ereignisses (9:00)"), 900 to tr("1 Tag vorher (9:00)"),
        2340 to tr("2 Tage vorher (9:00)"), 9540 to tr("1 Woche vorher (9:00)"))
    const val MOST = 2
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val WEEKDAYS get() = tr("Mo Di Mi Do Fr Sa So").split(" ")

    fun choices(allDay: Boolean) = if (allDay) ALL_DAY else TIMED

    fun label(minutes: Int?, allDay: Boolean): String {
        if (minutes == null) return "Keiner"
        choices(allDay).firstOrNull { it.first == minutes }?.let { return it.second }
        if (allDay) {  // counted from midnight of the first day
            val (whenText, clock) = if (minutes <= 0) tr("Am Tag des Ereignisses") to -minutes else {
                val days = Math.floorDiv(minutes + 1439, 1440)
                (if (days == 1) tr("1 Tag vorher") else tr("{days} Tage vorher", "days" to days)) to days * 1440 - minutes
            }
            return "$whenText (${clock / 60}:%02d)".format(clock % 60)
        }
        return when {
            minutes < 0 -> tr("{value} Minuten danach", "value" to (-minutes))
            minutes % 10080 == 0 -> if (minutes == 10080) tr("1 Woche vorher") else tr("{value} Wochen vorher", "value" to (minutes / 10080))
            minutes % 1440 == 0 -> tr("{value} Tage vorher", "value" to (minutes / 1440))
            minutes % 60 == 0 -> tr("{value} Stunden vorher", "value" to (minutes / 60))
            else -> tr("{minutes} Minuten vorher", "minutes" to minutes)
        }
    }

    /** Set the alerts (null entries are "Keiner"); at most two, no doubles, in the given order. */
    fun setAlerts(event: Event, alerts: List<Int?>): Event = event.copy(alerts = alerts.filterNotNull().distinct().take(MOST))

    /** The alerts that ring after `after` up to and including `until`, by time. */
    fun due(events: List<Event>, after: LocalDateTime, until: LocalDateTime): List<Alert> {
        val withAlerts = events.filter { it.alerts.isNotEmpty() }
        if (withAlerts.isEmpty()) return emptyList()
        val earliest = withAlerts.minOf { it.alerts.min() }
        val latest = withAlerts.maxOf { it.alerts.max() }
        val first = after.plusMinutes(minOf(earliest, 0).toLong()).toLocalDate()
        val last = until.plusMinutes(maxOf(latest, 0).toLong()).toLocalDate().plusDays(1)
        val found = mutableListOf<Alert>()
        for (item in Rules.occurrences(withAlerts, first, last)) {
            val start = Rules.parse(item.start).at
            for (minutes in item.event.alerts) {
                val at = start.minusMinutes(minutes.toLong())
                if (at > after && at <= until) found += Alert("${item.key}#$minutes", at.format(STAMP), minutes, item.event.id, item.title,
                    item.start, item.end, item.allDay, item.event.location)
            }
        }
        return found.sortedWith(compareBy({ it.at }, { it.key }))
    }

    /** When the next alert rings (null if none within `days`) – what the alarm is set to. */
    fun nextTime(events: List<Event>, after: LocalDateTime, days: Long = 40): LocalDateTime? =
        due(events, after, after.plusDays(days)).firstOrNull()?.let { LocalDateTime.parse(it.at, STAMP) }

    /** The notification's line under the title: "Heute, 08:30–09:15 · Dr. Weber". */
    fun text(alert: Alert, now: LocalDateTime): String {
        val start = Rules.parse(alert.start)
        val day = start.date
        val today = now.toLocalDate()
        var whenText = when (day) {
            today -> tr("Heute")
            today.plusDays(1) -> tr("Morgen")
            else -> "${WEEKDAYS[day.dayOfWeek.value - 1]}, ${Dates.numeric(day, year = false)}"
        }
        whenText += if (alert.allDay) tr(", ganztägig")
        else ", %02d:%02d–%02d:%02d".format(start.at.hour, start.at.minute, Rules.parse(alert.end).at.hour, Rules.parse(alert.end).at.minute)
        return whenText + if (alert.location.isNotEmpty()) " · ${alert.location}" else ""
    }
}
