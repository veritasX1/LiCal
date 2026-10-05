"""Month view like macOS Calendar: weekday names on top, day numbers in the top right corner, today in
a red circle, weekends a shade darker, all-day and multi-day events as tinted bars across the days,
the other events as lines with a color mark and their time; "+N weitere" when a day is full.
Drawn with Cairo (one widget, no child per day – fast also with many events)."""

from datetime import date, datetime, timedelta

import gi

gi.require_version("Gtk", "4.0")
from gi.repository import GObject, Gtk

from . import rules, theme
from .i18n import _

HEADER = 30
ROW = 19
TOP = 30          # space for the day number above the first event row


class MonthView(Gtk.DrawingArea):
    __gsignals__ = {
        "day-selected": (GObject.SignalFlags.RUN_FIRST, None, (object,)),
        "day-activated": (GObject.SignalFlags.RUN_FIRST, None, (object,)),       # double click on a day: new event
        "more-activated": (GObject.SignalFlags.RUN_FIRST, None, (object,)),      # "N weitere": the day view
        "event-activated": (GObject.SignalFlags.RUN_FIRST, None, (object, object)),  # click: selected; occurrence, area (x, y, w, h)
        "event-opened": (GObject.SignalFlags.RUN_FIRST, None, (object, object)),     # double click: the inspector
        "event-moved": (GObject.SignalFlags.RUN_FIRST, None, (object, object, object)),  # dragged to another day: occurrence, days, None
        "month-changed": (GObject.SignalFlags.RUN_FIRST, None, (int,)),         # wheel: ±1
    }

    def __init__(self, store):
        super().__init__(hexpand=True, vexpand=True, focusable=True)
        self.store = store
        self.month = date.today().replace(day=1)
        self.selected_day = date.today()
        self.selected_key = None
        self.selected_item = None
        self.mark_today = True
        self.areas = []
        self.set_draw_func(self.draw)
        click = Gtk.GestureClick()
        click.connect("pressed", self.on_pressed)
        self.add_controller(click)
        drag = Gtk.GestureDrag()
        drag.connect("drag-begin", self.on_drag_begin)
        drag.connect("drag-update", self.on_drag_update)
        drag.connect("drag-end", self.on_drag_end)
        self.add_controller(drag)
        self.drag = None
        scroll = Gtk.EventControllerScroll(flags=Gtk.EventControllerScrollFlags.VERTICAL | Gtk.EventControllerScrollFlags.DISCRETE)
        scroll.connect("scroll", lambda _c, _dx, dy: (self.emit("month-changed", 1 if dy > 0 else -1), True)[1])
        self.add_controller(scroll)

    def show_month(self, month):
        self.month = month.replace(day=1)
        self.queue_draw()

    # ------------------------------------------------------------------

    def draw(self, _area, cr, width, height):
        palette = theme.Palette()
        self.areas = []
        cr.set_source_rgb(*palette.background)
        cr.paint()
        weeks = rules.month_weeks(self.month.year, self.month.month)
        column_width = width / 7
        row_height = (height - HEADER) / len(weeks)
        today = date.today() if self.mark_today else None
        # Weekend columns.
        cr.set_source_rgb(*palette.weekend)
        cr.rectangle(5 * column_width, HEADER, 2 * column_width, height - HEADER)
        cr.fill()
        # Weekday names, right aligned like the day numbers below them.
        for column, name in enumerate(theme.WEEKDAYS_SHORT):
            theme.text(cr, name, (column + 1) * column_width - 9, 7, 13, palette.secondary if column < 5 else palette.tertiary,
                       weight=400, align="right")
        # Grid.
        cr.set_source_rgb(*palette.separator)
        cr.set_line_width(1)
        cr.move_to(0, HEADER - 0.5)
        cr.line_to(width, HEADER - 0.5)
        for row in range(1, len(weeks)):
            y = round(HEADER + row * row_height) - 0.5
            cr.move_to(0, y)
            cr.line_to(width, y)
        for column in range(1, 7):
            x = round(column * column_width) - 0.5
            cr.move_to(x, HEADER)
            cr.line_to(x, height)
        cr.stroke()

        first, last = weeks[0][0], weeks[-1][-1]
        items = self.store.occurrences(first, last + timedelta(days=1))
        by_key = {item["key"]: item for item in items}
        capacity = max(1, int((row_height - TOP - 4) // ROW))
        for row, week in enumerate(weeks):
            top = HEADER + row * row_height
            layout = rules.week_layout(items, week)
            shown_lanes = [rules.cell_rows(layout, column, capacity) for column in range(7)]
            for column, day in enumerate(week):
                self.draw_day_number(cr, palette, day, today, (column + 1) * column_width, top)
                self.areas.append(("day", day, column * column_width, top, column_width, row_height))
            # Bars (all-day, multi-day) – one per run of days where its lane is shown.
            for bar in layout["bars"]:
                item = by_key[bar["key"]]
                columns = [column for column in range(bar["first"], bar["last"] + 1) if bar["lane"] < shown_lanes[column][0]]
                for start, end in runs(columns):
                    x = start * column_width + 4
                    w = (end - start + 1) * column_width - 8
                    y = top + TOP + bar["lane"] * ROW
                    self.draw_bar(cr, palette, item, x, y, w, starts=start == bar["first"] and day_starts(item, week[start]))
            # Lines (timed single-day events) and "+N".
            for column in range(7):
                lanes, keys, hidden = shown_lanes[column]
                x = column * column_width + 4
                y = top + TOP + lanes * ROW
                for key in keys:
                    self.draw_line(cr, palette, by_key[key], x, y, column_width - 8)
                    y += ROW
                if hidden:
                    theme.text(cr, _("{hidden} weitere", hidden=hidden), x + 7, y + 2, 11, palette.secondary, weight=500)
                    self.areas.append(("more", week[column], x, y, column_width - 8, ROW))
        if self.drag and self.drag.get("active"):
            self.draw_drag(cr, palette)

    def draw_day_number(self, cr, palette, day, today, right, top):
        # The first of each month carries the month's name, like on the Mac ("1. Nov.").
        label = f"1. {short_month(day.month)}" if day.day == 1 else str(day.day)
        in_month = day.month == self.month.month
        if day == today:
            width = max(22, theme.text_width(cr, label, 13, 600) + 12)
            cr.set_source_rgb(*palette.red)
            theme.rounded(cr, right - 6 - width, top + 5, width, 22, 11)
            cr.fill()
            theme.text(cr, label, right - 6 - width / 2, top + 7.5, 13, palette.today_text, weight=600, align="center")
            return
        color = palette.label if in_month else palette.tertiary
        theme.text(cr, label, right - 9, top + 7.5, 13, color, weight=500 if in_month else 400, align="right")

    def draw_bar(self, cr, palette, item, x, y, width, starts=True):
        color = theme.system(self.store.calendar(item["calendar"])["color"] if self.store.calendar(item["calendar"]) else "blue")
        selected = item["key"] == self.selected_key
        cr.set_source_rgba(*palette.event_fill(color, selected))
        theme.rounded(cr, x, y + 1, width, ROW - 2, 4)
        cr.fill()
        if item.get("absence") and not selected:
            theme.hatch(cr, x, y + 1, width, ROW - 2, color, 0.3)
        label = item.get("title", "")
        if not item.get("allDay") and starts:
            label = rules.parse(item["start"]).strftime("%H:%M") + "  " + label
        theme.text(cr, label, x + 6, y + 2.5, 11.5, palette.event_text(color, selected), weight=600, width=width - 10)
        self.areas.append(("event", item, x, y, width, ROW))

    def draw_line(self, cr, palette, item, x, y, width):
        calendar = self.store.calendar(item["calendar"])
        color = theme.system(calendar["color"] if calendar else "blue")
        selected = item["key"] == self.selected_key
        if selected:
            cr.set_source_rgb(*color)
            theme.rounded(cr, x, y + 1, width, ROW - 2, 4)
            cr.fill()
        cr.set_source_rgb(*((1, 1, 1) if selected else color))
        theme.rounded(cr, x + 2, y + 4, 3, ROW - 8, 1.5)
        cr.fill()
        time = rules.parse(item["start"]).strftime("%H:%M")
        time_width = theme.text_width(cr, time, 10.5, 400) + 4
        text_color = (1, 1, 1) if selected else palette.label
        theme.text(cr, item.get("title", ""), x + 9, y + 2.5, 11.5, text_color, weight=500, width=width - 14 - time_width)
        theme.text(cr, time, x + width - 3, y + 3.5, 10.5, (1, 1, 1, 0.85) if selected else palette.secondary, align="right", tabular=True)
        self.areas.append(("event", item, x, y, width, ROW))

    # ------------------------------------------------------------------

    # ---- dragging an event to another day (like the Mac) ----

    def on_drag_begin(self, gesture, x, y):
        area = self.hit(x, y)
        self.drag = {"item": area[1], "x": x, "y": y, "dx": 0, "dy": 0, "active": False} if area and area[0] == "event" else None

    def on_drag_update(self, gesture, dx, dy):
        if not self.drag:
            return
        self.drag.update(dx=dx, dy=dy)
        if not self.drag["active"] and (abs(dx) > 5 or abs(dy) > 5):
            self.drag["active"] = True
            gesture.set_state(Gtk.EventSequenceState.CLAIMED)
        if self.drag["active"]:
            self.queue_draw()

    def drag_target(self):
        drag = self.drag
        target = next((a for a in self.areas if a[0] == "day" and a[2] <= drag["x"] + drag["dx"] < a[2] + a[4]
                       and a[3] <= drag["y"] + drag["dy"] < a[3] + a[5]), None)
        return target[1] if target else None

    def on_drag_end(self, gesture, dx, dy):
        drag = self.drag
        if not drag or not drag["active"]:
            self.drag = None
            return
        target = self.drag_target()
        self.drag = None
        self.queue_draw()
        if target is not None:
            days = (target - rules.days_covered(drag["item"])[0]).days
            if days:
                self.emit("event-moved", drag["item"], days, None)

    def draw_drag(self, cr, palette):
        target = self.drag_target()
        if target is None:
            return
        area = next(a for a in self.areas if a[0] == "day" and a[1] == target)
        calendar = self.store.calendar(self.drag["item"]["calendar"])
        color = theme.system(calendar["color"] if calendar else "blue")
        cr.set_source_rgba(*color, 0.18)
        cr.rectangle(area[2] + 1, area[3] + 1, area[4] - 2, area[5] - 2)
        cr.fill()
        theme.text(cr, self.drag["item"].get("title", ""), area[2] + 8, area[3] + area[5] - 22, 11.5, palette.label, weight=600, width=area[4] - 16)

    def area_of(self, key):
        """Where an occurrence is drawn (for the inspector's arrow), or None."""
        return next(((ax, ay, aw, ah) for kind, payload, ax, ay, aw, ah in self.areas if kind == "event" and payload["key"] == key), None)

    def hit(self, x, y):
        for area in reversed(self.areas):
            kind, payload, ax, ay, aw, ah = area
            if ax <= x < ax + aw and ay <= y < ay + ah:
                return area
        return None

    def on_pressed(self, gesture, presses, x, y):
        self.grab_focus()
        area = self.hit(x, y)
        if area is None:
            return
        kind, payload, ax, ay, aw, ah = area
        if kind == "event":
            self.selected_key = payload["key"]
            self.selected_item = payload
            self.selected_day = rules.days_covered(payload)[0]
            self.queue_draw()
            self.emit("event-opened" if presses == 2 else "event-activated", payload, (ax, ay, aw, ah))
            return
        if kind == "more":
            self.emit("more-activated", payload)
            return
        day = payload
        self.selected_key = None
        self.selected_item = None
        self.selected_day = day
        self.queue_draw()
        self.emit("day-selected", day)
        if presses == 2:
            self.emit("day-activated", day)


def runs(columns):
    """[2, 3, 4, 6] → [(2, 4), (6, 6)]"""
    result = []
    for column in columns:
        if result and result[-1][1] == column - 1:
            result[-1] = (result[-1][0], column)
        else:
            result.append((column, column))
    return result


def day_starts(item, day):
    start = rules.parse(item["start"])
    return (start.date() if isinstance(start, datetime) else start) == day


def short_month(month):
    name = theme.MONTHS[month - 1]
    return name if len(name) <= 4 else name[:3] + "."
