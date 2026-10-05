package io.github.veritasx1.lical

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A calendar event in the format of docs/DATA.md (the same JSON as on Ubuntu). */
data class Event(
    val id: String,
    val calendar: String,
    val title: String,
    val allDay: Boolean,
    val start: String,
    val end: String,
    val location: String = "",
    val notes: String = "",
    val rrule: String? = null,
    val exdates: List<String> = emptyList(),
    /** An absence (Urlaub, Krank …, see Editing.ABSENCES) and who stands in. */
    val absence: String? = null,
    val deputy: String? = null,
    /** Alerts (card 91adf47e): minutes before the start, at most two (see Alerts). */
    val alerts: List<Int> = emptyList(),
    /** Time zone (card 7a9187d6): an IANA name – the times are wall-clock times there. Null: local, floating. */
    val tz: String? = null,
)

/** One occurrence of an event (a repeating event has many): its own start and end, and a key. */
data class Occurrence(val event: Event, val start: String, val end: String, val key: String,
                      /** For an event in another zone: its own times there (start/end are local). */
                      val zoneStart: String? = null, val zoneEnd: String? = null) {
    val allDay get() = event.allDay
    val title get() = event.title
}

/** A day or a moment: "2026-10-04" (date) or "2026-10-04T09:00" (with a time). */
data class Moment(val at: LocalDateTime, val hasTime: Boolean) : Comparable<Moment> {
    val date: LocalDate get() = at.toLocalDate()
    override fun compareTo(other: Moment) = at.compareTo(other.at)
}

/** Calendar rules shared with Ubuntu – the twin of linux/lical/rules.py. RulesTest replays the
 *  cases in shared/cases/rules.json, written by the Python tests: keep both in step. */
object Rules {
    private val WEEKDAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private const val MAX_OCCURRENCES = 5000
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun parse(text: String): Moment =
        if ('T' in text) Moment(LocalDateTime.parse(text.take(16), DATE_TIME), true)
        else Moment(LocalDate.parse(text.take(10), DATE).atStartOfDay(), false)

    fun stamp(value: Moment): String = if (value.hasTime) value.at.format(DATE_TIME) else value.date.format(DATE)
    fun stamp(value: LocalDate): String = value.format(DATE)

    // ---- months and weeks ----

    /** The first day of day's week (firstWeekday 0 = Monday, 6 = Sunday). */
    fun weekStart(day: LocalDate, firstWeekday: Int = 0): LocalDate =
        day.minusDays((((day.dayOfWeek.value - 1) - firstWeekday) % 7 + 7) % 7L)

    /** The weeks a month touches, each as its 7 days. */
    fun monthWeeks(year: Int, month: Int, firstWeekday: Int = 0): List<List<LocalDate>> {
        val first = LocalDate.of(year, month, 1)
        val following = first.plusMonths(1)
        var start = weekStart(first, firstWeekday)
        val weeks = mutableListOf<List<LocalDate>>()
        while (start < following) {
            weeks.add((0 until 7).map { start.plusDays(it.toLong()) })
            start = start.plusDays(7)
        }
        return weeks
    }

    fun addMonths(day: LocalDate, months: Long): LocalDate = day.plusMonths(months)

    // ---- repetition (a subset of iCalendar RRULE) ----

    private fun ruleParts(rrule: String?): Map<String, String> =
        (rrule ?: "").split(";").filter { '=' in it }.associate { item ->
            val (key, value) = item.split("=", limit = 2)
            key.trim().uppercase() to value.trim().uppercase()
        }

