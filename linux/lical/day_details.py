"""The right column of the day view like macOS 26 Calendar: a month to jump around in, and below it the
chosen event's details (title, time, place, calendar, repeat, note) – or, without a chosen event,
the day's events. Read only; changes go through the inspector (double click or Ctrl+E)."""

from datetime import datetime

import gi

gi.require_version("Gtk", "4.0")
from gi.repository import GObject, Gtk

from . import editing, rules, theme
from .sidebar import MiniMonth
from .i18n import _


class DayDetails(Gtk.Box):
    __gsignals__ = {
        "day-selected": (GObject.SignalFlags.RUN_FIRST, None, (object,)),
        "event-chosen": (GObject.SignalFlags.RUN_FIRST, None, (object,)),
    }

    def __init__(self, store):
        super().__init__(orientation=Gtk.Orientation.VERTICAL, css_classes=["lical-day-details"], width_request=300, hexpand=False)
        self.store = store
        self.month = MiniMonth()
        self.month.set_margin_start(14)
        self.month.set_margin_end(14)
        self.month.set_margin_top(4)
        self.month.connect("day-selected", lambda _m, day: self.emit("day-selected", day))
        self.append(self.month)
        self.content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6, margin_start=16, margin_end=16, margin_top=10)
        scroller = Gtk.ScrolledWindow(child=self.content, vexpand=True, hscrollbar_policy=Gtk.PolicyType.NEVER)
        self.append(scroller)

    def show(self, day, item=None):
        self.month.show(day)
        child = self.content.get_first_child()
        while child is not None:
            following = child.get_next_sibling()
            self.content.remove(child)
            child = following
        if item is not None:
            self.show_event(item)
        else:
            self.show_day(day)

    def label(self, text, css, **kwargs):
        widget = Gtk.Label(label=text, xalign=0, wrap=True, css_classes=css, **kwargs)
        self.content.append(widget)
        return widget

    def show_event(self, item):
        calendar = self.store.calendar(item["calendar"])
        self.label(item.get("title", ""), ["lical-details-title"])
        start = rules.parse(item["start"])
        first, last = rules.days_covered(item)
        when = f"{theme.WEEKDAYS_LONG[first.weekday()]}, {first.day}. {theme.MONTHS[first.month - 1]} {first.year}"
        if last != first:
            when += _(" bis {day}. {value} {year}", day=last.day, value=theme.MONTHS[last.month - 1], year=last.year)
        self.label(when, ["lical-details-line"])
        if not item.get("allDay"):
            end = rules.parse(item["end"])
            self.label(_("{date} bis {date2} Uhr", date=start.strftime('%H:%M'), date2=end.strftime('%H:%M')), ["lical-details-line"])
            if item.get("zoneStart"):  # another zone (card 7a9187d6): its own times too
                from . import settings
                own_start, own_end = rules.parse(item["zoneStart"]), rules.parse(item["zoneEnd"])
                self.label(_("{own_start:%H:%M} bis {own_end:%H:%M} Uhr in {zone_label}", own_start=own_start, own_end=own_end, zone_label=settings.zone_label(item['tz'])), ["lical-details-dim"])
        else:
            self.label(_("ganztägig"), ["lical-details-line"])
        if item.get("absence"):
            self.label(editing.absence_text(item), ["lical-details-line"])
        if item.get("rrule"):
            self.label(_("Wiederholen: {repeat_label}", repeat_label=editing.repeat_label(item['rrule'])), ["lical-details-dim"])
        if item.get("location"):
            self.label(item["location"], ["lical-details-line"])
        if calendar:
            self.label(_("Kalender: {value}", value=calendar['name']), ["lical-details-dim"])
        if item.get("notes"):
            self.label(item["notes"], ["lical-details-notes"], selectable=True)
        self.label(_("Doppelklick oder Strg+E zum Bearbeiten"), ["lical-details-dim"], margin_top=8)

    def show_day(self, day):
        items = self.store.occurrences(day, day + rules.timedelta(days=1))
        self.label(f"{theme.WEEKDAYS_LONG[day.weekday()]}, {day.day}. {theme.MONTHS[day.month - 1]}", ["lical-details-title"])
        if not items:
            self.label(_("Keine Termine"), ["lical-details-dim"])
        for item in items:
            row = Gtk.Box(spacing=8)
            calendar = self.store.calendar(item["calendar"])
            color = theme.system(calendar["color"] if calendar else "blue")
            bar = Gtk.DrawingArea(content_width=4, content_height=32)
            bar.set_draw_func(lambda _a, cr, w, h, color=color: (cr.set_source_rgb(*color), theme.rounded(cr, 0, 0, w, h, 2), cr.fill()))
            row.append(bar)
            texts = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
            texts.append(Gtk.Label(label=item.get("title", ""), xalign=0, ellipsize=3, css_classes=["lical-details-line"]))
            start = rules.parse(item["start"])
            texts.append(Gtk.Label(label=_("ganztägig") if item.get("allDay") or isinstance(start, datetime) is False
                                   else f"{start.strftime('%H:%M')}–{rules.parse(item['end']).strftime('%H:%M')}",
                                   xalign=0, css_classes=["lical-details-dim"]))
            row.append(texts)
            click = Gtk.GestureClick()
            click.connect("released", lambda *_a, item=item: self.emit("event-chosen", item))
            row.add_controller(click)
            row.set_cursor_from_name("pointer")
            self.content.append(row)
