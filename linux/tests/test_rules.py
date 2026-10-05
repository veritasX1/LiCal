"""Calendar rules (card 93f6a101): checks with known answers, then all cases written to
shared/cases/rules.json, which Android's RulesTest replays against Rules.kt. After a deliberate
change of rules.py:  python3 tests/test_rules.py --update  (then mirror it in Rules.kt)."""

import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import rules  # noqa: E402

CASES = Path(__file__).resolve().parents[2] / "shared" / "cases" / "rules.json"


def ev(id, start, end, title=None, all_day=False, **extra):
    return {"id": id, "calendar": "c", "title": title or id, "allDay": all_day, "start": start, "end": end, **extra}


EVENTS = [
    ev("urlaub", "2026-10-07", "2026-10-13", "Urlaub Kreta", True),
    ev("feiertag", "2026-10-03", "2026-10-04", "Tag der Deutschen Einheit", True),
    ev("jourfixe", "2026-10-05T09:00", "2026-10-05T10:00", "Jour fixe", rrule="FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5", exdates=["2026-10-07"]),
    ev("zahnarzt", "2026-10-07T08:30", "2026-10-07T09:15", "Zahnarzt"),
    ev("review", "2026-10-07T09:00", "2026-10-07T11:00", "Review"),
    ev("call", "2026-10-07T10:30", "2026-10-07T11:00", "Call"),
    ev("mittag", "2026-10-07T12:00", "2026-10-07T13:00", "Mittag"),
    ev("nacht", "2026-10-09T22:00", "2026-10-10T02:00", "Nachtfahrt"),
    ev("messe", "2026-10-14T09:00", "2026-10-16T17:00", "Messe Köln"),
    ev("miete", "2026-01-31", "2026-02-01", "Miete", True, rrule="FREQ=MONTHLY"),
    ev("geburtstag", "2020-10-08", "2020-10-09", "Mias Geburtstag", True, rrule="FREQ=YEARLY"),
    ev("sport", "2026-10-01T18:00", "2026-10-01T19:00", "Sport", rrule="FREQ=DAILY;INTERVAL=3;UNTIL=20261015"),
    ev("kurz", "2026-10-07T15:00", "2026-10-07T15:05", "Kurz"),
    ev("mitternacht", "2026-10-07T23:00", "2026-10-08T00:00", "Bis Mitternacht"),
]


def build():
    cases = []
    for year, month, first in ((2026, 10, 0), (2026, 2, 0), (2026, 3, 0), (2026, 2, 6), (2027, 1, 6)):
        cases.append({"kind": "month_weeks", "year": year, "month": month, "firstWeekday": first,
                      "result": [[rules.stamp(day) for day in week] for week in rules.month_weeks(year, month, first)]})
    for start, end in (("2026-10-01", "2026-11-01"), ("2026-10-05", "2026-10-12"), ("2026-01-01", "2027-01-01")):
        found = rules.occurrences(EVENTS, rules.parse(start), rules.parse(end))
        cases.append({"kind": "occurrences", "from": start, "to": end, "events": EVENTS,
                      "result": [[item["key"], item["start"], item["end"]] for item in found]})
    for monday in ("2026-10-05", "2026-10-12", "2026-09-28"):
        week = [rules.parse(monday) + rules.timedelta(days=offset) for offset in range(7)]
        items = rules.occurrences(EVENTS, week[0], week[-1] + rules.timedelta(days=1))
        layout = rules.week_layout(items, week)
        rows = {str(capacity): [list(rules.cell_rows(layout, column, capacity)) for column in range(7)] for capacity in (1, 2, 3, 6)}
        cases.append({"kind": "week_layout", "week": monday, "events": EVENTS, "result": layout, "rows": rows})
    for day in ("2026-10-07", "2026-10-09", "2026-10-10", "2026-10-15", "2026-10-08"):
        moment = rules.parse(day)
        items = rules.occurrences(EVENTS, moment, moment + rules.timedelta(days=1))
        cases.append({"kind": "timeline", "day": day, "events": EVENTS, "result": rules.timeline_layout(items, moment)})
    return cases


