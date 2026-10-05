"""Card 9b27d02a: the Mac's ways of working in the real window (isolated folder) – ＋ in plain language,
keyboard shortcuts, search, dragging to create/move/resize, dragging in the month, calendars in the
sidebar, moving an event to another calendar, the day view's right column. Pictures with --shots."""

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
gi.require_version("Gdk", "4.0")
gi.require_version("Graphene", "1.0")
from gi.repository import Gdk, GLib, Graphene, Gtk  # noqa: E402

from lical import app as app_module, rules, store as store_module  # noqa: E402

SHOTS = Path(sys.argv[sys.argv.index("--shots") + 1]) if "--shots" in sys.argv else None
CTRL = Gdk.ModifierType.CONTROL_MASK
ALT = Gdk.ModifierType.ALT_MASK
SHIFT = Gdk.ModifierType.SHIFT_MASK


def settle(seconds=0.3):
    context = GLib.MainContext.default()
    end = time.time() + seconds
    while time.time() < end:
        context.iteration(False)
        time.sleep(0.005)


def shot(widget, name):
    if SHOTS is None:
        return
    SHOTS.mkdir(parents=True, exist_ok=True)
    for _attempt in range(10):  # the window may still be drawing (a popover just opened)
        settle(0.2)
        width, height = widget.get_width(), widget.get_height()
        snapshot = Gtk.Snapshot()
        Gtk.WidgetPaintable.new(widget).snapshot(snapshot, width, height)
        node = snapshot.to_node()
        if node is not None:
            widget.get_native().get_renderer().render_texture(node, Graphene.Rect().init(0, 0, width, height)).save_to_png(str(SHOTS / name))
            return


def walk(widget):
    yield widget
    child = widget.get_first_child()
    while child is not None:
        yield from walk(child)
        child = child.get_next_sibling()


