package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class CalendarInfo(val id: String, val name: String, val color: String, val visible: Boolean = true)

/** The computed holiday calendar (card 7a9187d6): read-only, kept apart from the own calendars.
 *  chosen: switched on or off by hand – until then it stays hidden while a phone holiday calendar shows. */
data class HolidaySettings(val name: String = "Feiertage", val color: String = "purple", val visible: Boolean = true,
                           val state: String = "", val chosen: Boolean = false)

/** Calendars and events on this phone: one JSON file in the app's private folder, the same format as
 *  on Ubuntu (docs/DATA.md). Never in a cloud backup (res/xml/no_backup.xml). */
class Store(private val file: File?, private val device: DeviceCalendars? = null) {
    var calendars = DEFAULT_CALENDARS.toMutableList()
        private set
    var events = mutableListOf<Event>()
        private set
    var holidays = HolidaySettings()
        private set
    /** Bumped on every change – the screens read it to redraw. */
    var revision by mutableIntStateOf(0)
        private set

    init {
        if (file != null && file.exists()) runCatching {
            val data = JSONObject(file!!.readText())
            data.optJSONArray("calendars")?.let { array -> calendars = (0 until array.length()).map { calendarFrom(array.getJSONObject(it)) }.toMutableList() }
            data.optJSONArray("events")?.let { array -> events = (0 until array.length()).map { eventFrom(array.getJSONObject(it)) }.toMutableList() }
            data.optJSONObject("holidays")?.let { json ->
                holidays = HolidaySettings(json.optString("name", "Feiertage").let { if (it == tr("Feiertage")) "Feiertage" else it }, json.optString("color", "purple"), json.optBoolean("visible", true),
                    json.optString("state"), json.optBoolean("chosen"))
            }
        }
    }

    /** The phone's calendars (Google via DAVx⁵ …), read when allowed; color as "#RRGGBB". */
    var deviceCalendars: List<DeviceCalendar> = emptyList()
        private set

    /** Called after LiCal's own events were written (alerts are set again). */
    var onSaved: (() -> Unit)? = null

    /** Called once the phone's calendars may be read (start watching them for changes). */
    var onDeviceAllowed: (() -> Unit)? = null

    fun refreshDevice() {
        deviceCalendars = device?.calendars() ?: emptyList()
        if (device?.permitted() == true) onDeviceAllowed?.invoke()
        revision++
    }

    /** Something changed in the phone's calendars (DAVx⁵ synchronised): draw again. */
    fun touch() { revision++ }

    fun devicePermitted() = device?.permitted() == true

    private fun deviceInfo(calendar: DeviceCalendar) = CalendarInfo(DEVICE + calendar.id, calendar.name,
        "#%06X".format(calendar.color and 0xFFFFFF), DEVICE + calendar.id !in Settings.hiddenDevice)

    /** LiCal's own calendars, then the phone's – for the list and for colors. */
    fun allCalendars(): List<CalendarInfo> = calendars + deviceCalendars.map(::deviceInfo)

    /** Calendars an event can be put into (the phone's read-only ones – holidays – not). */
    fun writableCalendars(): List<CalendarInfo> = calendars + deviceCalendars.filter { it.writable && device?.writable() == true }.map(::deviceInfo)

    /** A phone calendar of holidays is shown (Google's "Feiertage in Deutschland" …)? */
    private fun phoneHolidaysShown() = deviceCalendars.any { calendar ->
        DEVICE + calendar.id !in Settings.hiddenDevice && listOf("feiertag", "holiday").any { it in calendar.name.lowercase() }
    }

    fun holidaysShown() = holidays.visible && (holidays.chosen || !phoneHolidaysShown())

    // The stored default name was written in the language of that day – show it in today's (Michelle 16e05d3c)
    fun holidayInfo() = CalendarInfo(Holidays.CALENDAR, if (holidays.name == "Feiertage") tr("Feiertage") else holidays.name, holidays.color, holidaysShown())

    fun setHolidays(settings: HolidaySettings) {
        holidays = settings
        save()
    }

    fun calendar(id: String) = if (id == Holidays.CALENDAR) holidayInfo() else calendars.firstOrNull { it.id == id } ?: deviceCalendars.firstOrNull { DEVICE + it.id == id }?.let(::deviceInfo)

    /** Where new events go: the chosen default if it can still be written, else the first visible writable one. */
    fun defaultCalendar(): String {
        val writable = writableCalendars()
        return writable.firstOrNull { it.id == Settings.defaultCalendar }?.id ?: writable.firstOrNull { it.visible }?.id ?: calendars.first().id
    }