    fun occurrenceStarts(event: Event, until: LocalDateTime): List<Moment> {
        val first = parse(event.start)
        val parts = ruleParts(event.rrule)
        val frequency = parts["FREQ"]
        val excluded = event.exdates.toSet()
        if (frequency !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY"))
            return if (first.at < until && stamp(first).take(10) !in excluded) listOf(first) else emptyList()
        val interval = maxOf(1, parts["INTERVAL"]?.toIntOrNull() ?: 1)
        val count = parts["COUNT"]?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toInt()
        val untilText = parts["UNTIL"] ?: ""
        val last = if (untilText.length >= 8) LocalDate.of(untilText.substring(0, 4).toInt(), untilText.substring(4, 6).toInt(), untilText.substring(6, 8).toInt()) else null
        val weekdays = (parts["BYDAY"] ?: "").split(",").map { it.takeLast(2) }.filter { it in WEEKDAYS }.map { WEEKDAYS.indexOf(it) }.sorted()
        val result = mutableListOf<Moment>()
        var produced = 0
        val candidates = sequence {
            var step = 0L
            while (true) {
                when (frequency) {
                    "DAILY" -> yield(Moment(first.at.plusDays(step * interval), first.hasTime))
                    "WEEKLY" -> {
                        val base = first.at.plusWeeks(step * interval)
                        if (weekdays.isNotEmpty()) {
                            val monday = base.minusDays((base.dayOfWeek.value - 1).toLong())
                            for (weekday in weekdays) {
                                val candidate = monday.plusDays(weekday.toLong())
                                if (candidate >= first.at) yield(Moment(candidate, first.hasTime))
                            }
                        } else yield(Moment(base, first.hasTime))
                    }
                    else -> {
                        val base = first.date
                        val moved = addMonths(base, step * interval * (if (frequency == "YEARLY") 12 else 1))
                        // Months without that day (31st, 29 Feb) are left out, like Apple.
                        if (moved.dayOfMonth == base.dayOfMonth) yield(Moment(LocalDateTime.of(moved, first.at.toLocalTime()), first.hasTime))
                    }
                }
                step++
                if (step > MAX_OCCURRENCES * 4L) break
            }
        }
        for (candidate in candidates) {
            if (candidate.at >= until || (last != null && candidate.date > last)) break
            produced++
            if (count != null && produced > count) break
            if (stamp(candidate).take(10) !in excluded) result.add(candidate)
            if (result.size >= MAX_OCCURRENCES) break
        }
        return result
    }

    // ---- time zones (card 7a9187d6) ----

    /** A wall-clock time in zone `source` as wall-clock time in `target`. */
    fun convert(moment: LocalDateTime, source: String?, target: String?): LocalDateTime {
        if (source == null || target == null || source == target) return moment
        return runCatching { moment.atZone(java.time.ZoneId.of(source)).withZoneSameInstant(java.time.ZoneId.of(target)).toLocalDateTime() }.getOrDefault(moment)
    }

    /** The zone an event's times are in, if it differs from `zone` (null: floating, local). */
    fun eventZone(event: Event, zone: String): String? = event.tz?.takeIf { !event.allDay && it != zone }

    fun localZone(): String = java.time.ZoneId.systemDefault().id

    /** A local moment (a drag, a picked time) in the event's own zone. */
    fun toEventZone(event: Event, moment: LocalDateTime, zone: String = localZone()): LocalDateTime =
        eventZone(event, zone)?.let { convert(moment, zone, it) } ?: moment

    /** An occurrence with its times back in the event's own zone – what the editor changes. */
    fun forEditing(item: Occurrence): Occurrence {
        val zoneStart = item.zoneStart ?: return item
        val length = ChronoUnit.MINUTES.between(parse(zoneStart).at, parse(item.zoneEnd!!).at)
        return Occurrence(item.event, zoneStart, stamp(Moment(parse(zoneStart).at.plusMinutes(length), true)), item.key)
    }

    /** All occurrences overlapping [start, end), sorted like on Ubuntu. An event in another zone is
     *  repeated there and shown in `zone`; its own times stay in zoneStart/zoneEnd. */
    fun occurrences(events: List<Event>, start: LocalDate, end: LocalDate, zone: String = localZone()): List<Occurrence> {
        val windowStart = start.atStartOfDay()
        val windowEnd = end.atStartOfDay()
        val found = mutableListOf<Occurrence>()
        for (event in events) {
            val first = parse(event.start)
            var length = ChronoUnit.MINUTES.between(first.at, parse(event.end).at)
            if (length < 0) length = 0
            val own = eventZone(event, zone)
            for (begin in occurrenceStarts(event, if (own != null) windowEnd.plusDays(2) else windowEnd)) {
                val finish = begin.at.plusMinutes(length)
                val shownBegin = if (own != null) convert(begin.at, own, zone) else begin.at
                val shownFinish = if (own != null) convert(finish, own, zone) else finish
                val visibleEnd = if (shownFinish > shownBegin) shownFinish else shownBegin.plusMinutes(1)
                if (visibleEnd <= windowStart || shownBegin >= windowEnd) continue
                val startText = stamp(Moment(shownBegin, begin.hasTime))
                val endText = if (begin.hasTime) stamp(Moment(shownFinish, true)) else stamp(shownFinish.toLocalDate())
                found.add(Occurrence(event, startText, endText, "${event.id}@${stamp(begin)}",
                    if (own != null) stamp(begin) else null, if (own != null) stamp(Moment(finish, true)) else null))
            }
        }
        return found.sortedWith(ORDER)
    }

    /** Earlier first, all-day before timed, longer all-day before shorter, then by title. */
    val ORDER: Comparator<Occurrence> = compareBy<Occurrence>({ parse(it.start).date }, { if (it.allDay) 0 else 1 },
        { if (it.allDay) -ChronoUnit.SECONDS.between(parse(it.start).at, parse(it.end).at) else 0L },
        { parse(it.start).at }, { it.title }, { it.key })

    /** The days an occurrence shows on (all-day end is exclusive; ending at midnight does not count). */
    fun daysCovered(item: Occurrence): Pair<LocalDate, LocalDate> {
        val start = parse(item.start)
        val end = parse(item.end)
        return if (item.allDay) {
            start.date to maxOf(start.date, end.date.minusDays(1))
        } else {
            val first = start.date
            val last = if (end.at.toLocalTime() != LocalTime.MIDNIGHT || end.date == first) end.date else end.date.minusDays(1)
            first to maxOf(first, last)
        }
    }

    // ---- a week row of the month view ----

    data class Bar(val key: String, val first: Int, val last: Int, val lane: Int)
    data class WeekLayout(val bars: List<Bar>, val lines: Map<Int, List<String>>, val lanes: IntArray)

    fun weekLayout(items: List<Occurrence>, week: List<LocalDate>): WeekLayout {
        val firstDay = week.first()
        val lastDay = week.last()
        val bars = mutableListOf<Bar>()
        val lines = (0 until 7).associateWith { mutableListOf<String>() }
        val lanes = IntArray(7)
        val occupied = List(7) { mutableListOf<Int>() }
        for (item in items) {
            val (start, end) = daysCovered(item)
            if (end < firstDay || start > lastDay) continue
            val first = maxOf(0, ChronoUnit.DAYS.between(firstDay, start).toInt())
            val last = minOf(6, ChronoUnit.DAYS.between(firstDay, end).toInt())
            if (item.allDay || end > start) {
                var lane = 0
                while ((first..last).any { lane in occupied[it] }) lane++
                for (column in first..last) {
                    occupied[column].add(lane)
                    lanes[column] = maxOf(lanes[column], lane + 1)
                }
                bars.add(Bar(item.key, first, last, lane))
            } else lines.getValue(first).add(item.key)
        }
        return WeekLayout(bars, lines, lanes)
    }

    data class CellRows(val lanes: Int, val lines: List<String>, val hidden: Int)

    fun cellRows(layout: WeekLayout, column: Int, capacity: Int): CellRows {
        val lanes = layout.lanes[column]
        val lines = layout.lines.getValue(column)
        val barsHere = layout.bars.count { it.first <= column && column <= it.last }
        val total = barsHere + lines.size
        if (lanes + lines.size <= capacity) return CellRows(lanes, lines, 0)
        val room = maxOf(0, capacity - 1)
        val shownLanes = minOf(lanes, room)
        val shownBars = layout.bars.count { it.first <= column && column <= it.last && it.lane < shownLanes }
        val shownLines = lines.take(maxOf(0, room - shownLanes))
        return CellRows(shownLanes, shownLines, total - shownBars - shownLines.size)
    }

    // ---- timeline (day and week) ----

    data class Block(val key: String, val top: Int, val bottom: Int, var column: Int = 0, var columns: Int = 1)

    fun timelineLayout(items: List<Occurrence>, day: LocalDate): List<Block> {
        val dayStart = day.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val blocks = mutableListOf<Block>()
        for (item in items) {
            if (item.allDay) continue
            val start = parse(item.start).at
            val end = parse(item.end).at
            if (end <= dayStart || start >= dayEnd) continue
            val top = maxOf(0, ChronoUnit.MINUTES.between(dayStart, start).toInt())
            var bottom = minOf(24 * 60, ChronoUnit.MINUTES.between(dayStart, end).toInt())
            bottom = maxOf(bottom, top + 15)
            blocks.add(Block(item.key, top, bottom))
        }
        blocks.sortWith(compareBy<Block>({ it.top }, { -it.bottom }, { it.key }))
        var group = mutableListOf<Block>()
        var groupEnd = -1
        fun close() {
            if (group.isEmpty()) return
            val columnsEnd = mutableListOf<Int>()
            for (member in group) {
                val free = columnsEnd.indexOfFirst { it <= member.top }
                if (free >= 0) {
                    member.column = free
                    columnsEnd[free] = member.bottom
                } else {
                    member.column = columnsEnd.size
                    columnsEnd.add(member.bottom)
                }
            }
            for (member in group) member.columns = columnsEnd.size
        }
        for (block in blocks) {
            if (block.top >= groupEnd) {
                close()
                group = mutableListOf()
                groupEnd = -1
            }
            group.add(block)
            groupEnd = maxOf(groupEnd, block.bottom)
        }
        close()
        return blocks
    }
}
