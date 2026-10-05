"""The event inspector like macOS Calendar: a popover next to the event – title, place, all-day,
start and end, repeat, calendar, note – every change applies at once (no save button, as on the Mac).
Dates are typed as 4.10.2026, times as 9:30 (also 930 or 9); arrow keys step a day or 15 minutes."""

import re
from datetime import date, datetime, timedelta

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, GLib, Gtk

from . import alerts, editing, rules, settings, theme
from .i18n import _


def format_date(day):
    return f"{day.day}.{day.month}.{day.year}"


def parse_date(text, fallback):
    match = re.match(r"^\s*(\d{1,2})\.(\d{1,2})\.?(\d{2,4})?\s*$", text)
    if not match:
        return None
    year = int(match.group(3)) if match.group(3) else fallback.year
    if year < 100:
        year += 2000
    try:
        return date(year, int(match.group(2)), int(match.group(1)))
    except ValueError:
        return None


def parse_time(text):
    """9:30, 09.30, 930, 9 → (9, 30)."""
    text = text.strip().replace(".", ":")
    match = re.match(r"^(\d{1,2})(?::?(\d{2}))?$", text)
    if not match:
        return None
    hour, minute = int(match.group(1)), int(match.group(2) or 0)
    if hour > 23 or minute > 59:
        return None
    return hour, minute


class Field(Gtk.Entry):
    """A small date or time field: Enter or leaving it applies, ↑/↓ step, a wrong entry turns back."""

    def __init__(self, width, on_apply, on_step):
        super().__init__(width_chars=width, max_width_chars=width, css_classes=["lical-field"], xalign=0.5)
        self.on_apply = on_apply
        self.connect("activate", lambda _e: self.apply())
        focus = Gtk.EventControllerFocus()
        focus.connect("leave", lambda _c: self.apply())
        self.add_controller(focus)
        keys = Gtk.EventControllerKey()
        keys.connect("key-pressed", lambda _c, keyval, _code, _state: self.step(keyval, on_step))
        self.add_controller(keys)

    def apply(self):
        self.on_apply(self.get_text())

    def step(self, keyval, on_step):
        from gi.repository import Gdk
        if keyval in (Gdk.KEY_Up, Gdk.KEY_Down):
            on_step(1 if keyval == Gdk.KEY_Up else -1)
            return True
        return False


