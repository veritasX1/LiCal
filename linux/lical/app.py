"""LiCal for Ubuntu – a calendar in the look and feel of Apple's Calendar (card 93f6a101)."""

import sys
from pathlib import Path

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gdk, Gio, GLib, Gtk

from . import theme  # noqa: F401  (registers the font)
from .store import Store
from .window import CalendarWindow

APP_ID = "io.github.veritasx1.LiCal"


def load_css():
    provider = Gtk.CssProvider()
    provider.load_from_path(str(Path(__file__).resolve().parent / "style.css"))
    Gtk.StyleContext.add_provider_for_display(Gdk.Display.get_default(), provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)


class Application(Adw.Application):
    def __init__(self, store=None, application_id=APP_ID):
        # HANDLES_OPEN: LiCal as the computer's calendar app opens .ics files (invitations, downloads).
        super().__init__(application_id=application_id, flags=Gio.ApplicationFlags.HANDLES_OPEN)
        self.store = store
        self.new_event = "--neuer-termin" in sys.argv
        self.open_day = None
        # "--tag 2026-10-07" (a notification was clicked): show that day – also in a window already open.
        show_day = Gio.SimpleAction.new("tag", GLib.VariantType.new("s"))
        show_day.connect("activate", lambda _action, value: self.show_day(value.get_string()))
        self.add_action(show_day)

    def show_day(self, text):
        from datetime import date
        try:
            self.open_day = date.fromisoformat(text)
        except ValueError:
            return
        self.activate()

    def do_open(self, files, _count, _hint):
        """Like double-clicking an .ics file on the Mac: asks before adding (one event per file)."""
        self.activate()
        window = self.get_active_window()
        for file in files:
            try:
                ok, data, _tag = file.load_contents(None)
                text = data[:512 * 1024].decode("utf-8", "replace") if ok else ""
            except GLib.Error:
                text = ""
            window.ask_import(text, file.get_basename() or "Datei")

    def do_activate(self):
        window = self.get_active_window()
        if window is None:
            load_css()
            window = CalendarWindow(self, self.store or Store())
        window.present()
        if self.open_day is not None:
            day, self.open_day = self.open_day, None
            window.set_mode("day")
            window.go_to(day)
        if self.new_event:
            self.new_event = False
            GLib.timeout_add(300, lambda: window.show_quick_add() and False)


def main():
    """LICAL_DEMO=1: a look with example events in a temporary folder – real data stays untouched.
    --icon: only renew the launcher icon (today's date); --neuer-termin: open with ＋;
    --tag YYYY-MM-DD: open on that day; --erinnerungen: the alert service (lical-erinnerungen.service)."""
    import os
    if "--erinnerungen" in sys.argv:
        from . import reminders
        reminders.run()
        return 0
    if "--tag" in sys.argv:
        index = sys.argv.index("--tag")
        day = sys.argv[index + 1] if index + 1 < len(sys.argv) else ""
        application = Application()
        application.register(None)
        application.activate_action("tag", GLib.Variant("s", day))
        if application.get_is_remote():
            return 0
        return application.run(sys.argv[:1])
    if "--icon" in sys.argv:
        from . import icon
        icon.update()
        return 0
    try:
        from . import icon
        icon.update()
    except Exception as error:
        print("LiCal: Icon nicht erneuert:", error)
    store = None
    if os.environ.get("LICAL_DEMO") == "1":
        import tempfile
        from datetime import date
        from .store import demo_events
        store = Store(Path(tempfile.mkdtemp(prefix="lical-demo-")) / "kalender.json")
        store.events = demo_events(date.today())
        store.save()
        return Application(store, application_id=APP_ID + ".Demo").run(sys.argv[:1])
    return Application(store).run([arg for arg in sys.argv if arg != "--neuer-termin"])
