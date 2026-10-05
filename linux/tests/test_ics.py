"""Card b483dadf: events as iCalendar text for QR codes – both ways, umlauts and special characters,
all-day, repeating, foreign iCalendar (folded lines, UTC, no DTEND). Cases for Android's IcsTest in
shared/cases/ics.json (python3 tests/test_ics.py --update after a deliberate change)."""

import json
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ["TZ"] = "Europe/Berlin"  # UTC times become local time – the cases are for Berlin (Android's test sets the same)
time.tzset()
from lical import ics  # noqa: E402

CASES_FILE = Path(__file__).resolve().parents[2] / "shared" / "cases" / "ics.json"

EVENTS = [
    {"title": "Zahnarzt", "allDay": False, "start": "2026-10-07T08:30", "end": "2026-10-07T09:15", "location": "Dr. Weber, Köln"},
    {"title": "Urlaub; Kreta, Süden", "allDay": True, "start": "2026-10-12", "end": "2026-10-17", "notes": "Zeile 1\nZeile 2 \\ Ende"},
    {"title": "Jour fixe", "allDay": False, "start": "2026-10-05T09:00", "end": "2026-10-05T10:00", "rrule": "FREQ=WEEKLY;BYDAY=MO"},
    {"title": "Call New York", "allDay": False, "start": "2026-10-19T09:00", "end": "2026-10-19T10:00", "tz": "America/New_York"},
]
FOREIGN = [
    "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20261009T180000Z\r\nDTEND:20261009T193000Z\r\nSUMMARY:Konzert im\r\n  Park\r\nEND:VEVENT\r\nEND:VCALENDAR",
    "BEGIN:VEVENT\nSUMMARY:Feiertag\nDTSTART;VALUE=DATE:20261003\nEND:VEVENT",
    "BEGIN:VEVENT\nDTSTART;TZID=Europe/Berlin:20261010T110000\nSUMMARY:Ohne Ende\nLOCATION:\nEND:VEVENT",
    "kein Kalender",
    "BEGIN:VEVENT\nDTSTART;TZID=W. Europe Standard Time:20261010T110000\nDTEND;TZID=W. Europe Standard Time:20261010T120000\nSUMMARY:Outlook\nEND:VEVENT",
]


def build():
    cases = [{"kind": "to", "event": event, "result": ics.to_ics(event)} for event in EVENTS]
    cases += [{"kind": "from", "text": text, "result": ics.from_ics(text)} for text in FOREIGN]
    return cases


def main():
    for event in EVENTS:
        back = ics.from_ics(ics.to_ics(event))
        assert back == event, (back, event)
    assert ics.from_ics(FOREIGN[0]) == {"title": "Konzert im Park", "allDay": False, "start": "2026-10-09T20:00", "end": "2026-10-09T21:30"}
    assert ics.from_ics(FOREIGN[1]) == {"title": "Feiertag", "allDay": True, "start": "2026-10-03", "end": "2026-10-04"}
    assert ics.from_ics(FOREIGN[2])["end"] == "2026-10-10T12:00" and ics.from_ics(FOREIGN[2])["tz"] == "Europe/Berlin"
    assert "tz" not in ics.from_ics(FOREIGN[4])  # Outlook's Windows zone names: floating
    assert "DTSTART;TZID=America/New_York:20261019T090000" in ics.to_ics(EVENTS[3])
    assert ics.from_ics(FOREIGN[3]) is None
    assert "SUMMARY:Urlaub\\; Kreta\\, Süden" in ics.to_ics(EVENTS[1])
    cases = build()
    if "--update" in sys.argv or not CASES_FILE.exists():
        CASES_FILE.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    assert json.loads(CASES_FILE.read_text()) == json.loads(json.dumps(cases)), "shared/cases/ics.json veraltet (--update)"
    print(f"ok – Termine als iCalendar ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
