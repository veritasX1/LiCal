"""The sidebar like macOS Calendar: the calendars with colored check boxes (click: show/hide), and a
small month at the bottom to jump to a day."""

from datetime import date, timedelta

import gi

gi.require_version("Gtk", "4.0")
from gi.repository import GLib, GObject, Gtk

from . import holidays, rules, theme


class CalendarCheck(Gtk.DrawingArea):
    """A rounded check box in the calendar's color (filled with a white tick when shown)."""

    def __init__(self, color_name, active):
        super().__init__(content_width=16, content_height=16, valign=Gtk.Align.CENTER)
        self.color_name = color_name
        self.active = active
        self.set_draw_func(self.draw)

    def draw(self, _area, cr, width, height):
        color = theme.system(self.color_name)
        theme.rounded(cr, 1, 1, 14, 14, 3.5)
        if self.active:
            cr.set_source_rgb(*color)
            cr.fill()
            cr.set_source_rgb(1, 1, 1)
            cr.set_line_width(1.8)
            cr.set_line_cap(1)
            cr.set_line_join(1)
            cr.move_to(4.3, 8.2)
            cr.line_to(6.9, 10.8)
            cr.line_to(11.7, 5.2)
            cr.stroke()
        else:
            cr.set_source_rgb(*color)
            cr.set_line_width(1.5)
            theme.rounded(cr, 1.75, 1.75, 12.5, 12.5, 3)
            cr.stroke()


class MiniMonth(Gtk.DrawingArea):
    """The small month at the bottom of the sidebar: ‹ Oktober 2026 ›, weekday letters, days."""
    __gsignals__ = {"day-selected": (GObject.SignalFlags.RUN_FIRST, None, (object,))}

    def __init__(self):
        super().__init__(content_height=196)
        self.month = date.today().replace(day=1)
        self.selected = date.today()
        self.areas = []
        self.set_draw_func(self.draw)
        click = Gtk.GestureClick()
        click.connect("pressed", self.on_pressed)
        self.add_controller(click)

    def show(self, day):
        self.selected = day
        self.month = day.replace(day=1)
        self.queue_draw()

    def draw(self, _area, cr, width, height):
        palette = theme.Palette()
        self.areas = []
        theme.text(cr, f"{theme.MONTHS[self.month.month - 1]} {self.month.year}", width / 2, 4, 13, palette.label, weight=600, align="center")
        for direction, x in ((-1, 14), (1, width - 14)):
            cr.set_source_rgb(*palette.secondary)
            cr.set_line_width(1.6)
            cr.set_line_cap(1)
            cr.move_to(x + 2.5 * direction * -1, 8)
            cr.line_to(x + 2.5 * direction, 12.5)
            cr.line_to(x + 2.5 * direction * -1, 17)
            cr.stroke()
            self.areas.append(("month", direction, x - 12, 0, 24, 24))
        column = (width - 8) / 7
        for index, letter in enumerate(theme.WEEKDAYS_LETTER):
            theme.text(cr, letter, 4 + column * index + column / 2, 30, 10.5, palette.secondary, weight=500, align="center")
        today = date.today()
        for row, week in enumerate(rules.month_weeks(self.month.year, self.month.month)):
            for index, day in enumerate(week):
                cx = 4 + column * index + column / 2
                cy = 56 + row * 23
                color = palette.label if day.month == self.month.month else palette.tertiary
                weight = 500
                if day == today:
                    cr.set_source_rgb(*palette.red)
                    theme.circle(cr, cx, cy, 10)
                    cr.fill()
                    color, weight = palette.today_text, 600
                elif day == self.selected:
                    cr.set_source_rgba(*palette.label, 0.12)
                    theme.circle(cr, cx, cy, 10)
                    cr.fill()
                theme.text(cr, str(day.day), cx, cy - 7.5, 11.5, color, weight=weight, align="center", tabular=True)
                self.areas.append(("day", day, cx - column / 2, cy - 11, column, 22))

    def on_pressed(self, _gesture, _presses, x, y):
        for kind, payload, ax, ay, aw, ah in self.areas:
            if ax <= x < ax + aw and ay <= y < ay + ah:
                if kind == "month":
                    self.month = (self.month + timedelta(days=32 * payload)).replace(day=1) if payload > 0 else (self.month - timedelta(days=1)).replace(day=1)
                    self.queue_draw()
                else:
                    self.selected = payload
                    self.queue_draw()
                    self.emit("day-selected", payload)
                return


