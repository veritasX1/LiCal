"""Year view like macOS Calendar: twelve small months in a 4×3 grid, month names in red, days of the
neighbouring months faint, today in a red circle, days with events marked with a small dot.
A click on a day opens it in the day view, on a month name the month view."""

from datetime import date, timedelta

import gi

gi.require_version("Gtk", "4.0")
from gi.repository import GObject, Gtk

from . import rules, theme


class YearView(Gtk.DrawingArea):
    __gsignals__ = {
        "day-activated": (GObject.SignalFlags.RUN_FIRST, None, (object,)),
        "month-activated": (GObject.SignalFlags.RUN_FIRST, None, (object,)),
        "year-changed": (GObject.SignalFlags.RUN_FIRST, None, (int,)),
    }

    def __init__(self, store):
        super().__init__(hexpand=True, vexpand=True)
        self.store = store
        self.year = date.today().year
        self.areas = []
        self.set_draw_func(self.draw)
        click = Gtk.GestureClick()
        click.connect("pressed", self.on_pressed)
        self.add_controller(click)
        scroll = Gtk.EventControllerScroll(flags=Gtk.EventControllerScrollFlags.VERTICAL | Gtk.EventControllerScrollFlags.DISCRETE)
        scroll.connect("scroll", lambda _c, _dx, dy: (self.emit("year-changed", 1 if dy > 0 else -1), True)[1])
        self.add_controller(scroll)

    def show_year(self, year):
        self.year = year
        self.queue_draw()

    def draw(self, _area, cr, width, height):
        palette = theme.Palette()
        self.areas = []
        cr.set_source_rgb(*palette.background)
        cr.paint()
        busy = set()
        for item in self.store.occurrences(date(self.year, 1, 1), date(self.year + 1, 1, 1)):
            first, last = rules.days_covered(item)
            day = first
            while day <= last:
                busy.add(day)
                day += timedelta(days=1)
        columns, rows = (4, 3) if width >= 760 else (3, 4)
        cell_width = (width - 40) / columns
        cell_height = (height - 10) / rows
        today = date.today()
        for index in range(12):
            month = index + 1
            left = 20 + (index % columns) * cell_width + 12
            top = 4 + (index // columns) * cell_height
            inner = cell_width - 30
            column = inner / 7
            size = max(10.0, min(12.5, column * 0.42))
            theme.text(cr, theme.MONTHS[index], left, top, 17, palette.red, weight=500)
            self.areas.append(("month", date(self.year, month, 1), left, top, inner, 26))
            for weekday, letter in enumerate(theme.WEEKDAYS_LETTER):
                theme.text(cr, letter, left + weekday * column + column / 2, top + 32, size - 1.5, palette.secondary, align="center")
            row_height = min(22.0, (cell_height - 64) / 6)
            for row, week in enumerate(rules.month_weeks(self.year, month)):
                for weekday, day in enumerate(week):
                    cx = left + weekday * column + column / 2
                    cy = top + 58 + row * row_height
                    inside = day.month == month
                    color = palette.label if inside else palette.tertiary + (0.5,)
                    weight = 500
                    if inside and day == today:
                        cr.set_source_rgb(*palette.red)
                        theme.circle(cr, cx, cy, min(10.5, row_height / 2))
                        cr.fill()
                        color, weight = palette.today_text, 600
                    elif not inside:
                        weight = 400
                    theme.text(cr, str(day.day), cx, cy - size * 0.62, size, color, weight=weight, align="center", tabular=True)
                    if inside and day in busy and day != today:
                        cr.set_source_rgb(*palette.secondary)
                        theme.circle(cr, cx, cy + size * 0.72, 1.4)
                        cr.fill()
                    if inside:
                        self.areas.append(("day", day, cx - column / 2, cy - row_height / 2, column, row_height))

    def on_pressed(self, _gesture, _presses, x, y):
        for kind, payload, ax, ay, aw, ah in reversed(self.areas):
            if ax <= x < ax + aw and ay <= y < ay + ah:
                self.emit("day-activated" if kind == "day" else "month-activated", payload)
                return
