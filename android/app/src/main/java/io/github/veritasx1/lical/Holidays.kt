package io.github.veritasx1.lical

import io.github.veritasx1.lical.i18n.tr

import java.time.LocalDate

/** German public holidays, computed on the phone (card 7a9187d6) – no subscription, nothing fetched.
 *  A read-only calendar "Feiertage" like Apple's: the nationwide days plus one Bundesland's. Only days
 *  off by law for the whole state. Twin of linux/lical/holidays.py (shared/cases/holidays.json). */
object Holidays {
    const val CALENDAR = "feiertage"
    const val PREFIX = "feiertag:"
    val STATES = listOf("" to "Nur bundesweit", "BW" to "Baden-Württemberg", "BY" to "Bayern", "BE" to "Berlin",
        "BB" to "Brandenburg", "HB" to "Bremen", "HH" to "Hamburg", "HE" to "Hessen", "MV" to "Mecklenburg-Vorpommern",
        "NI" to "Niedersachsen", "NW" to "Nordrhein-Westfalen", "RP" to "Rheinland-Pfalz", "SL" to "Saarland",
        "SN" to "Sachsen", "ST" to "Sachsen-Anhalt", "SH" to "Schleswig-Holstein", "TH" to "Thüringen")

    /** Easter Sunday (Gregorian; Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** The year's holidays by date; state "" = nationwide only. */
    fun days(year: Int, state: String = ""): List<Pair<LocalDate, String>> {
        val sunday = easter(year)
        val found = mutableListOf<Pair<LocalDate, String>>()
        fun add(day: LocalDate, name: String, where: String = "*", since: Int? = null) {
            if ((where == "*" || state in where.split(" ")) && (since == null || year >= since)) found += day to name
        }
        add(LocalDate.of(year, 1, 1), tr("Neujahr"))
        add(LocalDate.of(year, 1, 6), tr("Heilige Drei Könige"), "BW BY ST")
        add(LocalDate.of(year, 3, 8), tr("Internationaler Frauentag"), "BE", 2019)
        add(LocalDate.of(year, 3, 8), tr("Internationaler Frauentag"), "MV", 2023)
        add(sunday.minusDays(2), tr("Karfreitag"))
        add(sunday, tr("Ostersonntag"), "BB")
        add(sunday.plusDays(1), tr("Ostermontag"))
        add(LocalDate.of(year, 5, 1), tr("Tag der Arbeit"))
        add(sunday.plusDays(39), tr("Christi Himmelfahrt"))
        add(sunday.plusDays(49), tr("Pfingstsonntag"), "BB")
        add(sunday.plusDays(50), tr("Pfingstmontag"))
        add(sunday.plusDays(60), tr("Fronleichnam"), "BW BY HE NW RP SL")
        add(LocalDate.of(year, 8, 15), tr("Mariä Himmelfahrt"), "SL")
        add(LocalDate.of(year, 9, 20), tr("Weltkindertag"), "TH", 2019)
        add(LocalDate.of(year, 10, 3), tr("Tag der Deutschen Einheit"))
        if (year == 2017) add(LocalDate.of(year, 10, 31), tr("Reformationstag"))  // 500 years: once nationwide
        else {
            add(LocalDate.of(year, 10, 31), tr("Reformationstag"), "BB MV SN ST TH")
            add(LocalDate.of(year, 10, 31), tr("Reformationstag"), "HB HH NI SH", 2018)
        }
        add(LocalDate.of(year, 11, 1), tr("Allerheiligen"), "BW BY NW RP SL")
        // Buß- und Bettag: the Wednesday before 23 November.
        val before = LocalDate.of(year, 11, 22)
        add(before.minusDays(((before.dayOfWeek.value - 1 - 2) % 7 + 7) % 7L), tr("Buß- und Bettag"), "SN")
        add(LocalDate.of(year, 12, 25), tr("1. Weihnachtstag"))
        add(LocalDate.of(year, 12, 26), tr("2. Weihnachtstag"))
        return found.sortedWith(compareBy({ it.first }, { it.second }))
    }

    /** The holidays between two dates (inclusive) as read-only all-day events of the calendar "feiertage". */
    fun events(first: LocalDate, last: LocalDate, state: String = ""): List<Event> = (first.year..last.year).flatMap { year ->
        days(year, state).filter { it.first in first..last }.map { (day, name) ->
            Event(PREFIX + day, CALENDAR, name, true, Rules.stamp(day), Rules.stamp(day.plusDays(1)))
        }
    }
}