class Sidebar(Gtk.Box):
    __gsignals__ = {"day-selected": (GObject.SignalFlags.RUN_FIRST, None, (object,))}

    def __init__(self, store):
        super().__init__(orientation=Gtk.Orientation.VERTICAL, css_classes=["lical-sidebar"], width_request=228, hexpand=False)
        self.store = store
        self.list = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=1, vexpand=True)
        scroller = Gtk.ScrolledWindow(child=self.list, vexpand=True, hscrollbar_policy=Gtk.PolicyType.NEVER)
        self.append(scroller)
        self.mini = MiniMonth()
        self.mini.set_margin_start(10)
        self.mini.set_margin_end(10)
        self.mini.set_margin_bottom(10)
        self.mini.connect("day-selected", lambda _m, day: self.emit("day-selected", day))
        self.append(self.mini)
        self.refresh()

    def refresh(self):
        child = self.list.get_first_child()
        while child is not None:
            following = child.get_next_sibling()
            self.list.remove(child)
            child = following
        heading = Gtk.Label(label="Auf diesem Computer", xalign=0, css_classes=["lical-sidebar-heading"])
        self.list.append(heading)
        for calendar in self.store.calendars:
            self.list.append(self.calendar_row(calendar))
        add = Gtk.Button(label="Neuer Kalender", css_classes=["flat", "lical-sidebar-add"], halign=Gtk.Align.START, margin_start=10, margin_top=4)
        add.connect("clicked", lambda _b: self.new_calendar(add))
        self.list.append(add)
        # Computed holidays (card 7a9187d6): like a subscribed calendar on the Mac, under "Andere".
        self.list.append(Gtk.Label(label="Andere", xalign=0, css_classes=["lical-sidebar-heading"], margin_top=10))
        self.list.append(self.holiday_row())

    def holiday_row(self):
        info = self.store.calendar(holidays.CALENDAR)
        row = Gtk.Box(spacing=8, css_classes=["lical-sidebar-row"])
        check = CalendarCheck(info["color"], info.get("visible", True))
        row.append(check)
        state = dict(holidays.STATES).get(info.get("state", ""), "")
        row.append(Gtk.Label(label=info["name"], xalign=0, hexpand=True, ellipsize=3,
                             tooltip_text=f"Gesetzliche Feiertage – {state if info.get('state') else 'bundesweit'}"))
        click = Gtk.GestureClick()

        def toggle(*_args):
            check.active = not check.active
            check.queue_draw()
            self.store.set_holidays(visible=check.active)
        click.connect("released", toggle)
        row.add_controller(click)
        menu = Gtk.GestureClick(button=3)
        menu.connect("pressed", lambda gesture, *_a: (gesture.set_state(Gtk.EventSequenceState.CLAIMED), self.holiday_menu(row)))
        row.add_controller(menu)
        self.holiday_check = check
        return row

    def holiday_menu(self, row):
        """Right click on "Feiertage": the Bundesland and the color."""
        info = self.store.calendar(holidays.CALENDAR)
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_start=10, margin_end=10, margin_top=10, margin_bottom=10)
        box.append(Gtk.Label(label="Bundesland", xalign=0, css_classes=["dim-label"]))
        keys = [key for key, _name in holidays.STATES]
        states = Gtk.DropDown.new_from_strings([name for _key, name in holidays.STATES])
        states.set_selected(keys.index(info.get("state", "")) if info.get("state", "") in keys else 0)
        states.connect("notify::selected", lambda dropdown, _p: self.store.set_holidays(state=keys[dropdown.get_selected()]))
        box.append(states)
        popover = self.popover(row, box)
        box.append(self.colors_row(info["color"], lambda color: (self.store.set_holidays(color=color), popover.popdown())))
        self.holiday_states = states

    def calendar_row(self, calendar):
        row = Gtk.Box(spacing=8, css_classes=["lical-sidebar-row"])
        check = CalendarCheck(calendar["color"], calendar.get("visible", True))
        row.append(check)
        row.append(Gtk.Label(label=calendar["name"], xalign=0, hexpand=True, ellipsize=3))
        click = Gtk.GestureClick()

        def toggle(*_args):
            check.active = not check.active
            check.queue_draw()
            self.store.set_visible(calendar["id"], check.active)
        click.connect("released", toggle)
        row.add_controller(click)
        # Right click: rename, color, delete (like the Mac's sidebar).
        menu = Gtk.GestureClick(button=3)
        menu.connect("pressed", lambda gesture, *_a: (gesture.set_state(Gtk.EventSequenceState.CLAIMED), self.calendar_menu(row, calendar)))
        row.add_controller(menu)
        row.set_tooltip_text("Ein- oder ausblenden · Rechtsklick: Name, Farbe, Löschen")
        return row

    # ---- managing calendars ----

    def popover(self, parent, child):
        popover = Gtk.Popover(child=child)
        popover.set_parent(parent)
        popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
        popover.popup()
        return popover

    def colors_row(self, chosen, on_pick):
        row = Gtk.Box(spacing=6)
        for name in theme.CALENDAR_COLORS:
            dot = Gtk.DrawingArea(content_width=20, content_height=20)
            color = theme.system(name)

            def draw(_a, cr, w, h, color=color, name=name):
                cr.set_source_rgb(*color)
                theme.circle(cr, w / 2, h / 2, 8)
                cr.fill()
                if name == chosen:
                    cr.set_source_rgb(1, 1, 1)
                    theme.circle(cr, w / 2, h / 2, 3)
                    cr.fill()
            dot.set_draw_func(draw)
            dot.set_cursor_from_name("pointer")
            dot.set_tooltip_text(name)
            click = Gtk.GestureClick()
            click.connect("released", lambda *_a, name=name: on_pick(name))
            dot.add_controller(click)
            row.append(dot)
        return row

    def calendar_menu(self, row, calendar):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_start=10, margin_end=10, margin_top=10, margin_bottom=10)
        name = Gtk.Entry(text=calendar["name"])
        box.append(name)
        box.append(self.colors_row(calendar["color"], lambda color: (self.store.update_calendar(calendar["id"], color=color), popover.popdown())))
        delete = Gtk.Button(label="Kalender löschen …", css_classes=["flat", "lical-delete"], halign=Gtk.Align.START)
        box.append(delete)
        popover = self.popover(row, box)
        name.connect("activate", lambda entry: (self.store.update_calendar(calendar["id"], name=entry.get_text().strip() or calendar["name"]), popover.popdown()))
        popover.connect("closed", lambda _p: self.store.update_calendar(calendar["id"], name=name.get_text().strip() or calendar["name"])
                        if name.get_text().strip() != calendar["name"] else None)
        delete.connect("clicked", lambda _b: (popover.popdown(), self.ask_delete_calendar(calendar)))

    def ask_delete_calendar(self, calendar):
        from gi.repository import Adw
        count = sum(1 for event in self.store.events if event.get("calendar") == calendar["id"])
        if len(self.store.calendars) <= 1:
            return
        dialog = Adw.AlertDialog(heading=f"„{calendar['name']}“ löschen?",
                                 body=f"Der Kalender und seine {count} Termine werden gelöscht." if count else "Der Kalender ist leer.")
        dialog.add_response("cancel", "Abbrechen")
        dialog.add_response("delete", "Löschen")
        dialog.set_response_appearance("delete", Adw.ResponseAppearance.DESTRUCTIVE)
        dialog.set_close_response("cancel")
        dialog.connect("response", lambda _d, response: self.store.delete_calendar(calendar["id"]) if response == "delete" else None)
        dialog.present(self.get_root())
        self.delete_dialog = dialog

    def new_calendar(self, anchor):
        used = {calendar["color"] for calendar in self.store.calendars}
        color = next((name for name in theme.CALENDAR_COLORS if name not in used), "blue")
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8, margin_start=10, margin_end=10, margin_top=10, margin_bottom=10)
        name = Gtk.Entry(placeholder_text="Name des Kalenders")
        box.append(name)
        chosen = {"color": color}
        holder = Gtk.Box()
        holder.append(self.colors_row(color, lambda pick: (chosen.update(color=pick), holder.remove(holder.get_first_child()),
                                                           holder.append(self.colors_row(pick, lambda p: chosen.update(color=p))))))
        box.append(holder)
        popover = self.popover(anchor, box)
        name.connect("activate", lambda entry: (self.store.add_calendar(entry.get_text().strip(), chosen["color"]), popover.popdown())
                     if entry.get_text().strip() else None)
        name.grab_focus()
