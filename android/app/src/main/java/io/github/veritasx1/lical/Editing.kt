package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Editing rules shared with Ubuntu – the twin of linux/lical/editing.py (EditingTest replays
 *  shared/cases/editing.json). They only change events; the screens call them. */
object Editing {
    val REPEATS = listOf(null to tr("Nie"), "FREQ=DAILY" to tr("Täglich"), "FREQ=WEEKLY" to tr("Wöchentlich"),
        "FREQ=WEEKLY;INTERVAL=2" to tr("Alle 2 Wochen"), "FREQ=MONTHLY" to tr("Monatlich"), "FREQ=YEARLY" to tr("Jährlich"))

    private fun stamp(at: LocalDateTime) = Rules.stamp(Moment(at, true))

    /** Absences (card 471febc2): key, label, away. Same list as editing.ABSENCES on Ubuntu. */
    data class Absence(val key: String, val label: String, val away: Boolean)
    val ABSENCES = listOf(Absence("urlaub", "Urlaub", true), Absence("krank", "Krank", true), Absence("weiterbildung", "Weiterbildung", true),
        Absence("freistellung", "Freistellung", true), Absence("abwesend", tr("Abwesend"), true), Absence("dienstreise", "Dienstreise", true),
        Absence("anderer_ort", tr("An einem anderen Ort tätig"), false), Absence("homeoffice", tr("Im Homeoffice"), false),
        Absence("office", tr("Im Office"), false), Absence("anwesend", "Anwesend", false))
    fun absenceLabel(key: String?) = ABSENCES.firstOrNull { it.key == key }?.label ?: key ?: ""

    /** Make an event an absence (null: ordinary again); all-day; a generic title becomes the kind's name. */
    fun setAbsence(event: Event, kind: String?): Event {
        if (kind == null) return event.copy(absence = null, deputy = null)
        val allDay = setAllDay(event, true)
        val generic = allDay.title.isEmpty() || allDay.title == "Neuer Termin" || ABSENCES.any { it.label == allDay.title }
        return allDay.copy(absence = kind, title = if (generic) absenceLabel(kind) else allDay.title)
    }

    fun setDeputy(event: Event, name: String?) = event.copy(deputy = name?.trim()?.ifEmpty { null })

    /** "1–4 Wochen": all-day from the start, so many weeks long. */
    fun absenceWeeks(event: Event, weeks: Int): Event {
        val allDay = setAllDay(event, true)
        return allDay.copy(end = Rules.stamp(Rules.parse(allDay.start).date.plusDays(7L * weeks)))
    }

    /** "Urlaub · Vertretung: Jens" ("" for an ordinary event). */
    fun absenceText(event: Event): String {
        if (event.absence == null) return ""
        return absenceLabel(event.absence) + (event.deputy?.let { tr(" · Vertretung: {it}", "it" to it) } ?: "")
    }

    /** On today the next full hour, on another day 9:00 (or the hour given), one hour, "Neuer Termin". */
    fun newEvent(day: LocalDate, now: LocalDateTime, calendar: String, id: String, hour: Int? = null): Event {
        val h = hour ?: if (day == now.toLocalDate()) minOf(23, now.hour + 1) else 9
        val start = day.atTime(h, 0)
        return Event(id, calendar, tr("Neuer Termin"), false, stamp(start), stamp(start.plusHours(1)))
    }

    fun durationMinutes(event: Event) = ChronoUnit.MINUTES.between(Rules.parse(event.start).at, Rules.parse(event.end).at)

    /** Move the start; the length stays. [start] without a time keeps the old time. */
    fun setStart(event: Event, start: Moment): Event {
        val length = durationMinutes(event)
        return if (event.allDay) {
            val day = start.date
            event.copy(start = Rules.stamp(day), end = Rules.stamp(day.plusDays(maxOf(1L, length / (24 * 60)))))
        } else {
            val moment = if (start.hasTime) start.at else start.date.atTime(Rules.parse(event.start).at.toLocalTime())
            event.copy(start = stamp(moment), end = stamp(moment.plusMinutes(length)))
        }
    }

    /** Change the end, never before the start (all-day: [end] is the last day shown). */
    fun setEnd(event: Event, end: Moment): Event {
        val start = Rules.parse(event.start)
        return if (event.allDay) {
            event.copy(end = Rules.stamp(maxOf(end.date, start.date).plusDays(1)))
        } else {
            val moment = if (end.hasTime) end.at else end.date.atTime(Rules.parse(event.end).at.toLocalTime())
            event.copy(end = stamp(maxOf(moment, start.at.plusMinutes(5))))
        }
    }

    /** The last day an all-day event covers (the editor's "Ende"). */
    fun lastDay(event: Event): LocalDate {
        val end = Rules.parse(event.end)
        return if (event.allDay) end.date.minusDays(1) else end.date
    }

    fun setAllDay(event: Event, on: Boolean): Event {
        if (event.allDay == on) return event
        val start = Rules.parse(event.start)
        return if (on) {
            val end = Rules.parse(event.end)
            val first = start.date
            val last = if (end.at.toLocalTime() != LocalTime.MIDNIGHT || end.date == first) end.date else end.date.minusDays(1)
            event.copy(allDay = true, start = Rules.stamp(first), end = Rules.stamp(maxOf(first, last).plusDays(1)), alerts = emptyList())
        } else {
            // Alerts start over: the choices differ (like Apple).
            event.copy(allDay = false, start = stamp(start.date.atTime(9, 0)), end = stamp(start.date.atTime(10, 0)), alerts = emptyList())
        }
    }

    fun repeatLabel(rrule: String?) = REPEATS.firstOrNull { (it.first ?: "") == (rrule ?: "") }?.second ?: "Benutzerdefiniert"

    /** An occurrence was edited: the series takes its fields and moves by the same amount of time. */
    fun applyToSeries(series: Event, occurrenceStart: String, edited: Event): Event {
        val shift = ChronoUnit.MINUTES.between(Rules.parse(occurrenceStart).at, Rules.parse(edited.start).at)
        val length = durationMinutes(edited)
        val base = Rules.parse(series.start).at.plusMinutes(shift)
        val moved = if (edited.allDay) edited.copy(start = Rules.stamp(base.toLocalDate()), end = Rules.stamp(base.toLocalDate().plusDays(maxOf(1L, length / (24 * 60)))))
            else edited.copy(start = stamp(base), end = stamp(base.plusMinutes(length)))
        return moved.copy(id = series.id, rrule = series.rrule, exdates = series.exdates)
    }

    /** "Nur diesen Termin löschen": the day goes into the series' exceptions. */
    fun skipOccurrence(series: Event, occurrenceStart: String) =
        series.copy(exdates = (series.exdates + occurrenceStart.take(10)).toSortedSet().toList())
}
