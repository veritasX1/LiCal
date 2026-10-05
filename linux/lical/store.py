"""Calendars and events on this computer: one JSON file in ~/.local/share/lical (written atomically,
private). The format is docs/DATA.md – the same on Android."""

import json
import os
import uuid
from datetime import date, datetime, timedelta
from pathlib import Path

from . import holidays, rules

DEFAULT_CALENDARS = [
    {"id": "privat", "name": "Privat", "color": "blue", "visible": True},
    {"id": "arbeit", "name": "Arbeit", "color": "orange", "visible": True},
    {"id": "familie", "name": "Familie", "color": "green", "visible": True},
]


# The holiday calendar (card 7a9187d6): computed, read-only, kept apart from the own calendars.
# "chosen": the user switched it on or off himself (Android hides it while a phone holiday calendar shows).
DEFAULT_HOLIDAYS = {"name": "Feiertage", "color": "purple", "visible": True, "state": "", "chosen": False}


def data_dir():
    return Path(os.environ.get("XDG_DATA_HOME") or Path.home() / ".local" / "share") / "lical"


class Store:
    def __init__(self, path=None):
        self.path = Path(path) if path else data_dir() / "kalender.json"
        self.calendars = [dict(calendar) for calendar in DEFAULT_CALENDARS]
        self.events = []
        self.holidays = dict(DEFAULT_HOLIDAYS)
        self.listeners = []
        if self.path.exists():
            data = json.loads(self.path.read_text())
            self.calendars = data.get("calendars") or self.calendars
            self.events = data.get("events") or []
            self.holidays.update(data.get("holidays") or {})

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = self.path.with_suffix(".tmp")
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as handle:
            json.dump({"version": 1, "calendars": self.calendars, "holidays": self.holidays, "events": self.events}, handle, ensure_ascii=False, indent=1)
        os.replace(temporary, self.path)
        for listener in list(self.listeners):
            listener()

    def calendar(self, calendar_id):
        if calendar_id == holidays.CALENDAR:
            return {"id": holidays.CALENDAR, **self.holidays}
        return next((calendar for calendar in self.calendars if calendar["id"] == calendar_id), None)

    def is_read_only(self, calendar_id):
        return calendar_id == holidays.CALENDAR

    def set_holidays(self, **fields):
        """Show/hide (then "chosen"), color, Bundesland of the holiday calendar."""
        if "visible" in fields:
            fields["chosen"] = True
        self.holidays.update(fields)
        self.save()

    def visible_events(self):
        shown = {calendar["id"] for calendar in self.calendars if calendar.get("visible", True)}
        return [event for event in self.events if event.get("calendar") in shown]

    def occurrences(self, start, end):
        found = rules.occurrences(self.visible_events(), start, end)
        if self.holidays.get("visible", True):
            days = holidays.events(start, end - timedelta(days=1), self.holidays.get("state", ""))
            found = sorted(found + rules.occurrences(days, start, end), key=rules.sort_key)
        return found

    def set_visible(self, calendar_id, visible):
        if calendar_id == holidays.CALENDAR:
            self.set_holidays(visible=visible)
            return
        calendar = self.calendar(calendar_id)
        if calendar is not None:
            calendar["visible"] = visible
            self.save()

    # ---- calendars (sidebar: new, rename, color, delete) ----

    def add_calendar(self, name, color):
        calendar = {"id": uuid.uuid4().hex, "name": name, "color": color, "visible": True}
        self.calendars.append(calendar)
        self.save()
        return calendar

    def update_calendar(self, calendar_id, **fields):
        calendar = self.calendar(calendar_id)
        if calendar is not None:
            calendar.update(fields)
            self.save()

    def delete_calendar(self, calendar_id):
        """The calendar and its events (the sidebar asks first). The last one stays."""
        if len(self.calendars) <= 1:
            return False
        self.calendars = [calendar for calendar in self.calendars if calendar["id"] != calendar_id]
        self.events = [event for event in self.events if event.get("calendar") != calendar_id]
        self.save()
        return True

    def put(self, event):
        event.setdefault("id", uuid.uuid4().hex)
        self.events = [other for other in self.events if other["id"] != event["id"]] + [event]
        self.save()
        return event

    def delete(self, event_id):
        self.events = [event for event in self.events if event["id"] != event_id]
        self.save()


def demo_events(today=None):
    """Examples around today for screenshots and the first look (tools/demo-ubuntu.py) – never mixed
    into real data."""
    today = today or date.today()
    monday = today - timedelta(days=today.weekday())

    def at(day, hour, minute=0):
        return (datetime(day.year, day.month, day.day, hour, minute)).strftime("%Y-%m-%dT%H:%M")

    def d(offset):
        return monday + timedelta(days=offset)

    items = [
        ("arbeit", "Jour fixe Team", at(d(0), 9), at(d(0), 10), False, "FREQ=WEEKLY;BYDAY=MO", "Besprechungsraum 2"),
        ("arbeit", "Review Release 2.2", at(d(1), 10), at(d(1), 11, 30), False, None, ""),
        ("arbeit", "Kundentermin Müller", at(d(1), 11), at(d(1), 12), False, None, "Köln"),
        ("privat", "Zahnarzt", at(d(2), 8, 30), at(d(2), 9, 15), False, None, "Dr. Weber"),
        ("arbeit", "Mittag mit Jens", at(d(2), 12, 30), at(d(2), 13, 30), False, None, ""),
        ("familie", "Elternabend", at(d(3), 19), at(d(3), 20, 30), False, None, "Grundschule"),
        ("privat", "Laufen", at(d(4), 7), at(d(4), 8), False, "FREQ=WEEKLY;BYDAY=TU,FR", ""),
        ("familie", "Kino mit Mia", at(d(5), 20), at(d(5), 22, 15), False, None, "Cinedom"),
        ("familie", "Mias Geburtstag", d(9).isoformat(), d(10).isoformat(), True, "FREQ=YEARLY", ""),
        ("privat", "Urlaub Kreta", d(16).isoformat(), d(23).isoformat(), True, None, ""),
        ("arbeit", "Messe Köln", at(d(8), 9), at(d(10), 17), False, None, "Koelnmesse"),
        ("arbeit", "Sprint-Planung", at(d(14), 10), at(d(14), 12), False, "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO", ""),
        ("privat", "Steuerberater", at(d(11), 16), at(d(11), 17), False, None, ""),
        ("familie", "Oma besuchen", at(d(6), 14), at(d(6), 17), False, None, ""),
        ("arbeit", "Tag der offenen Tür", d(-3).isoformat(), d(-2).isoformat(), True, None, ""),
        ("privat", "Wochenmarkt", at(d(5), 9), at(d(5), 10), False, "FREQ=WEEKLY;BYDAY=SA", ""),
        ("arbeit", "1:1 mit Sabine", at(d(3), 14), at(d(3), 14, 30), False, "FREQ=WEEKLY;BYDAY=TH", ""),
        ("arbeit", "Abgabe Konzept", d(4).isoformat(), d(5).isoformat(), True, None, ""),
    ]
    events = []
    for index, (calendar, title, start, end, all_day, rrule, place) in enumerate(items):
        event = {"id": f"demo{index}", "calendar": calendar, "title": title, "allDay": all_day, "start": start, "end": end}
        if rrule:
            event["rrule"] = rrule
        if place:
            event["location"] = place
        events.append(event)
    return events
