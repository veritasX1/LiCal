"""Alerts as desktop notifications (card 91adf47e) – also while LiCal's window is closed, like the Mac.
A small user service (`lical --erinnerungen`, lical-erinnerungen.service): it reads the calendar file,
notices changes, and every few seconds checks whether the next alert is due. Alerts missed while the
computer was off ring at the next start, up to six hours late. A click on a notification opens LiCal
on that day. Nothing leaves the computer; notes never appear in a notification."""

import json
import os
from datetime import datetime, timedelta
from pathlib import Path

from . import alerts
from .store import Store, data_dir

APP_ID = "io.github.veritasx1.LiCal"
LATEST_MISSED = timedelta(hours=6)
TICK_SECONDS = 15


def state_path():
    return Path(os.environ.get("XDG_STATE_HOME") or Path.home() / ".local" / "state") / "lical" / "erinnerungen.json"


class Reminders:
    """The bookkeeping without GTK: when was last checked, what rings now. `notify(alert, now)` shows
    one alert and returns False when it could not (then it rings again at the next tick)."""

    def __init__(self, calendar_path, state, notify):
        self.calendar_path = Path(calendar_path)
        self.state = Path(state)
        self.notify = notify
        self.events = []
        self.next = None
        self.checked = None
        if self.state.exists():
            try:
                self.checked = datetime.fromisoformat(json.loads(self.state.read_text())["checked"])
            except (ValueError, KeyError, json.JSONDecodeError):
                self.checked = None
        self.reload()

    def reload(self, now=None):
        try:
            self.events = Store(self.calendar_path).visible_events()
        except (OSError, json.JSONDecodeError):
            return  # half written: the next change notice reads it again
        self.next = alerts.next_time(self.events, self.checked or now or datetime.now())

    def save_state(self):
        self.state.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = self.state.with_suffix(".tmp")
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as handle:
            json.dump({"checked": self.checked.strftime("%Y-%m-%dT%H:%M:%S")}, handle)
        os.replace(temporary, self.state)

    def tick(self, now=None):
        """Ring what is due since the last check; remember up to where. The first run starts now."""
        now = (now or datetime.now()).replace(microsecond=0)
        if self.checked is None or self.checked > now:  # first run, or the clock went back
            self.checked = now
            self.save_state()
            self.next = alerts.next_time(self.events, now)
            return []
        if self.next is None or self.next > now:
            return []
        rung = []
        for alert in alerts.due(self.events, max(self.checked, now - LATEST_MISSED), now):
            if not self.notify(alert, now):
                break
            rung.append(alert)
        else:
            self.checked = now
        if rung and self.checked != now:
            self.checked = datetime.fromisoformat(rung[-1]["at"])
        self.save_state()
        self.next = alerts.next_time(self.events, self.checked)
        return rung


def run():
    """The service: notifications over D-Bus (org.freedesktop.Notifications), a click opens the day."""
    import gi
    gi.require_version("Gio", "2.0")
    from gi.repository import Gio, GLib

    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    opened = {}  # notification id → day

    def notify(alert, now):
        hints = {"desktop-entry": GLib.Variant("s", APP_ID), "category": GLib.Variant("s", "x-gnome.calendar.event"),
                 "urgency": GLib.Variant("y", 1)}
        try:
            reply = bus.call_sync("org.freedesktop.Notifications", "/org/freedesktop/Notifications", "org.freedesktop.Notifications",
                                  "Notify", GLib.Variant("(susssasa{sv}i)", ("LiCal", 0, APP_ID, alert["title"] or "Termin",
                                                                            alerts.text(alert, now), ["default", "Öffnen"], hints, -1)),
                                  GLib.VariantType("(u)"), Gio.DBusCallFlags.NONE, 5000, None)
        except GLib.Error as error:
            print("LiCal-Erinnerungen: Mitteilung nicht gezeigt:", error.message)
            return False
        opened[reply.unpack()[0]] = alert["start"][:10]
        return True

    def on_action(_connection, _sender, _path, _interface, _signal, parameters):
        number, action = parameters.unpack()
        day = opened.pop(number, None)
        if day and action == "default":
            launcher = Path.home() / ".local" / "bin" / "lical"
            GLib.spawn_async([str(launcher), "--tag", day], flags=GLib.SpawnFlags.DEFAULT)

    bus.signal_subscribe("org.freedesktop.Notifications", "org.freedesktop.Notifications", "ActionInvoked",
                         "/org/freedesktop/Notifications", None, Gio.DBusSignalFlags.NONE, on_action)
    reminders = Reminders(data_dir() / "kalender.json", state_path(), notify)
    # The calendar changed (LiCal saved): read it again. The folder is watched, the file is replaced on save.
    folder = Gio.File.new_for_path(str(reminders.calendar_path.parent))
    reminders.calendar_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    monitor = folder.monitor_directory(Gio.FileMonitorFlags.WATCH_MOVES, None)
    monitor.connect("changed", lambda _m, file, other, _event: reminders.reload()
                    if reminders.calendar_path.name in (file.get_basename(), other.get_basename() if other else None) else None)
    reminders.tick()
    GLib.timeout_add_seconds(TICK_SECONDS, lambda: reminders.tick() or True)
    GLib.MainLoop().run()
