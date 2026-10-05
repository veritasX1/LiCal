"""Editing rules shared by Ubuntu and Android – the twin of Editing.kt (cases in shared/cases/editing.json,
written by tests/test_editing.py). They only change event dicts; the screens call them."""

from datetime import date, datetime, timedelta

from .rules import as_datetime, parse, stamp
from .i18n import _

REPEATS = (
    (None, _("Nie")),
    ("FREQ=DAILY", _("Täglich")),
    ("FREQ=WEEKLY", _("Wöchentlich")),
    ("FREQ=WEEKLY;INTERVAL=2", _("Alle 2 Wochen")),
    ("FREQ=MONTHLY", _("Monatlich")),
    ("FREQ=YEARLY", _("Jährlich")),
)


# Absences (card 471febc2): key, label, and whether the person is away (not reachable as usual).
ABSENCES = (
    ("urlaub", "Urlaub", True),
    ("krank", "Krank", True),
    ("weiterbildung", "Weiterbildung", True),
    ("freistellung", "Freistellung", True),
    ("abwesend", _("Abwesend"), True),
    ("dienstreise", "Dienstreise", True),
    ("anderer_ort", _("An einem anderen Ort tätig"), False),
    ("homeoffice", _("Im Homeoffice"), False),
    ("office", _("Im Office"), False),
    ("anwesend", "Anwesend", False),
)
ABSENCE_LABELS = {key: label for key, label, _away in ABSENCES}


def set_absence(event, kind):
    """Make an event an absence of this kind (None: an ordinary event again). An absence is all-day;
    an empty or generic title becomes the kind's name."""
    result = dict(event)
    if kind is None:
        result.pop("absence", None)
        result.pop("deputy", None)
        return result
    result = set_all_day(result, True)
    result["absence"] = kind
    if result.get("title", "") in ("", _("Neuer Termin")) or result.get("title") in ABSENCE_LABELS.values():
        result["title"] = ABSENCE_LABELS.get(kind, kind)
    return result


def set_deputy(event, name):
    """Who stands in (a name, later a team member); empty: nobody."""
    result = dict(event)
    if name and name.strip():
        result["deputy"] = name.strip()
    else:
        result.pop("deputy", None)
    return result


def absence_weeks(event, weeks):
    """The quick choices "1–4 Wochen": all-day from the start, so many weeks long."""
    result = set_all_day(event, True)
    first = parse(result["start"])
    result["end"] = stamp(first + timedelta(days=7 * weeks))
    return result


def absence_text(event):
    """"Urlaub · Vertretung: Jens" for views and lists ("" for an ordinary event)."""
    if not event.get("absence"):
        return ""
    text = ABSENCE_LABELS.get(event["absence"], event["absence"])
    if event.get("deputy"):
        text += _(" · Vertretung: {value}", value=event['deputy'])
    return text


def new_event(day, now, calendar, event_id, hour=None):
    """A new event like Apple's: on today the next full hour, on another day 9:00 (or the hour that was
    double-clicked), one hour long, titled "Neuer Termin"."""
    if hour is None:
        hour = min(23, now.hour + 1) if day == now.date() else 9
    start = datetime(day.year, day.month, day.day, hour)
    return {"id": event_id, "calendar": calendar, "title": _("Neuer Termin"), "allDay": False,
            "start": stamp(start), "end": stamp(start + timedelta(hours=1))}


def duration(event):
    return as_datetime(parse(event["end"])) - as_datetime(parse(event["start"]))


def set_start(event, start):
    """Move the start; the length stays (like Apple)."""
    length = duration(event)
    result = dict(event)
    if event.get("allDay"):
        day = start.date() if isinstance(start, datetime) else start
        result["start"] = stamp(day)
        result["end"] = stamp(day + max(timedelta(days=1), length))
    else:
        moment = start if isinstance(start, datetime) else datetime(start.year, start.month, start.day,
                                                                    parse(event["start"]).hour, parse(event["start"]).minute)
        result["start"] = stamp(moment)
        result["end"] = stamp(moment + length)
    return result


def set_end(event, end):
    """Change the end; never before the start (all-day: at least the start day; timed: at least 5 min).
    All-day `end` here is the last day shown, stored as the day after (iCalendar)."""
    result = dict(event)
    start = parse(event["start"])
    if event.get("allDay"):
        last = end.date() if isinstance(end, datetime) else end
        result["end"] = stamp(max(last, start) + timedelta(days=1))
    else:
        moment = end if isinstance(end, datetime) else datetime(end.year, end.month, end.day, parse(event["end"]).hour, parse(event["end"]).minute)
        result["end"] = stamp(max(moment, start + timedelta(minutes=5)))
    return result


def last_day(event):
    """The last day an all-day event covers (what the editor shows as "Ende")."""
    end = parse(event["end"])
    return (end - timedelta(days=1)) if event.get("allDay") else end.date()


def set_all_day(event, on):
    """Switch between all-day and timed: all-day keeps the days; timed gets 9:00–10:00 on the first
    day (Apple keeps the last times – we have none for an all-day event). Alerts start over."""
    if bool(event.get("allDay")) == on:
        return dict(event)
    result = dict(event, allDay=on)
    result.pop("alerts", None)  # the choices differ (Apple starts over too)
    start = parse(event["start"])
    if on:
        first = start.date()
        last = parse(event["end"]).date() if parse(event["end"]).time() != datetime.min.time() or parse(event["end"]).date() == first \
            else parse(event["end"]).date() - timedelta(days=1)
        result["start"] = stamp(first)
        result["end"] = stamp(max(first, last) + timedelta(days=1))
    else:
        result["start"] = stamp(datetime(start.year, start.month, start.day, 9))
        result["end"] = stamp(datetime(start.year, start.month, start.day, 10))
    return result


def repeat_label(rrule):
    for rule, label in REPEATS:
        if (rule or None) == (rrule or None):
            return label
    return "Benutzerdefiniert"


def apply_to_series(series, occurrence_start, edited):
    """An occurrence was edited (`edited` holds its new start/end and the other fields): the whole
    series takes the fields and moves by the same amount of time."""
    result = dict(edited)
    shift = as_datetime(parse(edited["start"])) - as_datetime(parse(occurrence_start))
    length = duration(edited)
    base = as_datetime(parse(series["start"])) + shift
    if edited.get("allDay"):
        result["start"] = stamp(base.date())
        result["end"] = stamp(base.date() + max(timedelta(days=1), length))
    else:
        result["start"] = stamp(base)
        result["end"] = stamp(base + length)
    for key in ("id", "rrule", "exdates"):
        if key in series:
            result[key] = series[key]
        else:
            result.pop(key, None)
    return result


def skip_occurrence(series, occurrence_start):
    """"Nur diesen Termin löschen": the day goes into the series' exceptions."""
    result = dict(series)
    day = occurrence_start[:10]
    result["exdates"] = sorted(set(series.get("exdates") or []) | {day})
    return result