    fun isReadOnly(calendarId: String) = calendarId == Holidays.CALENDAR || calendarId.startsWith(DEVICE) && writableCalendars().none { it.id == calendarId }

    fun occurrences(start: LocalDate, end: LocalDate): List<Occurrence> {
        val shown = calendars.filter { it.visible }.map { it.id }.toSet()
        val ownEvents = events.filter { it.calendar in shown } +
            if (holidaysShown()) Holidays.events(start, end.minusDays(1), holidays.state) else emptyList()
        val own = Rules.occurrences(ownEvents, start, end)
        val phone = deviceCalendars.filter { DEVICE + it.id !in Settings.hiddenDevice }.map { it.id }.toSet()
        if (phone.isEmpty() || device == null) return own
        return (own + device.occurrences(start, end, phone)).sortedWith(Rules.ORDER)
    }

    fun replaceAll(newEvents: List<Event>) {
        events = newEvents.toMutableList()
        save()
    }

    /** Add or replace an event (same id). */
    /** Add or replace an event – in LiCal's file or, for a phone calendar, in Android's calendar store
     *  (moving between the two deletes it on the old side). */
    fun put(event: Event): Event {
        val wasPhone = event.id.startsWith(DEVICE)
        val toPhone = event.calendar.startsWith(DEVICE)
        if (toPhone && device != null) {
            if (!wasPhone) events = events.filter { it.id != event.id }.toMutableList()
            val id = device.save(if (wasPhone) event else event.copy(id = "new"))
            if (!wasPhone) save() else revision++
            return event.copy(id = id)
        }
        if (wasPhone) device?.delete(event.id)
        val own = if (wasPhone) event.copy(id = java.util.UUID.randomUUID().toString().replace("-", "")) else event
        events = (events.filter { it.id != own.id } + own).toMutableList()
        save()
        return own
    }

    fun delete(id: String) {
        if (id.startsWith(DEVICE)) {
            device?.delete(id)
            revision++
            return
        }
        events = events.filter { it.id != id }.toMutableList()
        save()
    }

    fun event(id: String): Event? =
        if (id.startsWith(DEVICE)) id.removePrefix(DEVICE).toLongOrNull()?.let { device?.event(it) } else events.firstOrNull { it.id == id }

    // ---- own calendars (Olaf 07.10.: „Kalender hinzufügen“ like Apple – name, color, not shared; twin of store.py) ----

    fun addCalendar(name: String, color: String): CalendarInfo {
        val calendar = CalendarInfo(java.util.UUID.randomUUID().toString().replace("-", ""), name.trim(), color)
        calendars = (calendars + calendar).toMutableList()
        save()
        return calendar
    }

    fun updateCalendar(id: String, name: String, color: String) {
        calendars = calendars.map { if (it.id == id) it.copy(name = name.trim(), color = color) else it }.toMutableList()
        save()
    }

    /** The calendar and its events (the sheet asks first). The last one stays. */
    fun deleteCalendar(id: String): Boolean {
        if (calendars.size <= 1 || calendars.none { it.id == id }) return false
        calendars = calendars.filter { it.id != id }.toMutableList()
        events = events.filter { it.calendar != id }.toMutableList()
        save()
        return true
    }

    fun setVisible(id: String, visible: Boolean) {
        if (id == Holidays.CALENDAR) {
            setHolidays(holidays.copy(visible = visible, chosen = true))
            return
        }
        if (id.startsWith(DEVICE)) {
            Settings.hiddenDevice = if (visible) Settings.hiddenDevice - id else Settings.hiddenDevice + id
            revision++
            return
        }
        calendars = calendars.map { if (it.id == id) it.copy(visible = visible) else it }.toMutableList()
        save()
    }

