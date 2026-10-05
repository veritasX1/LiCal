"""Events in plain language, like ＋ in the Mac's Calendar: "Party 6. Feb", "Fußballspiel Samstag von
11–13 Uhr", "Urlaub in Paris Mo–Fr", "Film heute Abend um 19 Uhr", "Frühstück" (9:00), "Mittagessen"
(12:00), "Abendessen" (20:00). German only; what is not understood stays in the title.
parse() gives {"title", "allDay", "start", "end"} – without a time an all-day event."""

import re
from datetime import date, datetime, timedelta

WEEKDAYS = {"montag": 0, "mo": 0, "dienstag": 1, "di": 1, "mittwoch": 2, "mi": 2, "donnerstag": 3, "do": 3,
            "freitag": 4, "fr": 4, "samstag": 5, "sa": 5, "sonntag": 6, "so": 6}
MONTHS = {"januar": 1, "jan": 1, "februar": 2, "feb": 2, "märz": 3, "mär": 3, "maerz": 3, "april": 4, "apr": 4, "mai": 5,
          "juni": 6, "jun": 6, "juli": 7, "jul": 7, "august": 8, "aug": 8, "september": 9, "sep": 9, "sept": 9,
          "oktober": 10, "okt": 10, "november": 11, "nov": 11, "dezember": 12, "dez": 12}
MEALS = {"frühstück": 9, "morgens": 9, "mittagessen": 12, "mittags": 12, "abendessen": 20, "abends": 20}
DASH = r"\s*(?:-|–|—|bis)\s*"
WEEKDAY = r"(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|mo|di|mi|do|fr|sa|so)\.?"
MONTH = r"(" + "|".join(sorted(MONTHS, key=len, reverse=True)) + r")\.?"
TIME = r"(\d{1,2})(?::|\.)?(\d{2})?"


ABSENCE_WORDS = (("urlaub", "urlaub"), ("krank", "krank"), ("weiterbildung", "weiterbildung"), ("fortbildung", "weiterbildung"),
                 ("schulung", "weiterbildung"), ("freistellung", "freistellung"), ("abwesend", "abwesend"),
                 ("dienstreise", "dienstreise"), ("homeoffice", "homeoffice"), ("home office", "homeoffice"))


def next_weekday(today, weekday):
    """The coming day with this weekday (today counts)."""
    return today + timedelta(days=(weekday - today.weekday()) % 7)


def make_date(day, month, year, today):
    """A day without a year: this year, or next year once it has passed."""
    try:
        result = date(year if year else today.year, month, day)
    except ValueError:
        return None
    if not year and result < today:
        result = result.replace(year=today.year + 1)
    return result


