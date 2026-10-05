"""Card 91adf47e: the Ubuntu alert service's bookkeeping – each alert rings once, missed ones after a
restart (up to six hours), a refused notification rings again, the clock going back, hidden calendars,
a changed calendar file. Isolated folder; no real notification is sent."""

import json
import os
import stat
import sys
import tempfile
from datetime import datetime
from pathlib import Path

SCRATCH = Path(tempfile.mkdtemp(prefix="lical-test-"))
os.environ["XDG_DATA_HOME"] = os.environ["XDG_STATE_HOME"] = str(SCRATCH)
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import reminders  # noqa: E402
from lical.store import Store  # noqa: E402


def main():
    calendar = SCRATCH / "lical" / "kalender.json"
    store = Store(calendar)
    store.events = [
        {"id": "z", "calendar": "privat", "title": "Zahnarzt", "allDay": False, "start": "2026-10-07T08:30", "end": "2026-10-07T09:15",
         "location": "Dr. Weber", "notes": "geheim", "alerts": [15, 1440]},
        {"id": "a", "calendar": "privat", "title": "Abendessen", "allDay": False, "start": "2026-10-06T20:00", "end": "2026-10-06T21:00", "alerts": [0]},
        {"id": "h", "calendar": "arbeit", "title": "Versteckt", "allDay": False, "start": "2026-10-07T09:00", "end": "2026-10-07T10:00", "alerts": [0]},
    ]
    store.calendar("arbeit")["visible"] = False
    store.save()
    shown = []
    refuse = {"on": False}

    def notify(alert, now):
        if refuse["on"]:
            return False
        shown.append((alert["title"], alert["at"], reminders.alerts.text(alert, now)))
        return True

    state = reminders.state_path()
    service = reminders.Reminders(calendar, state, notify)
    assert service.tick(datetime(2026, 10, 6, 8, 0)) == [] and shown == []  # first run: starts now
    assert stat.S_IMODE(state.stat().st_mode) == 0o600
    assert service.next == datetime(2026, 10, 6, 8, 30)
    assert service.tick(datetime(2026, 10, 6, 8, 29, 59)) == []
    service.tick(datetime(2026, 10, 6, 8, 30, 10))
    assert shown == [("Zahnarzt", "2026-10-06T08:30", "Morgen, 08:30–09:15 · Dr. Weber")], shown
    service.tick(datetime(2026, 10, 6, 8, 30, 25))
    assert len(shown) == 1  # once only

    # The notification service is not there (session just starting): it rings at the next tick.
    refuse["on"] = True
    assert service.tick(datetime(2026, 10, 6, 20, 0, 5)) == []
    refuse["on"] = False
    service.tick(datetime(2026, 10, 6, 20, 0, 20))
    assert shown[-1][0] == "Abendessen", shown

    # Computer off over night: a new service at 09:05 rings the missed 08:15 – nothing older than six
    # hours, nothing of the hidden calendar, never the notes.
    shown.clear()
    service = reminders.Reminders(calendar, state, notify)
    service.tick(datetime(2026, 10, 7, 9, 5))
    assert [title for title, _at, _text in shown] == ["Zahnarzt"], shown
    assert shown[0][2] == "Heute, 08:30–09:15 · Dr. Weber" and not any("geheim" in str(item) for item in shown)
    assert service.next is None

    # LiCal saved a new alert: after reloading it rings.
    store = Store(calendar)
    store.events.append({"id": "n", "calendar": "privat", "title": "Neu", "allDay": True, "start": "2026-10-08", "end": "2026-10-09",
                         "alerts": [-540]})
    store.save()
    service.reload()
    assert service.next == datetime(2026, 10, 8, 9, 0)
    service.tick(datetime(2026, 10, 8, 9, 0, 3))
    assert shown[-1][:2] == ("Neu", "2026-10-08T09:00") and shown[-1][2] == "Heute, ganztägig"

    # The clock went back (wrong time corrected): start from the new time instead of ringing nothing ever.
    service.tick(datetime(2026, 10, 1, 12, 0))
    assert json.loads(state.read_text())["checked"] == "2026-10-01T12:00:00"
    print("ok – Erinnerungen auf Ubuntu (Dienst)")


if __name__ == "__main__":
    main()
