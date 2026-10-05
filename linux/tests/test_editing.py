"""Editing rules (card 502ccfb2): known answers, then the cases for Android's EditingTest in
shared/cases/editing.json.  python3 tests/test_editing.py --update  after a deliberate change."""

import json
import sys
from datetime import date, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import editing  # noqa: E402

CASES = Path(__file__).resolve().parents[2] / "shared" / "cases" / "editing.json"

TIMED = {"id": "t", "calendar": "arbeit", "title": "Review", "allDay": False, "start": "2026-10-07T10:00", "end": "2026-10-07T11:30"}
ALLDAY = {"id": "u", "calendar": "privat", "title": "Urlaub", "allDay": True, "start": "2026-10-12", "end": "2026-10-17"}
SERIES = {"id": "s", "calendar": "arbeit", "title": "Jour fixe", "allDay": False, "start": "2026-10-05T09:00", "end": "2026-10-05T10:00",
          "rrule": "FREQ=WEEKLY;BYDAY=MO", "exdates": ["2026-10-19"]}


def build():
    cases = []
    for day, now, hour in (("2026-10-04", "2026-10-04T15:07", None), ("2026-10-08", "2026-10-04T15:07", None),
                           ("2026-10-04", "2026-10-04T23:30", None), ("2026-10-09", "2026-10-04T08:00", 14)):
        cases.append({"kind": "new", "day": day, "now": now, "hour": hour,
                      "result": editing.new_event(date.fromisoformat(day), datetime.fromisoformat(now), "privat", "n", hour)})
    for event, value in ((TIMED, "2026-10-09T14:15"), (TIMED, "2026-10-09"), (ALLDAY, "2026-10-20"), (ALLDAY, "2026-10-20T08:00")):
        moment = datetime.fromisoformat(value) if "T" in value else date.fromisoformat(value)
        cases.append({"kind": "start", "event": event, "value": value, "result": editing.set_start(event, moment)})
    for event, value in ((TIMED, "2026-10-07T12:00"), (TIMED, "2026-10-07T09:00"), (TIMED, "2026-10-08"), (ALLDAY, "2026-10-14"), (ALLDAY, "2026-10-01")):
        moment = datetime.fromisoformat(value) if "T" in value else date.fromisoformat(value)
        cases.append({"kind": "end", "event": event, "value": value, "result": editing.set_end(event, moment)})
    for event, on in ((TIMED, True), (ALLDAY, False), (TIMED, False), ({**TIMED, "end": "2026-10-09T00:00"}, True)):
        cases.append({"kind": "allday", "event": event, "on": on, "result": editing.set_all_day(event, on)})
    for rule in (None, "FREQ=WEEKLY", "FREQ=WEEKLY;INTERVAL=2", "FREQ=WEEKLY;BYDAY=MO,WE"):
        cases.append({"kind": "label", "rrule": rule, "result": editing.repeat_label(rule)})
    edited = {**SERIES, "title": "Jour fixe neu", "start": "2026-10-12T09:30", "end": "2026-10-12T10:45"}
    cases.append({"kind": "series", "series": SERIES, "occurrence": "2026-10-12T09:00", "edited": edited,
                  "result": editing.apply_to_series(SERIES, "2026-10-12T09:00", edited)})
    for event, kind in ((TIMED, "urlaub"), ({**TIMED, "title": "Neuer Termin"}, "krank"), (ALLDAY, None),
                        ({**ALLDAY, "absence": "urlaub", "deputy": "Jens"}, None), ({**ALLDAY, "title": "Urlaub", "absence": "urlaub"}, "dienstreise")):
        cases.append({"kind": "absence", "event": event, "absence": kind, "result": editing.set_absence(event, kind)})
    for event, name in ((ALLDAY, "Jens"), (ALLDAY, "  "), ({**ALLDAY, "deputy": "Jens"}, "")):
        cases.append({"kind": "deputy", "event": event, "name": name, "result": editing.set_deputy(event, name)})
    for event, weeks in ((TIMED, 1), (ALLDAY, 2), (ALLDAY, 4)):
        cases.append({"kind": "weeks", "event": event, "weeks": weeks, "result": editing.absence_weeks(event, weeks)})
    for event in ({**ALLDAY, "absence": "urlaub", "deputy": "Jens"}, {**ALLDAY, "absence": "homeoffice"}, ALLDAY):
        cases.append({"kind": "absence_text", "event": event, "result": editing.absence_text(event)})
    cases.append({"kind": "skip", "series": SERIES, "occurrence": "2026-10-12T09:00", "result": editing.skip_occurrence(SERIES, "2026-10-12T09:00")})
    return cases


def check_known():
    now = datetime(2026, 10, 4, 15, 7)
    assert editing.new_event(date(2026, 10, 4), now, "privat", "n")["start"] == "2026-10-04T16:00"
    assert editing.new_event(date(2026, 10, 8), now, "privat", "n")["start"] == "2026-10-08T09:00"
    assert editing.set_start(TIMED, datetime(2026, 10, 9, 14, 15))["end"] == "2026-10-09T15:45"  # length stays
    assert editing.set_end(TIMED, datetime(2026, 10, 7, 9, 0))["end"] == "2026-10-07T10:05"      # never before the start
    assert editing.set_end(ALLDAY, date(2026, 10, 14))["end"] == "2026-10-15"                     # last day 14th → stored 15th
    assert editing.last_day(ALLDAY) == date(2026, 10, 16)
    assert editing.set_all_day(TIMED, True) == {**TIMED, "allDay": True, "start": "2026-10-07", "end": "2026-10-08"}
    assert editing.set_all_day(ALLDAY, False)["start"] == "2026-10-12T09:00"
    assert editing.repeat_label("FREQ=WEEKLY;BYDAY=MO,WE") == "Benutzerdefiniert"
    moved = editing.apply_to_series(SERIES, "2026-10-12T09:00", {**SERIES, "title": "Neu", "start": "2026-10-12T09:30", "end": "2026-10-12T10:45"})
    assert (moved["start"], moved["end"], moved["title"], moved["rrule"]) == ("2026-10-05T09:30", "2026-10-05T10:45", "Neu", "FREQ=WEEKLY;BYDAY=MO")
    assert editing.skip_occurrence(SERIES, "2026-10-12T09:00")["exdates"] == ["2026-10-12", "2026-10-19"]
    # Absences: all-day, the kind's name as title when the title says nothing, a deputy, 1–4 weeks.
    away = editing.set_absence({**TIMED, "title": "Neuer Termin"}, "urlaub")
    assert (away["allDay"], away["title"], away["absence"], away["start"], away["end"]) == (True, "Urlaub", "urlaub", "2026-10-07", "2026-10-08")
    assert editing.set_absence(TIMED, "krank")["title"] == "Review"  # a real title stays
    assert editing.absence_weeks(away, 2)["end"] == "2026-10-21"
    assert editing.absence_text(editing.set_deputy(away, "Jens")) == "Urlaub · Vertretung: Jens"
    assert "absence" not in editing.set_absence(away, None) and "deputy" not in editing.set_absence(editing.set_deputy(away, "J"), None)


def main():
    check_known()
    cases = build()
    if "--update" in sys.argv or not CASES.exists():
        CASES.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    assert json.loads(CASES.read_text()) == json.loads(json.dumps(cases)), "shared/cases/editing.json veraltet (--update, Kotlin nachziehen)"
    print(f"ok – Bearbeitungsregeln ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
