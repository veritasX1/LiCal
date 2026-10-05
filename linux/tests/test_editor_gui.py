"""Card 502ccfb2: create and edit events in the real window (isolated folder): ＋ opens the inspector
for "Neuer Termin", title/place/times/all-day/repeat/calendar change the stored event at once, the
end never moves before the start, a repeating event can lose one occurrence, everything survives a
restart. Pictures of the inspector with --shots <folder>."""

import os
import sys
import tempfile
import time
from datetime import date, datetime, timedelta
from pathlib import Path

SCRATCH = tempfile.mkdtemp(prefix="lical-test-")
os.environ["XDG_DATA_HOME"] = os.environ["XDG_CONFIG_HOME"] = os.environ["XDG_CACHE_HOME"] = SCRATCH
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gi  # noqa: E402

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
gi.require_version("Graphene", "1.0")
from gi.repository import Adw, GLib, Graphene, Gtk  # noqa: E402

from lical import app as app_module, store as store_module  # noqa: E402

SHOTS = Path(sys.argv[sys.argv.index("--shots") + 1]) if "--shots" in sys.argv else None


def settle(seconds=0.4):
    context = GLib.MainContext.default()
    end = time.time() + seconds
    while time.time() < end:
        context.iteration(False)
        time.sleep(0.005)


def shot(widget, name):
    if SHOTS is None:
        return
    SHOTS.mkdir(parents=True, exist_ok=True)
    width, height = widget.get_width(), widget.get_height()
    snapshot = Gtk.Snapshot()
    Gtk.WidgetPaintable.new(widget).snapshot(snapshot, width, height)
    texture = widget.get_native().get_renderer().render_texture(snapshot.to_node(), Graphene.Rect().init(0, 0, width, height))
    texture.save_to_png(str(SHOTS / name))


def type_into(field, text):
    field.set_text(text)
    field.apply()
    settle(0.1)


class Test(app_module.Application):
    def do_activate(self):
        try:
            self.run_checks()
            print("ok – Termine anlegen und bearbeiten (Ubuntu)")
        except Exception:
            import traceback
            traceback.print_exc()
            sys.stdout.flush()
            os._exit(1)
        sys.stdout.flush()
        os._exit(0)

    def run_checks(self):
        app_module.load_css()
        path = Path(SCRATCH) / "kalender.json"
        store = store_module.Store(path)
        window = app_module.CalendarWindow(self, store)
        window.set_default_size(1240, 820)
        window.present()
        settle()
        day = date.today() + timedelta(days=2)
        window.go_to(day)
        window.set_mode("week")
        settle()
        window.create_event(day)
        settle(0.6)
        inspector = window.inspector
        assert inspector.get_visible(), "Inspektor offen"
        assert len(store.events) == 1 and store.events[0]["title"] == "Neuer Termin"
        assert store.events[0]["start"] == f"{day.isoformat()}T09:00", store.events[0]

        inspector.title.set_text("Zahnarzt")
        inspector.place.set_text("Dr. Weber")
        type_into(inspector.start_time, "8:30")
        settle(0.5)
        event = store.events[0]
        assert (event["title"], event.get("location"), event["start"], event["end"]) == \
            ("Zahnarzt", "Dr. Weber", f"{day.isoformat()}T08:30", f"{day.isoformat()}T09:30"), event
        type_into(inspector.end_time, "915")
        assert store.events[0]["end"] == f"{day.isoformat()}T09:15"
        type_into(inspector.end_time, "7:00")  # before the start: 5 minutes after it
        assert store.events[0]["end"] == f"{day.isoformat()}T08:35", store.events[0]
        type_into(inspector.end_time, "quatsch")  # nonsense: the field turns back
        assert inspector.end_time.get_text() == "08:35"
        inspector.step("start", 1)
        assert store.events[0]["start"] == f"{day.isoformat()}T08:45"
        inspector.calendar.set_selected(1)
        assert store.events[0]["calendar"] == store.calendars[1]["id"]
        if SHOTS:
            settle(0.3)
            shot(inspector, "inspektor.png")
            shot(window, "woche-mit-inspektor.png")

        inspector.all_day.set_active(True)
        event = store.events[0]
        assert event["allDay"] and event["start"] == day.isoformat() and event["end"] == (day + timedelta(days=1)).isoformat()
        type_into(inspector.end_date, f"{(day + timedelta(days=2)).day}.{(day + timedelta(days=2)).month}.{(day + timedelta(days=2)).year}")
        assert store.events[0]["end"] == (day + timedelta(days=3)).isoformat(), store.events[0]
        inspector.all_day.set_active(False)
        assert store.events[0]["start"] == f"{day.isoformat()}T09:00"

        inspector.repeat.set_selected(2)  # wöchentlich
        assert store.events[0]["rrule"] == "FREQ=WEEKLY"
        inspector.popdown()
        settle(0.3)

        # Delete only next week's occurrence of the series.
        next_week = f"{(day + timedelta(days=7)).isoformat()}T09:00"
        window.delete_occurrence(store.events[0]["id"], next_week)
        settle(0.3)
        window.delete_dialog.emit("response", "one")
        settle(0.2)
        assert store.events[0]["exdates"] == [next_week[:10]]
        keys = [item["key"] for item in store.occurrences(day, day + timedelta(days=15))]
        assert len(keys) == 2 and not any(next_week in key for key in keys), keys

        # Everything is in the file (a new store reads the same).
        again = store_module.Store(path)
        assert again.events == store.events
        assert oct(path.stat().st_mode & 0o777) == "0o600"

        # ＋ on the toolbar in the month view, then the Delete key removes the selected one.
        window.set_mode("month")
        settle()
        window.create_event(day + timedelta(days=1))
        settle(0.6)
        window.inspector.popdown()
        settle(0.2)
        assert len(store.events) == 2
        view = window.month_view
        created = next(event for event in store.events if "rrule" not in event)
        view.selected_item = next(item for item in store.occurrences(day, day + timedelta(days=3)) if item["id"] == created["id"])
        window.set_focus(view)
        from gi.repository import Gdk
        window.on_key(None, Gdk.KEY_Delete, 0, 0)
        assert len(store.events) == 1 and "rrule" in store.events[0]


Test(application_id="io.github.veritasx1.LiCalTest").run([])
