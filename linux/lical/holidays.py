"""German public holidays, computed on the device (card 7a9187d6) – no subscription, nothing fetched.
A read-only calendar "Feiertage" like Apple's: the nationwide days, plus those of one Bundesland when
chosen. Only days off by law for the whole state (not the ones of single towns). Twin: Holidays.kt
(shared/cases/holidays.json)."""

from datetime import date, timedelta

from .i18n import _

CALENDAR = "feiertage"
PREFIX = "feiertag:"

STATES = (
    ("", "Nur bundesweit"), ("BW", "Baden-Württemberg"), ("BY", "Bayern"), ("BE", "Berlin"), ("BB", "Brandenburg"),
    ("HB", "Bremen"), ("HH", "Hamburg"), ("HE", "Hessen"), ("MV", "Mecklenburg-Vorpommern"), ("NI", "Niedersachsen"),
    ("NW", "Nordrhein-Westfalen"), ("RP", "Rheinland-Pfalz"), ("SL", "Saarland"), ("SN", "Sachsen"),
    ("ST", "Sachsen-Anhalt"), ("SH", "Schleswig-Holstein"), ("TH", "Thüringen"),
)
ALL = "*"


def easter(year):
    """Easter Sunday (Gregorian, the anonymous algorithm of Meeus/Jones/Butcher)."""
    a, b, c = year % 19, year // 100, year % 100
    d, e = b // 4, b % 4
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    l = (32 + 2 * e + 2 * i - h - k) % 7  # noqa: E741
    m = (a + 11 * h + 22 * l) // 451
    month = (h + l - 7 * m + 114) // 31
    day = (h + l - 7 * m + 114) % 31 + 1
    return date(year, month, day)


def days(year, state=""):
    """[(date, name)] of the year, by date. state "" = nationwide only."""
    sunday = easter(year)
    found = []

    def add(day, name, where=ALL, since=None):
        if (where == ALL or state in where.split()) and (since is None or year >= since):
            found.append((day, name))

    add(date(year, 1, 1), _("Neujahr"))
    add(date(year, 1, 6), _("Heilige Drei Könige"), "BW BY ST")
    add(date(year, 3, 8), _("Internationaler Frauentag"), "BE", 2019)
    add(date(year, 3, 8), _("Internationaler Frauentag"), "MV", 2023)
    add(sunday - timedelta(days=2), _("Karfreitag"))
    add(sunday, _("Ostersonntag"), "BB")
    add(sunday + timedelta(days=1), _("Ostermontag"))
    add(date(year, 5, 1), _("Tag der Arbeit"))
    add(sunday + timedelta(days=39), _("Christi Himmelfahrt"))
    add(sunday + timedelta(days=49), _("Pfingstsonntag"), "BB")
    add(sunday + timedelta(days=50), _("Pfingstmontag"))
    add(sunday + timedelta(days=60), _("Fronleichnam"), "BW BY HE NW RP SL")
    add(date(year, 8, 15), _("Mariä Himmelfahrt"), "SL")
    add(date(year, 9, 20), _("Weltkindertag"), "TH", 2019)
    add(date(year, 10, 3), _("Tag der Deutschen Einheit"))
    if year == 2017:  # 500 years of the Reformation: once nationwide
        add(date(year, 10, 31), _("Reformationstag"))
    else:
        add(date(year, 10, 31), _("Reformationstag"), "BB MV SN ST TH")
        add(date(year, 10, 31), _("Reformationstag"), "HB HH NI SH", 2018)
    add(date(year, 11, 1), _("Allerheiligen"), "BW BY NW RP SL")
    # Buß- und Bettag: the Wednesday before 23 November.
    add(date(year, 11, 22) - timedelta(days=(date(year, 11, 22).weekday() - 2) % 7), _("Buß- und Bettag"), "SN")
    add(date(year, 12, 25), _("1. Weihnachtstag"))
    add(date(year, 12, 26), _("2. Weihnachtstag"))
    found.sort()
    return found


def events(first, last, state=""):
    """The holidays between two dates (inclusive) as read-only all-day events of the calendar "feiertage"."""
    result = []
    for year in range(first.year, last.year + 1):
        for day, name in days(year, state):
            if first <= day <= last:
                result.append({"id": f"{PREFIX}{day.isoformat()}", "calendar": CALENDAR, "title": name, "allDay": True,
                               "start": day.isoformat(), "end": (day + timedelta(days=1)).isoformat()})
    return result
