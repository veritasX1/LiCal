"""Week and day view like macOS Calendar: the days on top (today's number in a red circle), all-day
events in a band below them, then the hours – events as tinted blocks with a colored edge, side by side
when they overlap, a red line with the time for "now". Header and hours are drawn with Cairo; the
hours scroll, the header stays."""

from datetime import date, datetime, timedelta

import gi

gi.require_version("Gtk", "4.0")
from gi.repository import GLib, GObject, Gtk

from . import rules, theme
from .i18n import _

GUTTER = 58
HOUR = 50
ALL_DAY_ROW = 20


class TimelineView(Gtk.Box):
    __gsignals__ = {
        "day-selected": (GObject.SignalFlags.RUN_FIRST, None, (object,)),
        "event-activated": (GObject.SignalFlags.RUN_FIRST, None, (object, object)),
        "event-opened": (GObject.SignalFlags.RUN_FIRST, None, (object, object)),
        "slot-activated": (GObject.SignalFlags.RUN_FIRST, None, (object,)),  # double click on an empty hour: datetime
        "range-selected": (GObject.SignalFlags.RUN_FIRST, None, (object, object)),  # dragged over free time: start, end
        "event-moved": (GObject.SignalFlags.RUN_FIRST, None, (object, object, object)),  # occurrence, new start, new end
    }

    def __init__(self, store, days=7):
        super().__init__(orientation=Gtk.Orientation.VERTICAL, hexpand=True, vexpand=True)
        self.store = store
        self.days = days
        self.first = date.today()
        self.selected_key = None
        self.selected_item = None
        self.header_areas = []
        self.body_areas = []
        self.header = Gtk.DrawingArea(hexpand=True)
        self.header.set_draw_func(self.draw_header)
        self.append(self.header)
        self.body = Gtk.DrawingArea(hexpand=True, content_height=24 * HOUR + 12)
        self.body.set_draw_func(self.draw_body)
        self.scroller = Gtk.ScrolledWindow(child=self.body, vexpand=True, hscrollbar_policy=Gtk.PolicyType.NEVER)
        self.append(self.scroller)
        for area, handler in ((self.header, self.on_header_pressed), (self.body, self.on_body_pressed)):
            click = Gtk.GestureClick()
            click.connect("pressed", handler)
            area.add_controller(click)
        self.scrolled_once = False
        self.drag = None
        drag = Gtk.GestureDrag()
        drag.connect("drag-begin", self.on_drag_begin)
        drag.connect("drag-update", self.on_drag_update)
        drag.connect("drag-end", self.on_drag_end)
        self.body.add_controller(drag)
        motion = Gtk.EventControllerMotion()
        motion.connect("motion", self.on_motion)
        self.body.add_controller(motion)
        GLib.timeout_add_seconds(60, self.tick)

    def tick(self):
        self.body.queue_draw()  # the "now" line moves on
        return True

    def show_day(self, day):
        self.first = rules.week_start(day) if self.days == 7 else day
        self.items = self.store.occurrences(self.first, self.first + timedelta(days=self.days))
        lanes = self.all_day_lanes()
        self.header.set_content_height(self.band_top() + max(1, lanes) * ALL_DAY_ROW + 8)
        self.header.queue_draw()
        self.body.queue_draw()
        if not self.scrolled_once:
            self.scrolled_once = True
            GLib.timeout_add(30, self.scroll_to_morning)

    def scroll_to_morning(self):
        """Like the Mac: open at the morning, the first event of the days in view.
        Waits until the hours have their size – before that the scroll position cannot be set."""
        adjustment = self.scroller.get_vadjustment()
        if adjustment.get_upper() <= adjustment.get_page_size() + 1 or adjustment.get_page_size() <= 1:
            return True
        # The earlier of: half an hour before the first event, 7:30 – and on a day with "now", now.
        hour = 7.5
        for day in self.day_list():
            for block in rules.timeline_layout(self.items, day):
                hour = min(hour, max(0.0, block["top"] / 60 - 0.5))
        now = datetime.now()
        if self.first <= now.date() < self.first + timedelta(days=self.days):
            hour = min(hour, max(0.0, now.hour - 1.0))
        adjustment.set_value(min(hour * HOUR, adjustment.get_upper() - adjustment.get_page_size()))
        return False

    def band_top(self):
        """Where the all-day band starts: below the day names (week), at the top (day – the title names it)."""
        return 50 if self.days == 7 else 6

    def columns(self, width):
        column = (width - GUTTER) / self.days
        return [(GUTTER + index * column, column) for index in range(self.days)]

    def day_list(self):
        return [self.first + timedelta(days=index) for index in range(self.days)]

    def all_day_items(self):
        return [item for item in self.items if item.get("allDay") or rules.days_covered(item)[1] > rules.days_covered(item)[0]]

    def all_day_lanes(self):
        days = self.day_list()
        if self.days == 7:
            layout = rules.week_layout(self.all_day_items(), days)
            return max(layout["lanes"]) if layout["bars"] else 0
        return len([item for item in self.all_day_items() if rules.days_covered(item)[0] <= self.first <= rules.days_covered(item)[1]])

    # ------------------------------------------------------------------

    def draw_header(self, _area, cr, width, height):
        palette = theme.Palette()
        self.header_areas = []
        cr.set_source_rgb(*palette.background)
        cr.paint()
        today = date.today()
        for (x, column), day in zip(self.columns(width), self.day_list() if self.days == 7 else []):
            weekend = day.weekday() >= 5
            if self.days == 7 and weekend:
                cr.set_source_rgb(*palette.weekend)
                cr.rectangle(x, 0, column, height)
                cr.fill()
            # "Mo 5": weekday, then the number (today: white in a red circle).
            name = theme.WEEKDAYS_SHORT[day.weekday()] if self.days == 7 else theme.WEEKDAYS_LONG[day.weekday()]
            number = str(day.day)
            name_width = theme.text_width(cr, name, 13, 400)
            number_width = theme.text_width(cr, number, 13, 600)
            right = x + column - 10 if self.days == 7 else x + 8 + name_width + 8 + number_width + 8
            if day == today:
                width_circle = max(24, number_width + 12)
                cr.set_source_rgb(*palette.red)
                theme.rounded(cr, right - width_circle + 4, 12, width_circle, 24, 12)
                cr.fill()
                theme.text(cr, number, right - width_circle / 2 + 4, 15.5, 13, palette.today_text, weight=600, align="center")
                theme.text(cr, name, right - width_circle - 2, 15.5, 13, palette.red, align="right")
            else:
                color = palette.secondary if weekend else palette.label
                theme.text(cr, number, right, 15.5, 13, color, weight=600, align="right")
                theme.text(cr, name, right - number_width - 6, 15.5, 13, palette.secondary, align="right")
            self.header_areas.append(("day", day, x, 0, column, 44))
        theme.text(cr, _("ganztägig"), GUTTER - 8, self.band_top() + 4, 10.5, palette.secondary, align="right")
        # All-day band.
        cr.set_source_rgb(*palette.separator)
        cr.set_line_width(1)
        cr.move_to(0, height - 0.5)
        cr.line_to(width, height - 0.5)
        cr.stroke()
        columns = self.columns(width)
        if self.days == 7:
            layout = rules.week_layout(self.all_day_items(), self.day_list())
            by_key = {item["key"]: item for item in self.items}
            for bar in layout["bars"]:
                x = columns[bar["first"]][0] + 2
                w = columns[bar["last"]][0] + columns[bar["last"]][1] - x - 2
                self.draw_all_day(cr, palette, by_key[bar["key"]], x, self.band_top() + bar["lane"] * ALL_DAY_ROW, w)
        else:
            row = 0
            for item in self.all_day_items():
                start, end = rules.days_covered(item)
                if start <= self.first <= end:
                    self.draw_all_day(cr, palette, item, GUTTER + 2, self.band_top() + row * ALL_DAY_ROW, width - GUTTER - 8)
                    row += 1

    def draw_all_day(self, cr, palette, item, x, y, width):
        color = self.color(item)
        selected = item["key"] == self.selected_key
        cr.set_source_rgba(*palette.event_fill(color, selected))
        theme.rounded(cr, x, y + 1, width, ALL_DAY_ROW - 3, 4)
        cr.fill()
        if item.get("absence") and not selected:
            theme.hatch(cr, x, y + 1, width, ALL_DAY_ROW - 3, color, 0.3)
        theme.text(cr, item.get("title", ""), x + 6, y + 2.5, 11.5, palette.event_text(color, selected), weight=600, width=width - 10)
        self.header_areas.append(("event", item, x, y, width, ALL_DAY_ROW))

    def draw_body(self, _area, cr, width, height):
        palette = theme.Palette()
        self.body_areas = []
        cr.set_source_rgb(*palette.background)
        cr.paint()
        columns = self.columns(width)
        days = self.day_list()
        if self.days == 7:
            for (x, column), day in zip(columns, days):
                if day.weekday() >= 5:
                    cr.set_source_rgb(*palette.weekend)
                    cr.rectangle(x, 0, column, height)
                    cr.fill()
        now = datetime.now()
        cr.set_line_width(1)
        for hour in range(1, 24):
            y = 6 + hour * HOUR
            cr.set_source_rgb(*palette.separator)
            cr.move_to(GUTTER - 4, round(y) - 0.5)
            cr.line_to(width, round(y) - 0.5)
            cr.stroke()
            if not (days[0] <= now.date() <= days[-1] and abs(now.hour * 60 + now.minute - hour * 60) < 14):
                theme.text(cr, f"{hour:02d}:00", GUTTER - 10, y - 7, 10.5, palette.secondary, align="right", tabular=True)
        for x, _column in columns:
            cr.set_source_rgb(*palette.separator)
            cr.move_to(round(x) - 0.5, 0)
            cr.line_to(round(x) - 0.5, height)
            cr.stroke()
        for (x, column), day in zip(columns, days):
            # The day first: hit tests go from the last entry back, so events win over their day.
            self.body_areas.append(("day", day, x, 0, column, height))
            for block in rules.timeline_layout(self.items, day):
                item = next(item for item in self.items if item["key"] == block["key"])
                self.draw_block(cr, palette, item, block, x, column)
        if self.drag and self.drag.get("active"):
            self.draw_drag(cr, palette, columns)
        # Now: a red line across today with the time in the gutter.
        if days[0] <= now.date() <= days[-1]:
            y = 6 + (now.hour * 60 + now.minute) / 60 * HOUR
            index = (now.date() - days[0]).days
            x, column = columns[index]
            cr.set_source_rgb(*palette.red)
            theme.text(cr, now.strftime("%H:%M"), GUTTER - 10, y - 7, 10.5, palette.red, weight=600, align="right", tabular=True)
            cr.set_line_width(1.5)
            cr.move_to(GUTTER - 2, y)
            cr.line_to(x, y)
            cr.stroke()
            cr.set_line_width(2)
            cr.move_to(x, y)
            cr.line_to(x + column, y)
            cr.stroke()
            theme.circle(cr, x, y, 4)
            cr.fill()

    def draw_block(self, cr, palette, item, block, x, column):
        color = self.color(item)
        selected = item["key"] == self.selected_key
        gap = 2
        left = x + 2 + block["column"] * (column - 4) / block["columns"]
        width = (column - 4) / block["columns"] - gap
        top = 6 + block["top"] / 60 * HOUR + 1
        height = max(14, (block["bottom"] - block["top"]) / 60 * HOUR - 2)
        cr.set_source_rgba(*palette.event_fill(color, selected))
        theme.rounded(cr, left, top, width, height, 5)
        cr.fill()
        cr.set_source_rgb(*((1, 1, 1) if selected else color))
        theme.rounded(cr, left, top, 3.5, height, 1.75)
        cr.fill()
        text_color = palette.event_text(color, selected)
        cr.save()
        cr.rectangle(left, top, width, height)
        cr.clip()
        theme.text(cr, item.get("title", ""), left + 8, top + 3, 12, text_color, weight=600, width=width - 12)
        start = rules.parse(item["start"])
        lines = [start.strftime("%H:%M")]
        if item.get("location"):
            lines.insert(0, item["location"])
        if height > 34:
            theme.text(cr, "  ·  ".join(lines) if width > 170 else lines[-1], left + 8, top + 19, 11, text_color + (0.8,), width=width - 12)
        cr.restore()
        self.body_areas.append(("event", item, left, top, width, height))

    # ---- dragging: select a time range, move an event, pull its end (15-minute steps) ----

    def moment_at(self, x, y, width):
        """(day, minutes from midnight, snapped to 15) under a point of the hours."""
        columns = self.columns(width)
        index = max(0, min(self.days - 1, int((x - GUTTER) // columns[0][1]))) if x >= GUTTER else 0
        minutes = int(round(max(0, min(24 * 60, (y - 6) / HOUR * 60)) / 15) * 15)
        return self.day_list()[index], minutes

    def on_motion(self, _controller, x, y):
        area = self.find(self.body_areas, x, y)
        edge = area and area[0] == "event" and y > area[3] + area[5] - 6
        self.body.set_cursor_from_name("ns-resize" if edge else "default")

    def on_drag_begin(self, gesture, x, y):
        area = self.find(self.body_areas, x, y)
        if area and area[0] == "event":
            kind = "resize" if y > area[3] + area[5] - 6 else "move"
            self.drag = {"kind": kind, "item": area[1], "x": x, "y": y, "dx": 0, "dy": 0, "active": False}
        elif area:
            self.drag = {"kind": "create", "x": x, "y": y, "dx": 0, "dy": 0, "active": False}
        else:
            self.drag = None

    def on_drag_update(self, gesture, dx, dy):
        if not self.drag:
            return
        self.drag.update(dx=dx, dy=dy)
        if not self.drag["active"] and (abs(dx) > 4 or abs(dy) > 4):
            self.drag["active"] = True
            gesture.set_state(Gtk.EventSequenceState.CLAIMED)
        if self.drag["active"]:
            self.body.queue_draw()

    def drag_result(self, width):
        """What the drag means now: (start, end) as datetimes."""
        drag = self.drag
        if drag["kind"] == "create":
            day_a, minute_a = self.moment_at(drag["x"], drag["y"], width)
            day_b, minute_b = self.moment_at(drag["x"] + drag["dx"], drag["y"] + drag["dy"], width)
            a = datetime(day_a.year, day_a.month, day_a.day) + timedelta(minutes=minute_a)
            b = datetime(day_b.year, day_b.month, day_b.day) + timedelta(minutes=minute_b)
            if self.days == 1 or day_a == day_b:
                b = datetime(day_a.year, day_a.month, day_a.day) + timedelta(minutes=minute_b)
            a, b = min(a, b), max(a, b)
            return a, max(b, a + timedelta(minutes=15))
        item = drag["item"]
        start = rules.as_datetime(rules.parse(item["start"]))
        end = rules.as_datetime(rules.parse(item["end"]))
        shift = timedelta(minutes=round(drag["dy"] / HOUR * 60 / 15) * 15)
        if drag["kind"] == "resize":
            return start, max(start + timedelta(minutes=15), end + shift)
        days = round(drag["dx"] / self.columns(width)[0][1]) if self.days == 7 else 0
        return start + shift + timedelta(days=days), end + shift + timedelta(days=days)

    def draw_drag(self, cr, palette, columns):
        start, end = self.drag_result(self.body.get_width())
        color = palette.red if self.drag["kind"] == "create" else self.color(self.drag["item"])
        for (x, column), day in zip(columns, self.day_list()):
            day_start = datetime(day.year, day.month, day.day)
            top = max(start, day_start)
            bottom = min(end, day_start + timedelta(days=1))
            if bottom <= top:
                continue
            y1 = 6 + (top - day_start).total_seconds() / 3600 * HOUR
            y2 = 6 + (bottom - day_start).total_seconds() / 3600 * HOUR
            cr.set_source_rgba(*color, 0.35)
            theme.rounded(cr, x + 2, y1 + 1, column - 6, y2 - y1 - 2, 5)
            cr.fill()
            cr.set_source_rgba(*color, 0.9)
            cr.set_line_width(1.2)
            theme.rounded(cr, x + 2, y1 + 1, column - 6, y2 - y1 - 2, 5)
            cr.stroke()
            theme.text(cr, f"{start.strftime('%H:%M')}–{end.strftime('%H:%M')}", x + 8, y1 + 3, 11, palette.label, weight=600)

    def on_drag_end(self, gesture, dx, dy):
        drag, self.drag = self.drag, None
        if not drag or not drag.get("active"):
            return
        self.drag = drag
        start, end = self.drag_result(self.body.get_width())
        self.drag = None
        self.body.queue_draw()
        if drag["kind"] == "create":
            self.emit("range-selected", start, end)
        else:
            self.emit("event-moved", drag["item"], start, end)

    def color(self, item):
        calendar = self.store.calendar(item["calendar"])
        return theme.system(calendar["color"] if calendar else "blue")

    # ------------------------------------------------------------------

    def find(self, areas, x, y):
        for area in reversed(areas):
            kind, payload, ax, ay, aw, ah = area
            if ax <= x < ax + aw and ay <= y < ay + ah:
                return area
        return None

    def on_header_pressed(self, _gesture, presses, x, y):
        area = self.find(self.header_areas, x, y)
        if area is None:
            return
        if area[0] == "event":
            self.select(area[1], area, presses, header=True)
        else:
            self.emit("day-selected", area[1])

    def on_body_pressed(self, _gesture, presses, x, y):
        area = self.find(self.body_areas, x, y)
        if area is None:
            return
        if area[0] == "event":
            self.select(area[1], area, presses)
            return
        self.selected_key = None
        self.selected_item = None
        self.header.queue_draw()
        self.body.queue_draw()
        if presses == 2:
            minutes = int(max(0, (y - 6)) / HOUR * 60) // 15 * 15
            day = area[1]
            self.emit("slot-activated", datetime(day.year, day.month, day.day) + timedelta(minutes=minutes))

    def select(self, item, area, presses=1, header=False):
        self.selected_key = item["key"]
        self.selected_item = item
        self.header.queue_draw()
        self.body.queue_draw()
        self.emit("event-opened" if presses == 2 else "event-activated", item, (header,) + tuple(area[2:]))

    def area_of(self, key):
        """(in_header, x, y, w, h) of an occurrence, or None."""
        for in_header, areas in ((True, self.header_areas), (False, self.body_areas)):
            for kind, payload, ax, ay, aw, ah in areas:
                if kind == "event" and payload["key"] == key:
                    return (in_header, ax, ay, aw, ah)
        return None
