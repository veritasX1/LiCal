"""Card 9b27d02a: events in plain language like ＋ on the Mac – the handbook's examples and everyday ones."""

import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import quick  # noqa: E402

TODAY = date(2026, 10, 4)  # a Sunday

CASES = [
    # The Mac handbook's examples.
    ("Party 6. Feb", {"title": "Party", "allDay": True, "start": "2027-02-06", "end": "2027-02-07"}),
    ("Fußballspiel Samstag von 11–13 Uhr", {"title": "Fußballspiel", "allDay": False, "start": "2026-10-10T11:00", "end": "2026-10-10T13:00"}),
    ("Urlaub in Paris Mo–Fr", {"title": "Urlaub in Paris", "allDay": True, "start": "2026-10-05", "end": "2026-10-10", "absence": "urlaub"}),
    ("Film heute Abend um 19 Uhr", {"title": "Film", "allDay": False, "start": "2026-10-04T19:00", "end": "2026-10-04T20:00"}),
    ("Frühstück", {"title": "Frühstück", "allDay": False, "start": "2026-10-04T09:00", "end": "2026-10-04T10:00"}),
    ("Mittagessen mit Jens Dienstag", {"title": "Mittagessen mit Jens", "allDay": False, "start": "2026-10-06T12:00", "end": "2026-10-06T13:00"}),
    ("Abendessen morgen", {"title": "Abendessen", "allDay": False, "start": "2026-10-05T20:00", "end": "2026-10-05T21:00"}),
    # Everyday ones.
    ("Zahnarzt morgen 9:30", {"title": "Zahnarzt", "allDay": False, "start": "2026-10-05T09:30", "end": "2026-10-05T10:30"}),
    ("Meeting 14.10. 10-11:30", {"title": "Meeting", "allDay": False, "start": "2026-10-14T10:00", "end": "2026-10-14T11:30"}),
    ("Steuer 31.12.2026", {"title": "Steuer", "allDay": True, "start": "2026-12-31", "end": "2027-01-01"}),
    ("Elternabend am Donnerstag um 19 Uhr bis 20:30", {"title": "Elternabend", "allDay": False, "start": "2026-10-08T19:00", "end": "2026-10-08T20:30"}),
    ("Kinoabend 22-1 Uhr", {"title": "Kinoabend", "allDay": False, "start": "2026-10-04T22:00", "end": "2026-10-05T01:00"}),
    ("Oma besuchen übermorgen nachmittag", {"title": "Oma besuchen", "allDay": False, "start": "2026-10-06T15:00", "end": "2026-10-06T16:00"}),
    ("Release 2.2", {"title": "Release 2.2", "allDay": True, "start": "2026-10-04", "end": "2026-10-05"}),
    ("Termin um 3 Uhr nachmittag", {"title": "Termin", "allDay": False, "start": "2026-10-04T15:00", "end": "2026-10-04T16:00"}),
    ("Geburtstag Mia 8. Oktober", {"title": "Geburtstag Mia", "allDay": True, "start": "2026-10-08", "end": "2026-10-09"}),
    # Absences with a deputy (card 471febc2).
    ("Urlaub Mo–Fr Vertretung Jens", {"title": "Urlaub", "allDay": True, "start": "2026-10-05", "end": "2026-10-10", "absence": "urlaub", "deputy": "Jens"}),
    ("krank heute", {"title": "krank", "allDay": True, "start": "2026-10-04", "end": "2026-10-05", "absence": "krank"}),
    ("Homeoffice Freitag", {"title": "Homeoffice", "allDay": True, "start": "2026-10-09", "end": "2026-10-10", "absence": "homeoffice"}),
    ("Fortbildung Excel 14.10. vertreten durch Sabine Weber", {"title": "Fortbildung Excel", "allDay": True, "start": "2026-10-14",
                                                                "end": "2026-10-15", "absence": "weiterbildung", "deputy": "Sabine Weber"}),
]


def main():
    for text, expected in CASES:
        got = quick.parse(text, TODAY)
        assert got == expected, f"{text!r}: {got} statt {expected}"
    # Without a date the chosen day of the view is used.
    assert quick.parse("Laufen 7 Uhr", TODAY, date(2026, 10, 9))["start"] == "2026-10-09T07:00"
    assert quick.parse("", TODAY)["title"] == "Neuer Termin"
    print(f"ok – Termine in Alltagssprache ({len(CASES)} Beispiele)")


if __name__ == "__main__":
    main()
