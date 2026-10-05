"""Card 91adf47e: alerts before events – Apple's choices, labels, when they ring (repeating, all-day,
skipped days). Cases for Android's AlertsTest in shared/cases/alerts.json
(python3 tests/test_alerts.py --update after a deliberate change)."""

import json
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import alerts, editing  # noqa: E402

CASES_FILE = Path(__file__).resolve().parents[2] / "shared" / "cases" / "alerts.json"

EVENTS = [
    {"id": "z", "calendar": "privat", "title": "Zahnarzt", "allDay": False, "start": "2026-10-07T08:30", "end": "2026-10-07T09:15",
     "location": "Dr. Weber", "alerts": [15, 1440]},
    {"id": "j", "calendar": "arbeit", "title": "Jour fixe", "allDay": False, "start": "2026-10-05T09:00", "end": "2026-10-05T10:00",
     "rrule": "FREQ=WEEKLY;BYDAY=MO", "exdates": ["2026-10-12"], "alerts": [0]},
    {"id": "g", "calendar": "familie", "title": "Geburtstag Mia", "allDay": True, "start": "2026-10-08", "end": "2026-10-09", "alerts": [-540, 900]},
    {"id": "o", "calendar": "privat", "title": "Ohne Hinweis", "allDay": False, "start": "2026-10-07T10:00", "end": "2026-10-07T11:00"},
]
WINDOWS = [("2026-10-04T00:00", "2026-10-20T00:00"), ("2026-10-06T08:30", "2026-10-07T08:15"), ("2026-10-07T08:15", "2026-10-07T08:16"),
           ("2026-10-19T09:00", "2026-10-26T09:00")]
LABELS = [(15, False), (0, False), (1440, False), (45, False), (180, False), (4320, False), (-10, False), (None, False),
          (-540, True), (900, True), (9540, True), (-480, True), (1020, True), (2880, True)]


def build():
    cases = [{"kind": "label", "minutes": m, "allDay": a, "result": alerts.label(m, a)} for m, a in LABELS]
    for after, until in WINDOWS:
        cases.append({"kind": "due", "after": after, "until": until, "events": EVENTS,
                      "result": alerts.due(EVENTS, datetime.fromisoformat(after), datetime.fromisoformat(until))})
    for given in ([15], [15, 15, None], [None, 60], [5, 10, 30], []):
        cases.append({"kind": "set", "event": EVENTS[3], "alerts": given, "result": alerts.set_alerts(EVENTS[3], given)})
    nxt = alerts.next_time(EVENTS, datetime.fromisoformat("2026-10-07T08:15"))
    cases.append({"kind": "next", "after": "2026-10-07T08:15", "events": EVENTS, "result": nxt.strftime("%Y-%m-%dT%H:%M")})
    for alert, now in ((0, "2026-10-07T08:15"), (2, "2026-10-07T21:00"), (0, "2026-10-03T12:00")):
        found = alerts.due(EVENTS, datetime(2026, 10, 1), datetime(2026, 10, 9))[alert]
        cases.append({"kind": "text", "alert": found, "now": now, "result": alerts.text(found, datetime.fromisoformat(now))})
    return cases


def main():
    assert alerts.label(15, False) == "15 Minuten vorher" and alerts.label(-540, True) == "Am Tag des Ereignisses (9:00)"
    assert alerts.label(1020, True) == "1 Tag vorher (7:00)" and alerts.label(-480, True) == "Am Tag des Ereignisses (8:00)"
    assert alerts.label(180, False) == "3 Stunden vorher" and alerts.label(None, False) == "Keiner"
    found = alerts.due(EVENTS, datetime(2026, 10, 4), datetime(2026, 10, 20))
    at = [(a["title"], a["at"]) for a in found]
    assert ("Jour fixe", "2026-10-05T09:00") in at and ("Jour fixe", "2026-10-12T09:00") not in at, at  # skipped day
    assert ("Jour fixe", "2026-10-19T09:00") in at
    assert ("Zahnarzt", "2026-10-06T08:30") in at and ("Zahnarzt", "2026-10-07T08:15") in at
    assert ("Geburtstag Mia", "2026-10-08T09:00") in at and ("Geburtstag Mia", "2026-10-07T09:00") in at
    assert not any(a["title"] == "Ohne Hinweis" for a in found)
    assert [a["at"] for a in found] == sorted(a["at"] for a in found)
    # An alert rings exactly once: the window (after, until] excludes its start.
    assert [a["title"] for a in alerts.due(EVENTS, datetime(2026, 10, 7, 8, 15), datetime(2026, 10, 7, 8, 16))] == []
    assert alerts.set_alerts(EVENTS[3], [15, 15, 30, 60])["alerts"] == [15, 30]
    assert "alerts" not in alerts.set_alerts(EVENTS[0], [None])
    assert "alerts" not in editing.set_all_day(EVENTS[0], True)
    assert alerts.text(found[0], datetime(2026, 10, 5, 8, 0)).startswith("Heute, 09:00–10:00")
    cases = build()
    if "--update" in sys.argv or not CASES_FILE.exists():
        CASES_FILE.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    assert json.loads(CASES_FILE.read_text()) == json.loads(json.dumps(cases)), "shared/cases/alerts.json veraltet (--update)"
    print(f"ok – Hinweise vor Terminen ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