class Test(app_module.Application):
    def do_activate(self):
        if self.get_active_window() is not None:  # "--tag" while running: what the real application does
            return app_module.Application.do_activate(self)
        try:
            self.run_checks()
            print("ok – Bedienung wie auf dem Mac (Ubuntu)")
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
        key = lambda keyval, state=0: window.on_key(None, keyval, 0, state)  # noqa: E731

        # Views and navigation by keyboard.
        for keyval, mode in ((Gdk.KEY_1, "day"), (Gdk.KEY_2, "week"), (Gdk.KEY_3, "month"), (Gdk.KEY_4, "year")):
            assert key(keyval, CTRL) and window.mode == mode
        key(Gdk.KEY_3, CTRL)
        start = window.day
        key(Gdk.KEY_Right, CTRL)
        assert window.day == rules.add_months(start.replace(day=1), 1)
        key(Gdk.KEY_t, CTRL)
        assert window.day == date.today()

        # ＋ in plain language.
        key(Gdk.KEY_n, CTRL)
        settle()
        window.quick_entry.set_text("Fußballspiel Samstag von 11–13 Uhr")
        settle(0.1)
        window.quick_entry.emit("activate")
        settle(0.6)
        event = store.events[0]
        saturday = date.today() + timedelta(days=(5 - date.today().weekday()) % 7)
        assert (event["title"], event["start"], event["end"]) == ("Fußballspiel", f"{saturday}T11:00", f"{saturday}T13:00"), event
        window.inspector.popdown()
        settle(0.2)

        # Search finds it, Enter opens it in the day view.
        window.search_entry.set_text("fußball")
        window.show_search("fußball")
        assert [hit["id"] for hit in window.search_hits] == [event["id"]]
        window.open_first_hit()
        settle(0.6)
        assert window.mode == "day" and window.day == saturday and window.inspector.get_visible()
        if SHOTS:
            shot(window, "tag-rechte-spalte.png")
        window.inspector.popdown()
        window.search_entry.set_text("")
        window.show_search("")
        settle(0.2)
        assert not window.search("gibtsnicht")

        # Ctrl+Alt+arrows: 15 minutes later, a day later.
        window.day_view.selected_key = f"{event['id']}@{event['start']}"
        window.day_view.selected_item = next(i for i in store.occurrences(saturday, saturday + timedelta(days=1)) if i["id"] == event["id"])
        window.set_focus(None)
        key(Gdk.KEY_Down, CTRL | ALT)
        assert store.events[0]["start"] == f"{saturday}T11:15", store.events[0]
        key(Gdk.KEY_Right, CTRL | ALT)
        assert store.events[0]["start"] == f"{saturday + timedelta(days=1)}T11:15"

        # Week view: drag over free time creates; drag the event moves; pulling its edge lengthens.
        window.go_to(saturday)
        key(Gdk.KEY_2, CTRL)
        settle(0.5)
        view = window.week_view
        width = view.body.get_width()
        column = view.columns(width)[0][1]
        monday = rules.week_start(saturday)
        x_tuesday = view.columns(width)[1][0] + column / 2
        y = lambda hour: 6 + hour * 50  # noqa: E731
        view.on_drag_begin(None, x_tuesday, y(14))
        view.drag["active"] = True
        view.drag.update(dx=0, dy=y(16) - y(14))
        view.on_drag_end(None, 0, 0)
        settle(0.6)
        created = next(e for e in store.events if e["start"] == f"{monday + timedelta(days=1)}T14:00")
        assert created["end"] == f"{monday + timedelta(days=1)}T16:00", created
        window.inspector.popdown()
        settle(0.3)
        area = view.area_of(f"{created['id']}@{created['start']}")
        _header, ax, ay, aw, ah = area
        view.on_drag_begin(None, ax + 20, ay + 10)
        assert view.drag["kind"] == "move"
        view.drag.update(dx=column, dy=50, active=True)  # a day later, an hour later
        view.on_drag_end(None, 0, 0)
        settle(0.3)
        moved = next(e for e in store.events if e["id"] == created["id"])
        assert (moved["start"], moved["end"]) == (f"{monday + timedelta(days=2)}T15:00", f"{monday + timedelta(days=2)}T17:00"), moved
        settle(0.3)
        _header, ax, ay, aw, ah = view.area_of(f"{moved['id']}@{moved['start']}")
        view.on_drag_begin(None, ax + 20, ay + ah - 3)
        assert view.drag["kind"] == "resize"
        view.drag.update(dy=25, active=True)  # half an hour longer
        view.on_drag_end(None, 0, 0)
        assert next(e for e in store.events if e["id"] == created["id"])["end"] == f"{monday + timedelta(days=2)}T17:30"

        # Month: drag an event to another day.
        key(Gdk.KEY_3, CTRL)
        settle(0.5)
        month = window.month_view
        occurrence_key = f"{created['id']}@{monday + timedelta(days=2)}T15:00"
        ax, ay, aw, ah = month.area_of(occurrence_key)
        target = next(a for a in month.areas if a[0] == "day" and a[1] == monday + timedelta(days=4))
        month.on_drag_begin(None, ax + 5, ay + 5)
        month.drag.update(dx=target[2] + 10 - (ax + 5), dy=target[3] + 10 - (ay + 5), active=True)
        month.on_drag_end(None, 0, 0)
        assert next(e for e in store.events if e["id"] == created["id"])["start"] == f"{monday + timedelta(days=4)}T15:00"

        # Calendars: new, rename, color, delete; an event into another calendar.
        calendar = store.add_calendar("Verein", "purple")
        window.sidebar.refresh()
        store.update_calendar(calendar["id"], name="Sportverein", color="teal")
        assert store.calendar(calendar["id"]) == {**calendar, "name": "Sportverein", "color": "teal"}
        item = next(i for i in store.occurrences(monday, monday + timedelta(days=7)) if i["id"] == created["id"])
        window.set_calendar(item, calendar["id"])
        assert next(e for e in store.events if e["id"] == created["id"])["calendar"] == calendar["id"]
        assert store.delete_calendar(calendar["id"])
        assert all(e["id"] != created["id"] for e in store.events)  # its events went with it
        assert store_module.Store(path).calendars == store.calendars  # saved

        # Tab selects the next event; Delete removes the selected one.
        key(Gdk.KEY_1, CTRL)
        window.go_to(saturday + timedelta(days=1))
        settle(0.3)
        window.set_focus(None)
        key(Gdk.KEY_Tab)
        assert window.day_view.selected_item["title"] == "Fußballspiel"
        key(Gdk.KEY_Delete)
        assert not store.events
        # Absence with a deputy (card 471febc2): ＋ understands it, the inspector changes it.
        key(Gdk.KEY_n, CTRL)
        settle()
        window.quick_entry.set_text("Urlaub Mo–Fr Vertretung Jens")
        window.quick_entry.emit("activate")
        settle(0.6)
        away = store.events[0]
        assert (away["absence"], away["deputy"], away["allDay"]) == ("urlaub", "Jens", True), away
        inspector = window.inspector
        assert inspector.absence.get_selected() == 1 and inspector.deputy.get_text() == "Jens"
        inspector.weeks.get_first_child().get_next_sibling().emit("clicked")  # 2 weeks
        first = rules.parse(away["start"])
        assert store.events[0]["end"] == (first + timedelta(days=14)).isoformat(), store.events[0]
        inspector.deputy.set_text("Sabine")
        inspector.flush()
        assert store.events[0]["deputy"] == "Sabine"
        if SHOTS:
            inspector.popdown()
            key(Gdk.KEY_2, CTRL)
            window.go_to(first)
            window.week_view.selected_key = window.week_view.selected_item = None
            settle(1.0)  # the view change cross-fades
            shot(window, "woche-abwesenheit.png")
            window.open_inspector(window.week_view, next(i for i in store.occurrences(first, first + timedelta(days=1)) if i["id"] == away["id"]))
            settle(0.4)
            shot(window.inspector, "inspektor-abwesenheit.png")
            inspector = window.inspector
        inspector.absence.set_selected(0)
        assert "absence" not in store.events[0] and "deputy" not in store.events[0], store.events[0]
        inspector.popdown()

        # Alerts in the inspector (card 91adf47e): "Hinweis", the second row once the first is set,
        # all-day has its own choices and starts over.
        from lical import alerts
        store.put({"id": "al1", "calendar": "privat", "title": "Hinweis-Test", "allDay": False, "start": f"{saturday}T15:00", "end": f"{saturday}T16:00"})
        window.go_to(saturday)
        key(Gdk.KEY_1, CTRL)
        settle(0.5)
        window.open_inspector(window.day_view, next(i for i in store.occurrences(saturday, saturday + timedelta(days=1)) if i["id"] == "al1"))
        settle(0.4)
        inspector = window.inspector
        first, second = inspector.alert_boxes
        assert not second.get_parent().get_visible()
        labels = [label for _value, label in inspector.alert_options]
        first.set_selected(labels.index("15 Minuten vorher"))
        settle(0.3)
        assert inspector.get_visible(), "Inspektor nach Hinweis-Wahl geschlossen"
        assert store.events[-1]["alerts"] == [15], store.events[-1]
        assert second.get_parent().get_visible()
        second.set_selected(labels.index("1 Tag vorher"))
        settle(0.3)
        assert inspector.get_visible()
        assert next(e for e in store.events if e["id"] == "al1")["alerts"] == [15, 1440]
        if SHOTS:
            shot(inspector, "inspektor-hinweise.png")
        first.set_selected(0)  # "Keiner": the second goes too
        assert "alerts" not in next(e for e in store.events if e["id"] == "al1")
        first.set_selected(labels.index("5 Minuten vorher"))
        inspector.all_day.set_active(True)
        event = next(e for e in store.events if e["id"] == "al1")
        assert event["allDay"] and "alerts" not in event, event
        assert [label for _v, label in inspector.alert_options][1] == alerts.ALL_DAY[0][1]
        inspector.popdown()
        settle(0.2)

        # Pass an event on as a QR code (card b483dadf): only this event, notes only on request.
        from lical import ics
        share_me = {"id": "qr1", "calendar": "arbeit", "title": "Zahnarzt", "allDay": False, "start": f"{saturday}T08:30",
                    "end": f"{saturday}T09:15", "location": "Dr. Weber, Köln", "notes": "Privat: Krone", "absence": "abwesend", "deputy": "Jens"}
        store.put(share_me)
        window.share_event("qr1")
        settle(0.4)
        dialog = window.share_dialog
        assert ics.from_ics(dialog.text) == {"title": "Zahnarzt", "allDay": False, "start": f"{saturday}T08:30",
                                             "end": f"{saturday}T09:15", "location": "Dr. Weber, Köln"}, dialog.text
        assert "Krone" not in dialog.text and "Jens" not in dialog.text and "qr1" not in dialog.text and "arbeit" not in dialog.text
        if SHOTS:
            shot(dialog, "qr-teilen.png")
        switch = [w for w in walk(dialog) if isinstance(w, Gtk.Switch)][0]
        switch.set_active(True)
        assert ics.from_ics(dialog.text)["notes"] == "Privat: Krone"
        dialog.close()
        settle(0.2)
        # Holidays (card 7a9187d6): computed, read-only, a Bundesland adds its own days.
        from lical import holidays
        october = store.occurrences(date(2026, 10, 1), date(2026, 11, 2))
        assert any(i["title"] == "Tag der Deutschen Einheit" and i["start"] == "2026-10-03" for i in october)
        assert not any(i["title"] == "Allerheiligen" for i in october)
        window.sidebar.holiday_menu(window.sidebar.list.get_last_child())
        settle(0.2)
        window.sidebar.holiday_states.set_selected([k for k, _n in holidays.STATES].index("NW"))
        settle(0.2)
        assert store.holidays["state"] == "NW"
        assert any(i["title"] == "Allerheiligen" for i in store.occurrences(date(2026, 10, 1), date(2026, 11, 2)))
        assert all(c["id"] != holidays.CALENDAR for c in store.calendars)  # never offered for new events
        window.go_to(date(2026, 10, 3))
        key(Gdk.KEY_1, CTRL)
        settle(0.5)
        unity = next(i for i in store.occurrences(date(2026, 10, 3), date(2026, 10, 4)) if i["calendar"] == holidays.CALENDAR)
        window.open_inspector(window.day_view, unity)
        settle(0.3)
        assert window.inspector.get_visible() and not hasattr(window.inspector, "alert_boxes")
        if SHOTS:
            shot(window.inspector, "feiertag.png")
        window.inspector.popdown()
        count = len(store.events)
        window.move_event(unity, days=1)
        window.delete_occurrence(unity["id"], unity["start"])
        assert len(store.events) == count and store.holidays["state"] == "NW"
        store.set_holidays(visible=False)
        assert not any(i["calendar"] == holidays.CALENDAR for i in store.occurrences(date(2026, 10, 1), date(2026, 11, 2)))
        assert store.holidays["chosen"]
        store.set_holidays(visible=True, state="")
        settle(0.2)

        # Time zones (card 7a9187d6): Ctrl+, turns the support on; the event keeps its own times, the
        # views show Berlin time, dragging changes it in its own zone.
        from lical import settings as lical_settings
        os.environ["TZ"] = "Europe/Berlin"
        window.activate_action("win.preferences", None)  # Ctrl+, (menu shortcut)
        settle(0.3)
        window.preferences.controls["zones"].set_active(True)
        assert lical_settings.get("timeZones")
        window.preferences.close()
        store.put({"id": "ny1", "calendar": "arbeit", "title": "Call New York", "allDay": False, "start": "2026-10-26T09:00",
                   "end": "2026-10-26T10:00"})
        window.go_to(date(2026, 10, 26))
        key(Gdk.KEY_1, CTRL)
        settle(0.5)
        window.open_inspector(window.day_view, next(i for i in store.occurrences(date(2026, 10, 26), date(2026, 10, 27)) if i["id"] == "ny1"))
        settle(0.4)
        inspector = window.inspector
        assert inspector.zone.get_parent().get_visible()
        inspector.zone.set_selected(inspector.zone_names.index("America/New_York"))
        settle(0.3)
        assert inspector.get_visible()
        saved = next(e for e in store.events if e["id"] == "ny1")
        assert (saved["tz"], saved["start"]) == ("America/New_York", "2026-10-26T09:00"), saved  # the clock time stays
        shown = next(i for i in store.occurrences(date(2026, 10, 26), date(2026, 10, 27)) if i["id"] == "ny1")
        assert shown["start"] == "2026-10-26T14:00" and shown["zoneStart"] == "2026-10-26T09:00", shown
        if SHOTS:
            shot(inspector, "inspektor-zeitzone.png")
        inspector.popdown()
        settle(0.2)
        # Dragged one hour later in the view (15:00 Berlin) = 10:00 in New York.
        window.move_event(shown, start=datetime(2026, 10, 26, 15, 0), end=datetime(2026, 10, 26, 16, 0))
        saved = next(e for e in store.events if e["id"] == "ny1")
        assert (saved["start"], saved["end"], saved["tz"]) == ("2026-10-26T10:00", "2026-10-26T11:00", "America/New_York"), saved
        # Opened again: its own times, never the local ones written back.
        settle(0.4)  # the view draws the moved event first
        shown = next(i for i in store.occurrences(date(2026, 10, 26), date(2026, 10, 27)) if i["id"] == "ny1")
        window.open_inspector(window.day_view, shown)
        settle(0.3)
        assert window.inspector.start_time.get_text() == "10:00"
        window.inspector.flush()
        assert next(e for e in store.events if e["id"] == "ny1")["start"] == "2026-10-26T10:00"
        window.inspector.popdown()
        window.day_details.show(date(2026, 10, 26), shown)
        texts = [w.get_label() for w in walk(window.day_details) if isinstance(w, Gtk.Label)]
        assert any("10:00 bis 11:00 Uhr in New York (Amerika)" in t for t in texts), texts
        lical_settings.put("timeZones", False)
        settle(0.2)

        # Printing (card 7a9187d6): Ctrl+P, month landscape one page each, list with notes on request – as PDF.
        import subprocess
        key(Gdk.KEY_3, CTRL)
        window.go_to(date(2026, 10, 1))
        window.activate_action("win.print", None)  # Ctrl+P (menu shortcut)
        assert self.get_accels_for_action("win.print") == ["<Control>p"]
        settle(0.3)
        dialog = window.print_dialog
        dialog.controls["count"].set_value(2)
        month_pdf = Path(SCRATCH) / "monat.pdf"
        dialog.job().export(month_pdf)
        info = subprocess.run(["pdfinfo", str(month_pdf)], capture_output=True, text=True).stdout
        import re
        assert re.search(r"Pages:\s+2\n", info) and "841.89 x 595.276" in info, info  # two A4 pages, landscape
        text = subprocess.run(["pdftotext", "-layout", str(month_pdf), "-"], capture_output=True, text=True).stdout
        assert "Oktober" in text and "November" in text and "Tag der Deutschen Einheit" in text, text[:500]
        dialog.controls["view"].set_selected(1)
        dialog.controls["notes"].set_active(True)
        store.put({"id": "pr1", "calendar": "privat", "title": "Druckprobe", "allDay": False, "start": "2026-10-14T10:00",
                   "end": "2026-10-14T11:00", "location": "Büro", "notes": "Unterlagen mitbringen"})
        list_pdf = Path(SCRATCH) / "liste.pdf"
        dialog.job().export(list_pdf)
        text = subprocess.run(["pdftotext", "-layout", str(list_pdf), "-"], capture_output=True, text=True).stdout
        assert "Mittwoch, 14. Oktober 2026" in text and "10:00–11:00" in text and "Druckprobe" in text and "Unterlagen mitbringen" in text, text
        dialog.controls["notes"].set_active(False)
        dialog.job().export(list_pdf)
        assert "Unterlagen" not in subprocess.run(["pdftotext", str(list_pdf), "-"], capture_output=True, text=True).stdout
        if SHOTS:
            import shutil
            shutil.copy(month_pdf, SHOTS / "druck-monat.pdf")
            shutil.copy(Path(SCRATCH) / "liste.pdf", SHOTS / "druck-liste.pdf")
            shot(dialog, "drucken.png")
        dialog.close()
        settle(0.2)

        # LiCal as the calendar app: an .ics file asks first, then lands in the chosen calendar.
        count = len(store.events)
        invite = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:Elternabend\r\nDTSTART:20261022T190000\r\nDTEND:20261022T203000\r\nLOCATION:Schule\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        dialog = window.ask_import(invite, "einladung.ics")
        settle(0.3)
        assert "Elternabend" in dialog.get_heading() and "19:00–20:30" in dialog.get_body() and len(store.events) == count
        if SHOTS:
            shot(window, "ics-oeffnen.png")
        window.import_calendars.set_selected(2)  # Familie
        dialog.emit("response", "add")
        dialog.close()
        settle(0.3)
        added = store.events[-1]
        assert (added["title"], added["start"], added["calendar"]) == ("Elternabend", "2026-10-22T19:00", "familie"), added
        assert window.mode == "day" and window.day == date(2026, 10, 22)
        bad = window.ask_import("kein Kalender", "notiz.txt")
        assert bad.get_heading() == "Kein Termin gefunden" and len(store.events) == count + 1
        bad.close()
        settle(0.2)

        # A clicked notification opens LiCal on that day (lical --tag, also into an open window).
        key(Gdk.KEY_3, CTRL)
        self.activate_action("tag", GLib.Variant("s", (saturday + timedelta(days=3)).isoformat()))
        settle(0.3)
        assert window.mode == "day" and window.day == saturday + timedelta(days=3), (window.mode, window.day)
        if SHOTS:
            key(Gdk.KEY_3, CTRL)
            window.go_to(date.today())
            settle(1.0)
            shot(window, "monat.png")


Test(application_id="io.github.veritasx1.LiCalMacTest").run([])