class Inspector(Gtk.Popover):
    def __init__(self, window, store, occurrence):
        super().__init__(css_classes=["lical-inspector"], has_arrow=True, autohide=True)
        self.window = window
        self.store = store
        self.series = next(event for event in store.events if event["id"] == occurrence["id"])
        occurrence = rules.for_editing(occurrence)  # another zone: edited in its own times (card 7a9187d6)
        self.occurrence_start = occurrence["start"]
        # What the inspector shows and edits: this occurrence (its own dates), the series' other fields.
        self.event = {key: value for key, value in occurrence.items() if key != "key"}
        self.updating = False
        self.save_timer = None

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2, width_request=320)
        self.title = Gtk.Entry(text=self.event.get("title", ""), placeholder_text=_("Neuer Termin"), css_classes=["lical-inspector-title", "flat"])
        self.title.connect("changed", lambda entry: self.change(title=entry.get_text().strip() or _("Neuer Termin"), later=True))
        box.append(self.title)
        self.place = Gtk.Entry(text=self.event.get("location", ""), placeholder_text=_("Ort hinzufügen"), css_classes=["lical-inspector-place", "flat"])
        self.place.connect("changed", lambda entry: self.change(location=entry.get_text().strip(), later=True))
        box.append(self.place)
        box.append(Gtk.Separator(margin_top=6, margin_bottom=6))

        grid = Gtk.Grid(column_spacing=10, row_spacing=7)
        self.all_day = Gtk.CheckButton(active=bool(self.event.get("allDay")))
        self.all_day.connect("toggled", lambda button: self.set_all_day(button.get_active()))
        self.start_date = Field(10, lambda text: self.set_date("start", text), lambda step: self.step("start", step, days=True))
        self.start_time = Field(5, lambda text: self.set_time("start", text), lambda step: self.step("start", step))
        self.end_date = Field(10, lambda text: self.set_date("end", text), lambda step: self.step("end", step, days=True))
        self.end_time = Field(5, lambda text: self.set_time("end", text), lambda step: self.step("end", step))
        self.repeat = Gtk.DropDown.new_from_strings([label for _rule, label in editing.REPEATS])
        self.repeat.connect("notify::selected", lambda dropdown, _p: self.set_repeat(dropdown.get_selected()))
        self.calendar = Gtk.DropDown.new_from_strings([calendar["name"] for calendar in store.calendars])
        self.calendar.set_factory(self.calendar_factory())
        self.calendar.connect("notify::selected", lambda dropdown, _p: self.set_calendar(dropdown.get_selected()))
        # Time zone (card 7a9187d6): only with "Zeitzonen-Unterstützung" on, or when the event has one.
        self.zone_names = [None] + settings.zones()
        self.zone = Gtk.DropDown.new_from_strings([_("Ortszeit (schwebend)")] + [settings.zone_label(name) for name in self.zone_names[1:]])
        self.zone.set_enable_search(True)
        self.zone.set_expression(Gtk.PropertyExpression.new(Gtk.StringObject, None, "string"))
        self.zone.connect("notify::selected", lambda dropdown, _p: self.set_zone(dropdown.get_selected()))
        # Alerts like the Mac's (card 91adf47e): "Hinweis", a second one below once the first is set.
        self.alert_options = []
        self.alert_boxes = [Gtk.DropDown(model=Gtk.StringList()) for _slot in range(alerts.MOST)]
        for slot, dropdown in enumerate(self.alert_boxes):
            dropdown.connect("notify::selected", lambda dropdown, _p, slot=slot: self.set_alert(slot, dropdown.get_selected()))
        # Absence (Urlaub, Krank …) with a deputy and quick lengths (card 471febc2).
        self.absence = Gtk.DropDown.new_from_strings([_("Keine")] + [label for _key, label, _away in editing.ABSENCES])
        self.absence.connect("notify::selected", lambda dropdown, _p: self.set_absence(dropdown.get_selected()))
        self.deputy = Gtk.Entry(placeholder_text=_("Name"), width_chars=16)
        self.deputy.connect("changed", lambda entry: self.change(later=True, **{"deputy": entry.get_text().strip()})
                            if not self.updating else None)
        self.weeks = Gtk.Box(css_classes=["linked"])
        for weeks in (1, 2, 3, 4):
            button = Gtk.Button(label=_("{weeks} Wo.", weeks=weeks), tooltip_text=_("{weeks} {value} ab Beginn", weeks=weeks, value='Woche' if weeks == 1 else 'Wochen'))
            button.connect("clicked", lambda _b, weeks=weeks: self.replace(editing.absence_weeks(self.event, weeks)))
            self.weeks.append(button)
        rows = [(_("ganztägig"), [self.all_day]), (_("Beginn"), [self.start_date, self.start_time]),
                (_("Ende"), [self.end_date, self.end_time]), (_("Zeitzone"), [self.zone]), (_("Wiederholen"), [self.repeat]), (_("Kalender"), [self.calendar]),
                (_("Hinweis"), [self.alert_boxes[0]]), ("", [self.alert_boxes[1]]), (_("Abwesenheit"), [self.absence]), (_("Vertretung"), [self.deputy]), ("Dauer", [self.weeks])]
        self.absence_rows = []
        for row, (label, widgets) in enumerate(rows):
            name = Gtk.Label(label=label, xalign=1, css_classes=["lical-inspector-label"], width_request=86)
            grid.attach(name, 0, row, 1, 1)
            line = Gtk.Box(spacing=6)
            for widget in widgets:
                line.append(widget)
            grid.attach(line, 1, row, 1, 1)
            if label in (_("Vertretung"), "Dauer"):
                self.absence_rows += [name, line]
            if widgets[0] is self.alert_boxes[1]:
                self.second_alert_row = [name, line]
            if widgets[0] is self.zone:
                self.zone_row = [name, line]
        box.append(grid)
        box.append(Gtk.Separator(margin_top=8, margin_bottom=6))
        self.notes = Gtk.TextView(wrap_mode=Gtk.WrapMode.WORD_CHAR, css_classes=["lical-inspector-notes"], top_margin=4, bottom_margin=4)
        self.notes.get_buffer().set_text(self.event.get("notes", ""))
        self.notes.get_buffer().connect("changed", lambda buffer: self.change(
            notes=buffer.get_text(buffer.get_start_iter(), buffer.get_end_iter(), False), later=True))
        notes_frame = Gtk.ScrolledWindow(child=self.notes, min_content_height=48, max_content_height=140, propagate_natural_height=True)
        self.notes_hint = Gtk.Label(label=_("Notiz hinzufügen"), xalign=0, css_classes=["lical-inspector-hint"], can_target=False)
        overlay = Gtk.Overlay(child=notes_frame)
        overlay.add_overlay(self.notes_hint)
        box.append(overlay)
        delete = Gtk.Button(label=_("Termin löschen"), css_classes=["flat", "lical-delete"], halign=Gtk.Align.START, margin_top=6)
        delete.connect("clicked", lambda _b: (self.popdown(), self.window.delete_occurrence(self.series["id"], self.occurrence_start)))
        share = Gtk.Button(label=_("Als QR-Code teilen …"), css_classes=["flat"], halign=Gtk.Align.START, margin_top=6)
        share.connect("clicked", lambda _b: (self.popdown(), self.window.share_event(self.series["id"])))
        actions = Gtk.Box(spacing=6)
        actions.append(share)
        actions.append(Gtk.Box(hexpand=True))
        actions.append(delete)
        delete.set_halign(Gtk.Align.END)
        box.append(actions)
        for side in ("start", "end", "top", "bottom"):
            getattr(box, f"set_margin_{side}")(12 if side in ("start", "end") else 10)
        self.set_child(box)
        self.connect("closed", lambda _p: self.flush())
        # GTK closes a popover when a drop-down in it is shown for the very first time; so the second
        # alert row and the zone row are shown while the inspector opens and hidden right after.
        self.first_shown = False
        self.connect("map", lambda _p: GLib.idle_add(self.after_first_map))
        self.show_values()

    def calendar_factory(self):
        factory = Gtk.SignalListItemFactory()

        def setup(_factory, item):
            row = Gtk.Box(spacing=7)
            dot = Gtk.DrawingArea(content_width=10, content_height=10, valign=Gtk.Align.CENTER)
            row.append(dot)
            row.append(Gtk.Label(xalign=0))
            item.set_child(row)

        def bind(_factory, item):
            calendar = self.store.calendars[item.get_position()]
            row = item.get_child()
            dot, label = row.get_first_child(), row.get_last_child()
            label.set_label(calendar["name"])
            color = theme.system(calendar["color"])
            dot.set_draw_func(lambda _a, cr, w, h: (cr.set_source_rgb(*color), theme.circle(cr, w / 2, h / 2, 5), cr.fill()))
            dot.queue_draw()
        factory.connect("setup", setup)
        factory.connect("bind", bind)
        return factory

    # ---- showing ----

    def show_values(self):
        self.updating = True
        event = self.event
        all_day = bool(event.get("allDay"))
        self.all_day.set_active(all_day)
        start = rules.parse(event["start"])
        self.start_date.set_text(format_date(start.date() if isinstance(start, datetime) else start))
        self.end_date.set_text(format_date(editing.last_day(event)))
        for field in (self.start_time, self.end_time):
            field.set_visible(not all_day)
        if not all_day:
            self.start_time.set_text(start.strftime("%H:%M"))
            self.end_time.set_text(rules.parse(event["end"]).strftime("%H:%M"))
        rule = self.series.get("rrule")
        index = next((i for i, (value, _label) in enumerate(editing.REPEATS) if (value or None) == (rule or None)), 0)
        self.repeat.set_selected(index)
        ids = [calendar["id"] for calendar in self.store.calendars]
        self.calendar.set_selected(ids.index(event["calendar"]) if event["calendar"] in ids else 0)
        self.notes_hint.set_visible(not event.get("notes"))
        keys = [key for key, _label, _away in editing.ABSENCES]
        self.absence.set_selected(keys.index(event["absence"]) + 1 if event.get("absence") in keys else 0)
        if self.deputy.get_text() != event.get("deputy", ""):
            self.deputy.set_text(event.get("deputy", ""))
        for widget in self.absence_rows:
            widget.set_visible(bool(event.get("absence")))
        tz = event.get("tz")
        for widget in self.zone_row:
            widget.set_visible((not all_day and (bool(tz) or settings.get("timeZones"))) or not self.first_shown)
        self.zone.set_selected(self.zone_names.index(tz) if tz in self.zone_names else 0)
        chosen = event.get("alerts") or []
        options = [(None, "Keiner")] + list(alerts.choices(all_day))
        for minutes in chosen:  # a value from elsewhere keeps its own entry
            if minutes not in [value for value, _label in options]:
                options.append((minutes, alerts.label(minutes, all_day)))
        if options != self.alert_options:
            self.alert_options = options
            for dropdown in self.alert_boxes:
                dropdown.get_model().splice(0, dropdown.get_model().get_n_items(), [label for _value, label in options])
        values = [value for value, _label in options]
        for slot, dropdown in enumerate(self.alert_boxes):
            current = chosen[slot] if slot < len(chosen) else None
            dropdown.set_selected(values.index(current))
        for widget in self.second_alert_row:
            widget.set_visible(bool(chosen) or not self.first_shown)
        self.updating = False

    def after_first_map(self):
        if not self.first_shown:
            self.first_shown = True
            self.show_values()
        return False

    # ---- changing ----

    def change(self, later=False, **fields):
        if self.updating:
            return
        self.event.update(fields)
        if "notes" in fields:
            self.notes_hint.set_visible(not fields["notes"])
        if later:  # typing: write once the hand rests
            if self.save_timer:
                GLib.source_remove(self.save_timer)
            self.save_timer = GLib.timeout_add(400, lambda: self.flush() and False)
        else:
            self.flush()

    def replace(self, event):
        self.event = event
        self.show_values()
        self.flush()

    def flush(self):
        if self.save_timer:
            GLib.source_remove(self.save_timer)
            self.save_timer = None
        if self.series.get("rrule"):
            stored = editing.apply_to_series(self.series, self.occurrence_start, self.event)
            self.occurrence_start = self.event["start"]
        else:
            stored = {key: value for key, value in self.event.items() if key != "key"}
            stored["id"] = self.series["id"]
        if not stored.get("deputy"):
            stored.pop("deputy", None)
        if not stored.get("absence"):
            stored.pop("absence", None)
        if not stored.get("tz"):
            stored.pop("tz", None)
        for key in ("zoneStart", "zoneEnd"):
            stored.pop(key, None)
        if stored != self.series:
            self.series = stored
            self.store.put(stored)
        return False

    def set_all_day(self, on):
        if not self.updating:
            self.replace(editing.set_all_day(self.event, on))

    def set_date(self, which, text):
        day = parse_date(text, rules.parse(self.event["start"]))
        if day is None:
            self.show_values()
            return
        self.replace(editing.set_start(self.event, day) if which == "start" else editing.set_end(self.event, day))

    def set_time(self, which, text):
        parsed = parse_time(text)
        if parsed is None:
            self.show_values()
            return
        base = rules.parse(self.event[which])
        moment = datetime(base.year, base.month, base.day, *parsed)
        self.replace(editing.set_start(self.event, moment) if which == "start" else editing.set_end(self.event, moment))

    def step(self, which, direction, days=False):
        base = rules.parse(self.event[which])
        if self.event.get("allDay") or days:
            if which == "end" and self.event.get("allDay"):
                self.replace(editing.set_end(self.event, editing.last_day(self.event) + timedelta(days=direction)))
                return
            moved = base + timedelta(days=direction)
        else:
            # To the next (or previous) quarter of an hour.
            minutes = base.hour * 60 + base.minute
            minutes = (minutes // 15 + 1) * 15 if direction > 0 else ((minutes - 1) // 15) * 15
            moved = datetime(base.year, base.month, base.day) + timedelta(minutes=minutes)
        self.replace(editing.set_start(self.event, moved) if which == "start" else editing.set_end(self.event, moved))

    def set_repeat(self, index):
        if self.updating:
            return
        rule = editing.REPEATS[index][0]
        if (rule or None) == (self.series.get("rrule") or None):
            return
        # A new repeat rule starts from this occurrence (as the Mac does).
        series = {key: value for key, value in self.event.items() if key != "key"}
        series["id"] = self.series["id"]
        series.pop("exdates", None)
        if rule:
            series["rrule"] = rule
        else:
            series.pop("rrule", None)
        self.series = series
        self.occurrence_start = series["start"]
        self.store.put(series)

    def set_absence(self, index):
        if self.updating:
            return
        kind = None if index == 0 else editing.ABSENCES[index - 1][0]
        if kind == self.event.get("absence"):
            return
        event = editing.set_absence(self.event, kind)
        if kind is None:
            event.pop("absence", None)
            event.pop("deputy", None)
        self.event = event
        self.title.set_text(event.get("title", ""))
        self.show_values()
        self.flush()

    def set_zone(self, index):
        """Another zone keeps the clock time (9:00 stays 9:00 – then in New York), like the Mac."""
        if self.updating or not 0 <= index < len(self.zone_names):
            return
        if self.zone_names[index] != self.event.get("tz"):
            self.change(tz=self.zone_names[index])

    def set_alert(self, slot, index):
        if self.updating or not 0 <= index < len(self.alert_options):
            return
        chosen = list(self.event.get("alerts") or []) + [None] * alerts.MOST
        chosen[slot] = self.alert_options[index][0]
        if slot == 0 and chosen[0] is None:
            chosen = [None]  # without a first alert there is no second (like the Mac)
        if (alerts.set_alerts(self.event, chosen).get("alerts") or []) != (self.event.get("alerts") or []):
            self.replace(alerts.set_alerts(self.event, chosen))

    def set_calendar(self, index):
        if not self.updating and 0 <= index < len(self.store.calendars):
            self.change(calendar=self.store.calendars[index]["id"])


def ask_delete(window, title, on_choice):
    """A repeating event: only this one, or all of them (like the Mac)."""
    dialog = Adw.AlertDialog(heading=_("Termin löschen?"), body=_("„{title}“ wiederholt sich. Nur diesen Termin löschen oder alle?", title=title))
    dialog.add_response("cancel", _("Abbrechen"))
    dialog.add_response("one", _("Nur diesen Termin"))
    dialog.add_response("all", _("Alle Termine"))
    dialog.set_response_appearance("all", Adw.ResponseAppearance.DESTRUCTIVE)
    dialog.set_default_response("one")
    dialog.set_close_response("cancel")
    dialog.connect("response", lambda _d, response: on_choice(response))
    dialog.present(window)
    return dialog
