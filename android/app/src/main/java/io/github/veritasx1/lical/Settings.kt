package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import android.content.Context
import android.content.SharedPreferences

/** Small view preferences of this phone (not calendar data): the month view's compact mode. */
object Settings {
    private var preferences: SharedPreferences? = null

    fun init(context: Context) {
        preferences = context.getSharedPreferences("ansicht", Context.MODE_PRIVATE)
    }

    /** Phone calendars the user hid in LiCal (only in LiCal – the phone's own setting stays). */
    var hiddenDevice: Set<String>
        get() = preferences?.getStringSet("handy-aus", emptySet())?.toSet() ?: memoryHidden
        set(value) {
            memoryHidden = value
            preferences?.edit()?.putStringSet("handy-aus", value)?.apply()
        }
    private var memoryHidden: Set<String> = emptySet()

    /** Where new events go (iOS: Einstellungen › Kalender › Standardkalender); null: the first writable one. */
    var defaultCalendar: String?
        get() = preferences?.getString("standardkalender", null) ?: memoryDefault
        set(value) {
            memoryDefault = value
            preferences?.edit()?.putString("standardkalender", value)?.apply()
        }
    private var memoryDefault: String? = null

    /** "Zeitzonen-Unterstützung" (card 7a9187d6): events may carry their own zone (iOS: Zeitzone-Override). */
    var timeZones: Boolean
        get() = preferences?.getBoolean("zeitzonen", false) ?: memoryZones
        set(value) {
            memoryZones = value
            preferences?.edit()?.putBoolean("zeitzonen", value)?.apply()
        }
    private var memoryZones = false

    /** Zones offered in the editor: the big places, like the cities iOS suggests first. */
    val ZONES = listOf("Europe/London", "Europe/Lisbon", "Europe/Paris", "Europe/Berlin", "Europe/Vienna", "Europe/Zurich",
        "Europe/Rome", "Europe/Madrid", "Europe/Athens", "Europe/Helsinki", "Europe/Istanbul", "Europe/Moscow",
        "Africa/Cairo", "Africa/Johannesburg", "Asia/Dubai", "Asia/Kolkata", "Asia/Bangkok", "Asia/Singapore",
        "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Tokyo", "Asia/Seoul", "Australia/Perth", "Australia/Sydney",
        "Pacific/Auckland", "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver",
        "America/Chicago", "America/New_York", "America/Toronto", "America/Mexico_City", "America/Bogota",
        "America/Sao_Paulo", "America/Buenos_Aires")

    /** "America/New_York" → "New York (Amerika)" – as on Ubuntu (settings.zone_label). */
    fun zoneLabel(name: String): String {
        val regions = mapOf("Africa" to "Afrika", "America" to "Amerika", "Antarctica" to "Antarktis", "Asia" to "Asien",
            "Atlantic" to "Atlantik", "Australia" to "Australien", "Europe" to "Europa", "Indian" to tr("Indischer Ozean"), "Pacific" to "Pazifik")
        if ('/' !in name) return name
        val region = name.substringBefore('/')
        return "${name.substringAfterLast('/').replace('_', ' ')} (${regions[region] ?: region})"
    }

    /** How the month shows events – Apple's default: compact (marks under the day numbers). */
    var monthMode: MonthMode
        get() = runCatching { MonthMode.valueOf(preferences?.getString("monat", null) ?: "Compact") }.getOrDefault(MonthMode.Compact)
        set(value) { preferences?.edit()?.putString("monat", value.name)?.apply() }
}

/** The month's three looks on the iPhone: marks, event pills, month with the day's list. */
enum class MonthMode(val label: String) { Compact("Kompakt"), Stacked("Gestapelt"), Details("Details") }
