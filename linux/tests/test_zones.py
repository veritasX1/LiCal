"""Card 7a9187d6: events in another time zone – repeated in their zone (summer time there), shown in
the local one, edited in their own. Cases for Android's ZonesTest in shared/cases/zones.json
(python3 tests/test_zones.py --update after a deliberate change). Local zone of the cases: Berlin."""

import json
import sys
from datetime import date, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import rules  # noqa: E402

CASES_FILE = Path(__file__).resolve().parents[2] / "shared" / "cases" / "zones.json"
BERLIN = "Europe/Berlin"
NEW_YORK = {"id": "ny", "calendar": "arbeit", "title": "Call New York", "allDay": False, "start": "2026-10-19T09:00",
            "end": "2026-10-19T10:00", "rrule": "FREQ=WEEKLY", "tz": "America/New_York"}
TOKYO = {"id": "tk", "calendar": "arbeit", "title": "Tokio früh", "allDay": False, "start": "2026-10-08T06:30",
         "end": "2026-10-08T07:30", "tz": "Asia/Tokyo"}
FLOATING = {"id": "fl", "calendar": "privat", "title": "Ortszeit", "allDay": False, "start": "2026-10-08T06:30", "end": "2026-10-08T07:30"}
ALLDAY = {"id": "ad", "calendar": "privat", "title": "Ganztägig", "allDay": True, "start": "2026-10-08", "end": "2026-10-09", "tz": "Asia/Tokyo"}
WINDOWS = [("2026-10-19", "2026-11-10"), ("2026-10-07", "2026-10-09"), ("2026-10-08", "2026-10-09")]


def shown(items):
    return [[item["key"], item["start"], item["end"], item.get("zoneStart"), item.get("zoneEnd")] for item in items]


def build():
    cases = []
    for first, last in WINDOWS:
        found = rules.occurrences([NEW_YORK, TOKYO, FLOATING, ALLDAY], date.fromisoformat(first), date.fromisoformat(last), BERLIN)
        cases.append({"kind": "occurrences", "from": first, "to": last, "events": [NEW_YORK, TOKYO, FLOATING, ALLDAY], "result": shown(found)})
    for moment in ("2026-10-28T14:00", "2026-11-03T15:30", "2026-03-29T02:30"):
        cases.append({"kind": "to_zone", "event": NEW_YORK, "moment": moment,
                      "result": rules.stamp(rules.to_event_zone(NEW_YORK, datetime.fromisoformat(moment), BERLIN))})
    item = rules.occurrences([TOKYO], date(2026, 10, 7), date(2026, 10, 8), BERLIN)[0]
    cases.append({"kind": "for_editing", "item": item, "result": rules.for_editing(item)})
    return cases


def main():
    found = rules.occurrences([NEW_YORK], date(2026, 10, 19), date(2026, 11, 10), BERLIN)
    starts = [item["start"] for item in found]
    # 09:00 in New York: 15:00 in Berlin – but 14:00 in the week between the two summer-time ends.
    assert starts == ["2026-10-19T15:00", "2026-10-26T14:00", "2026-11-02T15:00", "2026-11-09T15:00"], starts
    assert found[1]["zoneStart"] == "2026-10-26T09:00" and found[1]["key"] == "ny@2026-10-26T09:00"
    # 06:30 in Tokyo is 23:30 the evening before in Berlin.
    tokyo = rules.occurrences([TOKYO], date(2026, 10, 7), date(2026, 10, 8), BERLIN)
    assert [(i["start"], i["end"]) for i in tokyo] == [("2026-10-07T23:30", "2026-10-08T00:30")], tokyo
    assert rules.occurrences([TOKYO], date(2026, 10, 8), date(2026, 10, 9), BERLIN)[0]["start"] == "2026-10-07T23:30"  # still overlaps
    assert rules.occurrences([TOKYO], date(2026, 10, 9), date(2026, 10, 10), BERLIN) == []
    # Floating and all-day events are not converted; in their own zone nothing changes.
    assert rules.occurrences([FLOATING, ALLDAY], date(2026, 10, 8), date(2026, 10, 9), BERLIN)[1]["start"] == "2026-10-08T06:30"
    assert "zoneStart" not in rules.occurrences([NEW_YORK], date(2026, 10, 19), date(2026, 10, 20), "America/New_York")[0]
    # Editing goes back to the event's zone; a local moment becomes the zone's.
    edit = rules.for_editing(tokyo[0])
    assert (edit["start"], edit["end"]) == ("2026-10-08T06:30", "2026-10-08T07:30") and "zoneStart" not in edit
    assert rules.to_event_zone(NEW_YORK, datetime(2026, 10, 28, 14, 0), BERLIN) == datetime(2026, 10, 28, 9, 0)
    assert rules.to_event_zone(FLOATING, datetime(2026, 10, 28, 14, 0), BERLIN) == datetime(2026, 10, 28, 14, 0)
    cases = build()
    if "--update" in sys.argv or not CASES_FILE.exists():
        CASES_FILE.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    assert json.loads(CASES_FILE.read_text()) == json.loads(json.dumps(cases)), "shared/cases/zones.json veraltet (--update)"
    print(f"ok – Zeitzonen ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
