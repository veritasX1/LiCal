"""Card 7a9187d6: German public holidays computed offline – Easter, the nationwide days, the states'
own ones, rules with a start year. Cases for Android's HolidaysTest in shared/cases/holidays.json
(python3 tests/test_holidays.py --update after a deliberate change)."""

import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import holidays  # noqa: E402

CASES_FILE = Path(__file__).resolve().parents[2] / "shared" / "cases" / "holidays.json"
EASTER = {2000: "2000-04-23", 2019: "2019-04-21", 2024: "2024-03-31", 2025: "2025-04-20", 2026: "2026-04-05",
          2027: "2027-03-28", 2028: "2028-04-16", 2030: "2030-04-21", 2038: "2038-04-25", 2049: "2049-04-18"}


def build():
    cases = [{"kind": "easter", "year": year, "result": holidays.easter(year).isoformat()} for year in EASTER]
    for year in (2017, 2018, 2022, 2026, 2027):
        for state, _name in holidays.STATES:
            cases.append({"kind": "days", "year": year, "state": state,
                          "result": [[day.isoformat(), name] for day, name in holidays.days(year, state)]})
    cases.append({"kind": "events", "first": "2026-12-20", "last": "2027-01-07", "state": "BY",
                  "result": holidays.events(date(2026, 12, 20), date(2027, 1, 7), "BY")})
    return cases


def main():
    for year, expected in EASTER.items():
        assert holidays.easter(year).isoformat() == expected, (year, holidays.easter(year))
    names = lambda year, state: {day.isoformat(): name for day, name in holidays.days(year, state)}  # noqa: E731
    # Nordrhein-Westfalen 2026: eleven days.
    assert list(names(2026, "NW")) == ["2026-01-01", "2026-04-03", "2026-04-06", "2026-05-01", "2026-05-14", "2026-05-25",
                                       "2026-06-04", "2026-10-03", "2026-11-01", "2026-12-25", "2026-12-26"], names(2026, "NW")
    assert len(holidays.days(2026)) == 9
    assert names(2026, "SN")["2026-11-18"] == "Buß- und Bettag" and names(2027, "SN")["2027-11-17"] == "Buß- und Bettag"
    assert "2026-03-08" in names(2026, "BE") and "2018-03-08" not in names(2018, "BE")
    assert "2026-03-08" in names(2026, "MV") and "2022-03-08" not in names(2022, "MV")
    assert "2017-10-31" in names(2017, "BY") and "2017-10-31" in names(2017, "")  # 2017: nationwide once
    assert "2018-10-31" in names(2018, "HH") and "2018-10-31" not in names(2018, "BY")
    assert "2026-04-05" in names(2026, "BB") and "2026-05-24" in names(2026, "BB")
    assert names(2026, "TH")["2026-09-20"] == "Weltkindertag"
    assert names(2026, "SL")["2026-08-15"] == "Mariä Himmelfahrt"
    found = holidays.events(date(2026, 12, 20), date(2027, 1, 7), "BY")
    assert [event["title"] for event in found] == ["1. Weihnachtstag", "2. Weihnachtstag", "Neujahr", "Heilige Drei Könige"]
    assert found[0] == {"id": "feiertag:2026-12-25", "calendar": "feiertage", "title": "1. Weihnachtstag", "allDay": True,
                        "start": "2026-12-25", "end": "2026-12-26"}
    cases = build()
    if "--update" in sys.argv or not CASES_FILE.exists():
        CASES_FILE.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    assert json.loads(CASES_FILE.read_text()) == json.loads(json.dumps(cases)), "shared/cases/holidays.json veraltet (--update)"
    print(f"ok – Feiertage ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