def parse(text, today, default_day=None):
    rest = " " + text.strip() + " "
    lower = rest.lower()
    first = last = None
    start_time = end_time = None

    def cut(match):
        nonlocal rest, lower
        rest = rest[:match.start()] + " " + rest[match.end():]
        lower = rest.lower()

    # Weekday range "Mo–Fr": all-day over those days.
    match = re.search(r"(?<![\w.])" + WEEKDAY + DASH + WEEKDAY + r"(?![\w])", lower)
    if match:
        first = next_weekday(today, WEEKDAYS[match.group(1)])
        last = first + timedelta(days=(WEEKDAYS[match.group(2)] - WEEKDAYS[match.group(1)]) % 7)
        cut(match)
    # Dates: "6. Feb", "6. Februar 2027", "14.10.", "14.10.2026".
    if first is None:
        match = re.search(r"(?<![\w.])(\d{1,2})\.\s*" + MONTH + r"(?:\s+(\d{4}))?(?![\w])", lower)
        if match:
            first = make_date(int(match.group(1)), MONTHS[match.group(2)], int(match.group(3)) if match.group(3) else None, today)
            cut(match)
    if first is None:
        match = re.search(r"(?<![\w.:])(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})?(?![\w:])", lower)
        if match:
            year = int(match.group(3)) if match.group(3) else None
            first = make_date(int(match.group(1)), int(match.group(2)), (year + 2000 if year and year < 100 else year), today)
            cut(match)
    # heute / morgen / übermorgen / a weekday ("am Samstag").
    if first is None:
        for word, offset in (("übermorgen", 2), ("morgen", 1), ("heute", 0)):
            match = re.search(r"(?<![\w])" + word + r"(?![\w])", lower)
            if match:
                first = today + timedelta(days=offset)
                cut(match)
                break
    if first is None:
        match = re.search(r"(?<![\w])(?:am\s+)?" + WEEKDAY + r"(?![\w])", lower)
        if match and len(match.group(1)) > 2 or (match and re.search(r"\bam\s", match.group(0))):
            first = next_weekday(today, WEEKDAYS[match.group(1)])
            cut(match)
    # Times: "von 11–13 Uhr", "10:00-11:30", "um 19 Uhr", "19:30", "bis 13 Uhr".
    match = re.search(r"(?<![\w.:])(?:von\s+)?" + TIME + DASH + TIME + r"(?:\s*uhr)?(?![\w])", lower)
    if match and _hour(match.group(1)) is not None and _hour(match.group(3)) is not None:
        start_time = (int(match.group(1)), int(match.group(2) or 0))
        end_time = (int(match.group(3)), int(match.group(4) or 0))
        cut(match)
    else:
        match = re.search(r"(?<![\w.:])(?:um\s+|ab\s+)?" + TIME + r"\s*uhr(?![\w])|(?<![\w.:])(?:um\s+|ab\s+)?(\d{1,2}):(\d{2})(?![\w:])", lower)
        if match:
            hour = match.group(1) or match.group(3)
            minute = match.group(2) or match.group(4)
            if _hour(hour) is not None:
                start_time = (int(hour), int(minute or 0))
                cut(match)
        match = re.search(r"(?<![\w])bis\s+" + TIME + r"(?:\s*uhr)?(?![\w])", lower)
        if match and start_time and _hour(match.group(1)) is not None:
            end_time = (int(match.group(1)), int(match.group(2) or 0))
            cut(match)
    # "Abend"/meals: a time of day when none was given ("heute Abend um 19 Uhr" keeps 19).
    for word, hour in MEALS.items():
        if re.search(r"(?<![\w])" + word + r"(?![\w])", lower) and start_time is None:
            start_time = (hour, 0)
            break
    match = re.search(r"(?<![\w])(abend|morgen früh|nachmittag)(?![\w])", lower)
    if match:
        if start_time is None:
            start_time = {"abend": (20, 0), "morgen früh": (9, 0), "nachmittag": (15, 0)}[match.group(1)]
        elif match.group(1) in ("abend", "nachmittag") and start_time[0] < 12:
            start_time = (start_time[0] + 12, start_time[1])
        cut(match)

    # "Vertretung Jens" / "vertreten durch Jens": a deputy (the rest of the text is the name).
    deputy = None
    match = re.search(r"(?<![\w])(?:vertretung|vertreten durch)\s*:?\s+(.+)$", lower)
    if match:
        deputy = rest[match.start(1):match.end(1)].strip(" ,")
        cut(match)
    title = re.sub(r"\s+", " ", rest).strip()
    title = re.sub(r"^(am|um|an|von|ab)\s+|\s+(am|um|an|von|ab|bis)$", "", title, flags=re.I).strip(" ,–-") or "Neuer Termin"
    day = first or default_day or today
    # Absences: "Urlaub", "krank", "Homeoffice" … at the start make the event an absence (all-day).
    absence = next((key for word, key in ABSENCE_WORDS if re.match(r"^" + word + r"(?![\w])", title.lower())), None)
    if start_time is None or absence:
        last = last or day
        result = {"title": title, "allDay": True, "start": day.isoformat(), "end": (last + timedelta(days=1)).isoformat()}
        if absence:
            result["absence"] = absence
        if deputy:
            result["deputy"] = deputy
        return result
    start = datetime(day.year, day.month, day.day, *start_time)
    if end_time is None:
        end = start + timedelta(hours=1)
    else:
        end = datetime(day.year, day.month, day.day, *end_time)
        if end <= start:
            end += timedelta(days=1)  # "22–1 Uhr": past midnight
    result = {"title": title, "allDay": False, "start": start.strftime("%Y-%m-%dT%H:%M"), "end": end.strftime("%Y-%m-%dT%H:%M")}
    if deputy:
        result["deputy"] = deputy
    return result


def _hour(text):
    return int(text) if text is not None and 0 <= int(text) <= 23 else None