def check_known():
    october = rules.month_weeks(2026, 10)
    assert len(october) == 5 and rules.stamp(october[0][0]) == "2026-09-28" and rules.stamp(october[-1][-1]) == "2026-11-01"
    assert len(rules.month_weeks(2026, 3)) == 6  # 1 March 2026 is a Sunday
    assert rules.stamp(rules.month_weeks(2026, 2, 6)[0][0]) == "2026-02-01"  # weeks from Sunday
    found = rules.occurrences(EVENTS, date(2026, 10, 1), date(2026, 11, 1))
    starts = {item["key"] for item in found}
    assert "jourfixe@2026-10-07T09:00" not in starts and "jourfixe@2026-10-19T09:00" in starts  # exdate, COUNT=5
    assert "jourfixe@2026-10-21T09:00" not in starts
    assert "geburtstag@2026-10-08" in starts and "miete@2026-10-31" in starts
    assert [k for k in starts if k.startswith("sport@")] and max(k for k in starts if k.startswith("sport@")) == "sport@2026-10-13T18:00"
    year = rules.occurrences(EVENTS, date(2026, 1, 1), date(2027, 1, 1))
    assert [item["start"] for item in year if item["id"] == "miete"] == ["2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31",
                                                                         "2026-08-31", "2026-10-31", "2026-12-31"]
    assert found[0]["start"] == "2026-10-01T18:00"
    # Days covered: all-day end is exclusive, a timed event ending at midnight stays on its day.
    assert rules.days_covered({"allDay": True, "start": "2026-10-07", "end": "2026-10-13"}) == (date(2026, 10, 7), date(2026, 10, 12))
    assert rules.days_covered({"start": "2026-10-07T23:00", "end": "2026-10-08T00:00"}) == (date(2026, 10, 7), date(2026, 10, 7))
    assert rules.days_covered({"start": "2026-10-09T22:00", "end": "2026-10-10T02:00"}) == (date(2026, 10, 9), date(2026, 10, 10))
    # Week row: the holiday bar spans Wed–Sun in lane 0, the night drive Fri–Sat in lane 1.
    week = [date(2026, 10, 5) + rules.timedelta(days=offset) for offset in range(7)]
    layout = rules.week_layout(rules.occurrences(EVENTS, week[0], week[-1] + rules.timedelta(days=1)), week)
    bars = {bar["key"]: bar for bar in layout["bars"]}
    assert (bars["urlaub@2026-10-07"]["first"], bars["urlaub@2026-10-07"]["last"], bars["urlaub@2026-10-07"]["lane"]) == (2, 6, 0)
    assert bars["nacht@2026-10-09T22:00"]["lane"] == 1 and bars["geburtstag@2026-10-08"]["lane"] == 1
    assert layout["lines"]["2"][:2] == ["zahnarzt@2026-10-07T08:30", "review@2026-10-07T09:00"]
    lanes, lines, hidden = rules.cell_rows(layout, 2, 3)
    assert lanes == 1 and len(lines) == 1 and hidden == 6, (lanes, lines, hidden)  # 7 events, one row for "+6"
    # Timeline: dentist and review overlap, the call overlaps the review → 2 columns; lunch alone.
    blocks = {block["key"]: block for block in rules.timeline_layout(rules.occurrences(EVENTS, date(2026, 10, 7), date(2026, 10, 8)), date(2026, 10, 7))}
    assert blocks["zahnarzt@2026-10-07T08:30"]["columns"] == 2 and blocks["review@2026-10-07T09:00"]["column"] == 1
    assert blocks["call@2026-10-07T10:30"]["column"] == 0 and blocks["mittag@2026-10-07T12:00"]["columns"] == 1
    assert blocks["kurz@2026-10-07T15:00"]["bottom"] - blocks["kurz@2026-10-07T15:00"]["top"] == 15
    night = rules.timeline_layout(rules.occurrences(EVENTS, date(2026, 10, 10), date(2026, 10, 11)), date(2026, 10, 10))
    assert [(block["top"], block["bottom"]) for block in night if block["key"].startswith("nacht")] == [(0, 120)]


def main():
    check_known()
    cases = build()
    if "--update" in sys.argv or not CASES.exists():
        CASES.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    assert json.loads(CASES.read_text()) == json.loads(json.dumps(cases)), "shared/cases/rules.json veraltet (--update, Kotlin nachziehen)"
    print(f"ok – Kalenderregeln ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