    fun save() {
        revision++
        val target = file ?: return
        val data = JSONObject().put("version", 1)
            .put("calendars", JSONArray(calendars.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("visible", it.visible) }))
            .put("holidays", JSONObject().put("name", holidays.name).put("color", holidays.color).put("visible", holidays.visible)
                .put("state", holidays.state).put("chosen", holidays.chosen))
            .put("events", JSONArray(events.map(::eventJson)))
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".tmp")
        temporary.writeText(data.toString(1))
        temporary.renameTo(target)
        onSaved?.invoke()
    }

    companion object {
        val DEFAULT_CALENDARS = listOf(CalendarInfo("privat", tr("Privat"), "blue"), CalendarInfo("arbeit", tr("Arbeit"), "orange"), CalendarInfo("familie", tr("Familie"), "green"))

        fun calendarFrom(json: JSONObject) = CalendarInfo(json.getString("id"), json.optString("name"), json.optString("color", "blue"), json.optBoolean("visible", true))

        fun eventFrom(json: JSONObject) = Event(
            id = json.getString("id"), calendar = json.optString("calendar"), title = json.optString("title"),
            allDay = json.optBoolean("allDay"), start = json.getString("start"), end = json.optString("end", json.getString("start")),
            location = json.optString("location"), notes = json.optString("notes"),
            rrule = json.optString("rrule").ifEmpty { null },
            exdates = json.optJSONArray("exdates")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList(),
            absence = json.optString("absence").ifEmpty { null },
            deputy = json.optString("deputy").ifEmpty { null },
            alerts = json.optJSONArray("alerts")?.let { array -> (0 until array.length()).map { array.getInt(it) } } ?: emptyList(),
            tz = json.optString("tz").ifEmpty { null },
        )

        fun eventJson(event: Event): JSONObject = JSONObject().put("id", event.id).put("calendar", event.calendar).put("title", event.title)
            .put("allDay", event.allDay).put("start", event.start).put("end", event.end).apply {
                if (event.location.isNotEmpty()) put("location", event.location)
                if (event.notes.isNotEmpty()) put("notes", event.notes)
                event.rrule?.let { put("rrule", it) }
                if (event.exdates.isNotEmpty()) put("exdates", JSONArray(event.exdates))
                event.absence?.let { put("absence", it) }
                event.deputy?.let { put("deputy", it) }
                if (event.alerts.isNotEmpty()) put("alerts", JSONArray(event.alerts))
                event.tz?.let { put("tz", it) }
            }

        /** Examples around today for pictures and tests – the same as store.demo_events on Ubuntu. */
        fun demoEvents(today: LocalDate = LocalDate.now()): List<Event> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            fun d(offset: Long) = monday.plusDays(offset)
            fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(day.year, day.month, day.dayOfMonth, hour, minute).format(format)
            data class Item(val calendar: String, val title: String, val start: String, val end: String, val allDay: Boolean, val rrule: String?, val place: String)
            val items = listOf(
                Item("arbeit", "Jour fixe Team", at(d(0), 9), at(d(0), 10), false, "FREQ=WEEKLY;BYDAY=MO", "Besprechungsraum 2"),
                Item("arbeit", "Review Release 2.2", at(d(1), 10), at(d(1), 11, 30), false, null, ""),
                Item("arbeit", "Kundentermin Müller", at(d(1), 11), at(d(1), 12), false, null, "Köln"),
                Item("privat", "Zahnarzt", at(d(2), 8, 30), at(d(2), 9, 15), false, null, "Dr. Weber"),
                Item("arbeit", "Mittag mit Jens", at(d(2), 12, 30), at(d(2), 13, 30), false, null, ""),
                Item("familie", "Elternabend", at(d(3), 19), at(d(3), 20, 30), false, null, "Grundschule"),
                Item("privat", "Laufen", at(d(4), 7), at(d(4), 8), false, "FREQ=WEEKLY;BYDAY=TU,FR", ""),
                Item("familie", "Kino mit Mia", at(d(5), 20), at(d(5), 22, 15), false, null, "Cinedom"),
                Item("familie", "Mias Geburtstag", Rules.stamp(d(9)), Rules.stamp(d(10)), true, "FREQ=YEARLY", ""),
                Item("privat", "Urlaub Kreta", Rules.stamp(d(16)), Rules.stamp(d(23)), true, null, ""),
                Item("arbeit", "Messe Köln", at(d(8), 9), at(d(10), 17), false, null, "Koelnmesse"),
                Item("arbeit", "Sprint-Planung", at(d(14), 10), at(d(14), 12), false, "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO", ""),
                Item("privat", "Steuerberater", at(d(11), 16), at(d(11), 17), false, null, ""),
                Item("familie", "Oma besuchen", at(d(6), 14), at(d(6), 17), false, null, ""),
                Item("arbeit", "Tag der offenen Tür", Rules.stamp(d(-3)), Rules.stamp(d(-2)), true, null, ""),
                Item("privat", "Wochenmarkt", at(d(5), 9), at(d(5), 10), false, "FREQ=WEEKLY;BYDAY=SA", ""),
                Item("arbeit", "1:1 mit Sabine", at(d(3), 14), at(d(3), 14, 30), false, "FREQ=WEEKLY;BYDAY=TH", ""),
                Item("arbeit", "Abgabe Konzept", Rules.stamp(d(4)), Rules.stamp(d(5)), true, null, ""),
            )
            return items.mapIndexed { index, item -> Event("demo$index", item.calendar, item.title, item.allDay, item.start, item.end, item.place, rrule = item.rrule) }
        }
    }
}
