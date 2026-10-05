"""The main window like macOS Calendar: toolbar (sidebar, new event | Tag Woche Monat Jahr | search),
sidebar with calendars and a small month, and the view with its large title and ‹ Heute ›."""

from datetime import date, timedelta

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gio, GLib, Gtk

import uuid
from datetime import datetime

from . import editing, quick, rules, theme
from .inspector import Inspector, ask_delete
from .day_details import DayDetails
from .month_view import MonthView
from .sidebar import Sidebar
from .timeline_view import TimelineView
from .year_view import YearView
from .i18n import _

MODES = (("day", _("Tag")), ("week", _("Woche")), ("month", _("Monat")), ("year", _("Jahr")))


class Segmented(Gtk.Box):
    """A segmented control like Apple's: a gray capsule, the chosen segment a raised white pill."""

    def __init__(self, items, on_change):
        super().__init__(css_classes=["lical-segmented"], valign=Gtk.Align.CENTER)
        self.buttons = {}
        self.on_change = on_change
        for key, label in items:
            button = Gtk.Button(label=label, css_classes=["lical-segment"])
            button.connect("clicked", lambda _b, key=key: self.select(key, notify=True))
            self.append(button)
            self.buttons[key] = button

    def select(self, key, notify=False):
        for other, button in self.buttons.items():
            if other == key:
                button.add_css_class("selected")
            else:
                button.remove_css_class("selected")
        if notify:
            self.on_change(key)


class Chevron(Gtk.DrawingArea):
    """A thin chevron like Apple's (the icon theme's arrows look like angle brackets)."""

    def __init__(self, direction):
        super().__init__(content_width=12, content_height=12, valign=Gtk.Align.CENTER, halign=Gtk.Align.CENTER)
        self.direction = direction
        self.set_draw_func(self.draw)

    def draw(self, _area, cr, width, height):
        color = self.get_color()
        cr.set_source_rgba(color.red, color.green, color.blue, color.alpha)
        cr.set_line_width(1.7)
        cr.set_line_cap(1)
        cr.set_line_join(1)
        x = width / 2 + (1 if self.direction < 0 else -1)
        cr.move_to(x - 2.5 * self.direction, height / 2 - 5)
        cr.line_to(x + 2.5 * self.direction, height / 2)
        cr.line_to(x - 2.5 * self.direction, height / 2 + 5)
        cr.stroke()


def glass_button(icon, tooltip):
    button = Gtk.Button(icon_name=icon, css_classes=["lical-glass", "circular"], tooltip_text=tooltip, valign=Gtk.Align.CENTER)
    button.update_property([Gtk.AccessibleProperty.LABEL], [tooltip])
    return button


def describe(parsed):
    """What ＋ understood, e.g. "Mo., 5. Okt. · 19:00–20:00 · Abendessen"."""
    start = rules.parse(parsed["start"])
    end = rules.parse(parsed["end"])
    day = start if not isinstance(start, datetime) else start.date()
    text = f"{theme.WEEKDAYS_SHORT[day.weekday()]}., {day.day}. {theme.MONTHS[day.month - 1][:3]}."
    if parsed["allDay"]:
        last = end - timedelta(days=1)
        if last != day:
            text += f" – {theme.WEEKDAYS_SHORT[last.weekday()]}., {last.day}. {theme.MONTHS[last.month - 1][:3]}."
        text += _(" · ganztägig")
    else:
        text += f" · {start.strftime('%H:%M')}–{end.strftime('%H:%M')}"
    return f"{text} · {parsed['title']}"


class CalendarWindow(Adw.ApplicationWindow):
    def __init__(self, application, store):
        super().__init__(application=application, title="LiCal", default_width=1240, default_height=820)
        self.store = store
        self.mode = "month"
        self.day = date.today()

        header = Adw.HeaderBar(css_classes=["lical-toolbar"])
        self.sidebar_button = Gtk.ToggleButton(icon_name="sidebar-show-symbolic", active=True, css_classes=["lical-glass", "circular"],
                                               tooltip_text="Kalenderliste", valign=Gtk.Align.CENTER)
        header.pack_start(self.sidebar_button)
        new = glass_button("list-add-symbolic", _("Neuer Termin"))
        new.connect("clicked", lambda _b: self.show_quick_add())
        self.new_button = new
        header.pack_start(new)
        self.segmented = Segmented(MODES, self.set_mode)
        header.set_title_widget(self.segmented)
        self.search_entry = Gtk.SearchEntry(placeholder_text=_("Suchen"), css_classes=["lical-search"], width_chars=18, valign=Gtk.Align.CENTER)
        self.search_entry.connect("search-changed", lambda entry: self.show_search(entry.get_text()))
        self.search_entry.connect("activate", lambda _e: self.open_first_hit())
        # Main menu (the Mac's menu bar): print, settings – with their shortcuts shown.
        menu = Gio.Menu()
        menu.append(_("Drucken …"), "win.print")
        menu.append(_("Einstellungen …"), "win.preferences")
        self.menu_button = Gtk.MenuButton(icon_name="open-menu-symbolic", menu_model=menu, tooltip_text="Hauptmenü",
                                          valign=Gtk.Align.CENTER, css_classes=["flat"])
        header.pack_end(self.menu_button)
        header.pack_end(self.search_entry)
        for name, run in (("print", self.show_print), ("preferences", self.show_preferences)):
            action = Gio.SimpleAction.new(name, None)
            action.connect("activate", lambda _a, _p, run=run: run())
            self.add_action(action)
        application.set_accels_for_action("win.print", ["<Control>p"])
        application.set_accels_for_action("win.preferences", ["<Control>comma"])
        self.search_popover = None

        self.sidebar = Sidebar(store)
        self.sidebar.connect("day-selected", lambda _s, day: self.go_to(day))
        self.sidebar_button.connect("toggled", lambda button: self.sidebar.set_visible(button.get_active()))

        # Title row: "Oktober 2026" (month bold, year regular) and ‹ Heute ›.
        self.title = Gtk.Label(xalign=0, hexpand=True, css_classes=["lical-title"], use_markup=True)
        navigation = Gtk.Box(css_classes=["lical-nav"], valign=Gtk.Align.CENTER, spacing=6)
        back = Gtk.Button(child=Chevron(-1), css_classes=["lical-glass", "circular", "lical-small"], tooltip_text=_("Zurück"))
        today = Gtk.Button(label=_("Heute"), css_classes=["lical-glass", "lical-pill"], tooltip_text=_("Zu heute"))
        forward = Gtk.Button(child=Chevron(1), css_classes=["lical-glass", "circular", "lical-small"], tooltip_text=_("Weiter"))
        back.connect("clicked", lambda _b: self.step(-1))
        forward.connect("clicked", lambda _b: self.step(1))
        today.connect("clicked", lambda _b: self.go_to(date.today()))
        for button in (back, today, forward):
            navigation.append(button)
        title_row = Gtk.Box(css_classes=["lical-title-row"])
        title_row.append(self.title)
        title_row.append(navigation)

        self.month_view = MonthView(store)
        self.month_view.connect("month-changed", lambda _v, step: self.step(step))
        self.month_view.connect("day-selected", lambda _v, day: self.select_day(day))
        self.stack = Gtk.Stack(transition_type=Gtk.StackTransitionType.CROSSFADE, transition_duration=120)
        self.stack.add_named(self.month_view, "month")
        self.week_view = TimelineView(store, days=7)
        self.day_view = TimelineView(store, days=1)
        self.year_view = YearView(store)
        for view in (self.week_view, self.day_view):
            view.connect("day-selected", lambda _v, day: (self.go_to(day), self.set_mode("day")))
        self.year_view.connect("day-activated", lambda _v, day: (self.go_to(day), self.set_mode("day")))
        self.year_view.connect("month-activated", lambda _v, day: (self.go_to(day), self.set_mode("month")))
        self.year_view.connect("year-changed", lambda _v, step: self.step(step))
        self.month_view.connect("day-activated", lambda _v, day: self.create_event(day))
        self.month_view.connect("more-activated", lambda _v, day: (self.go_to(day), self.set_mode("day")))
        for view in (self.month_view, self.week_view, self.day_view):
            view.connect("event-opened", lambda view, item, _area: self.open_inspector(view, item))
        for view in (self.week_view, self.day_view):
            view.connect("slot-activated", lambda _v, moment: self.create_event(moment.date(), moment.hour))
            view.connect("range-selected", lambda _v, start, end: self.create_event(
                start.date(), parsed={"title": _("Neuer Termin"), "allDay": False, "start": start.strftime("%Y-%m-%dT%H:%M"), "end": end.strftime("%Y-%m-%dT%H:%M")}))
            view.connect("event-moved", lambda _v, item, start, end: self.move_event(item, start, end))
        self.month_view.connect("event-moved", lambda _v, item, days, _none: self.move_event(item, days=days))
        # Right click on an event in any view: its menu.
        for widget, view in ((self.month_view, self.month_view), (self.week_view.body, self.week_view), (self.week_view.header, self.week_view),
                             (self.day_view.body, self.day_view), (self.day_view.header, self.day_view)):
            menu = Gtk.GestureClick(button=3)
            menu.connect("pressed", lambda gesture, _n, x, y, widget=widget, view=view: self.on_right_click(gesture, widget, view, x, y))
            widget.add_controller(menu)
        keys = Gtk.EventControllerKey()
        keys.connect("key-pressed", self.on_key)
        self.add_controller(keys)
        self.stack.add_named(self.week_view, "week")
        # Day view like macOS 26: the hours on the left, a month and the details on the right.
        self.day_details = DayDetails(store)
        self.day_details.connect("day-selected", lambda _d, day: self.go_to(day))
        self.day_details.connect("event-chosen", lambda _d, item: self.open_hit(item))
        self.day_view.connect("event-activated", lambda _v, item, _area: self.day_details.show(self.day, item))
        day_page = Gtk.Box()
        day_page.append(self.day_view)
        day_page.append(Gtk.Separator(orientation=Gtk.Orientation.VERTICAL))
        day_page.append(self.day_details)
        self.stack.add_named(day_page, "day")
        self.stack.add_named(self.year_view, "year")

        content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, css_classes=["lical-content"], hexpand=True)
        content.append(title_row)
        content.append(self.stack)
        content.set_hexpand(True)
        body = Gtk.Box()
        body.append(self.sidebar)
        body.append(content)
        view = Adw.ToolbarView(content=body)
        view.add_top_bar(header)
        self.set_content(view)
        store.listeners.append(self.refresh)
        # Dark appearance: a class on the window (the CSS media query does not follow Adwaita's switch).
        Adw.StyleManager.get_default().connect("notify::dark", lambda *_a: self.follow_appearance())
        self.follow_appearance()
        self.set_mode("month")

    def follow_appearance(self):
        if Adw.StyleManager.get_default().get_dark():
            self.add_css_class("lical-dark")
        else:
            self.remove_css_class("lical-dark")
        self.refresh()

    def set_mode(self, mode):
        self.mode = mode
        self.segmented.select(mode)
        self.stack.set_visible_child_name(mode)
        self.refresh()

    def step(self, direction):
        if self.mode == "month":
            self.day = rules.add_months(self.day.replace(day=1), direction)
        elif self.mode == "year":
            self.day = self.day.replace(year=self.day.year + direction)
        elif self.mode == "week":
            self.day += timedelta(days=7 * direction)
        else:
            self.day += timedelta(days=direction)
        self.refresh()

    # ---- events: open, create, delete ----

    def current_view(self):
        return {"month": self.month_view, "week": self.week_view, "day": self.day_view}.get(self.mode)

    def open_inspector(self, view, item):
        """The inspector next to the event, as on the Mac (the arrow points at it)."""
        from gi.repository import Gdk
        area = view.area_of(item["key"]) if view is not None else None
        if area is None:
            return
        if self.store.is_read_only(item.get("calendar")):
            return self.open_read_only(view, item, area)
        if len(area) == 5:  # timeline: (in_header, x, y, w, h)
            in_header, x, y, w, h = area
            target = view.header if in_header else view.body
        else:
            x, y, w, h = area
            target = view
        inspector = Inspector(self, self.store, item)
        inspector.set_parent(target)
        inspector.set_pointing_to(Gdk.Rectangle(x=int(x), y=int(y), width=int(w), height=int(h)))
        inspector.set_position(Gtk.PositionType.RIGHT)
        inspector.connect("closed", lambda popover: GLib.idle_add(lambda: popover.unparent() and False))
        inspector.popup()
        self.inspector = inspector
        if not item.get("title") or item.get("title") == "Neuer Termin":
            inspector.title.grab_focus()
            inspector.title.select_region(0, -1)

    def open_read_only(self, view, item, area):
        """A holiday: what it is, nothing to change (like a subscribed calendar on the Mac)."""
        from gi.repository import Gdk
        from . import holidays
        if len(area) == 5:
            in_header, x, y, w, h = area
            target = view.header if in_header else view.body
        else:
            x, y, w, h = area
            target = view
        first = rules.parse(item["start"])
        info = self.store.calendar(item["calendar"])
        state = dict(holidays.STATES).get(info.get("state", ""), "")
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4, width_request=260, margin_start=14, margin_end=14,
                      margin_top=12, margin_bottom=12)
        box.append(Gtk.Label(label=item.get("title", ""), xalign=0, wrap=True, css_classes=["lical-inspector-title"]))
        box.append(Gtk.Label(label=_("{value}, {day}. {value2} {year} · ganztägig", value=theme.WEEKDAYS_LONG[first.weekday()], day=first.day, value2=theme.MONTHS[first.month - 1], year=first.year),
                             xalign=0, css_classes=["lical-inspector-label"]))
        box.append(Gtk.Label(label=_("{value} – {value2} · nur lesen", value=info['name'], value2=state if info.get('state') else 'bundesweit'), xalign=0,
                             css_classes=["dim-label", "caption"], margin_top=6))
        popover = Gtk.Popover(child=box, css_classes=["lical-inspector"])
        popover.set_parent(target)
        popover.set_pointing_to(Gdk.Rectangle(x=int(x), y=int(y), width=int(w), height=int(h)))
        popover.set_position(Gtk.PositionType.RIGHT)
        popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
        popover.popup()
        self.inspector = popover
        return None

    def create_event(self, day, hour=None, parsed=None):
        """＋ (plain language), a double click or a drag: a new event in the first visible calendar,
        then straight into the inspector."""
        calendar = next((c["id"] for c in self.store.calendars if c.get("visible", True)), self.store.calendars[0]["id"])
        event = editing.new_event(day, datetime.now(), calendar, uuid.uuid4().hex, hour)
        if parsed:
            event.update(parsed)
        self.store.put(event)
        if self.mode == "year":
            self.go_to(day)
            self.set_mode("day")
        else:
            self.go_to(day)
        view = self.current_view()
        key = f"{event['id']}@{event['start']}"
        item = next((i for i in self.store.occurrences(day, day + timedelta(days=1)) if i["key"] == key), None)

        if view is None or item is None:
            return
        view.selected_key = key
        view.selected_item = item
        tries = {"left": 40}

        def open_when_drawn():
            # The inspector points at the event, so wait until the view has drawn it.
            tries["left"] -= 1
            if view.area_of(key) is None and tries["left"] > 0:
                return True
            self.open_inspector(view, item)
            return False
        GLib.timeout_add(25, open_when_drawn)

    def delete_occurrence(self, event_id, occurrence_start):
        series = next((event for event in self.store.events if event["id"] == event_id), None)
        if series is None:
            return
        if not series.get("rrule"):
            self.store.delete(event_id)
            return

        def choice(response):
            if response == "one":
                self.store.put(editing.skip_occurrence(series, occurrence_start))
            elif response == "all":
                self.store.delete(event_id)
        self.delete_dialog = ask_delete(self, series.get("title", ""), choice)

    # ---- ＋: an event in plain language (like the Mac) ----

    def show_quick_add(self):
        """A field under ＋: "Abendessen morgen um 19 Uhr"; below it what LiCal understood; Enter creates it."""
        popover = Gtk.Popover(css_classes=["lical-quick"])
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6, width_request=340)
        for side in ("start", "end", "top", "bottom"):
            getattr(box, f"set_margin_{side}")(10)
        entry = Gtk.Entry(placeholder_text=_("Neues Ereignis erstellen"), css_classes=["lical-quick-entry"])
        hint = Gtk.Label(xalign=0, css_classes=["lical-inspector-label"], wrap=True)
        hint.set_label(_("z. B. „Abendessen morgen um 19 Uhr“ oder „Urlaub Mo–Fr“"))
        box.append(entry)
        box.append(hint)
        popover.set_child(box)

        def understood(_entry):
            text = entry.get_text().strip()
            if not text:
                hint.set_label(_("z. B. „Abendessen morgen um 19 Uhr“ oder „Urlaub Mo–Fr“"))
                return
            hint.set_label(describe(quick.parse(text, date.today(), self.day)))

        def create(_entry):
            text = entry.get_text().strip()
            if not text:
                return
            popover.popdown()
            parsed = quick.parse(text, date.today(), self.day)
            self.create_event(rules.parse(parsed["start"]) if parsed["allDay"] else rules.parse(parsed["start"]).date(), parsed=parsed)
        entry.connect("changed", understood)
        entry.connect("activate", create)
        popover.set_parent(self.new_button)
        popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
        popover.popup()
        entry.grab_focus()
        self.quick_popover, self.quick_entry = popover, entry

    # ---- search (like the Mac: matches below the field, a click jumps there) ----

    def search(self, text):
        """Events whose title, place or note contain every word; each once, at its next occurrence
        from today (or its last one)."""
        words = [word for word in text.lower().split() if word]
        if not words:
            return []
        today = date.today()
        hits = []
        for event in self.store.visible_events():
            haystack = " ".join(str(event.get(key, "")) for key in ("title", "location", "notes")).lower()
            if not all(word in haystack for word in words):
                continue
            upcoming = rules.occurrences([event], today, today + timedelta(days=3 * 365))
            if upcoming:
                hits.append(upcoming[0])
            else:
                past = rules.occurrences([event], rules.as_datetime(rules.parse(event["start"])).date(), today)
                if past:
                    hits.append(past[-1])
        hits.sort(key=lambda item: (rules.days_covered(item)[0] < today, rules.sort_key(item)))
        return hits

    def show_search(self, text):
        if self.search_popover is not None:
            self.search_popover.popdown()
            self.search_popover = None
        self.search_hits = self.search(text)
        if not text.strip():
            return
        popover = Gtk.Popover(autohide=False, has_arrow=True, css_classes=["lical-search-results"])
        popover.set_can_focus(False)
        box = Gtk.ListBox(selection_mode=Gtk.SelectionMode.NONE, css_classes=["lical-results"])
        if not self.search_hits:
            box.append(Gtk.Label(label=_("Keine Treffer"), css_classes=["dim-label"], margin_top=10, margin_bottom=10))
        for item in self.search_hits[:30]:
            box.append(self.search_row(item))
        scroller = Gtk.ScrolledWindow(child=box, propagate_natural_height=True, max_content_height=420, min_content_width=320,
                                      hscrollbar_policy=Gtk.PolicyType.NEVER)
        popover.set_child(scroller)
        popover.set_parent(self.search_entry)
        popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
        popover.popup()
        self.search_popover = popover

    def search_row(self, item):
        row = Gtk.Box(spacing=10, margin_start=10, margin_end=10, margin_top=6, margin_bottom=6)
        calendar = self.store.calendar(item["calendar"])
        dot = Gtk.DrawingArea(content_width=10, content_height=10, valign=Gtk.Align.CENTER)
        color = theme.system(calendar["color"] if calendar else "blue")
        dot.set_draw_func(lambda _a, cr, w, h: (cr.set_source_rgb(*color), theme.circle(cr, w / 2, h / 2, 5), cr.fill()))
        row.append(dot)
        texts = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, hexpand=True)
        texts.append(Gtk.Label(label=item.get("title", ""), xalign=0, ellipsize=3, css_classes=["heading"]))
        start = rules.parse(item["start"])
        day = rules.days_covered(item)[0]
        when = f"{theme.WEEKDAYS_SHORT[day.weekday()]}., {day.day}. {theme.MONTHS[day.month - 1]} {day.year}"
        when += _(" · ganztägig") if item.get("allDay") else f" · {start.strftime('%H:%M')}"
        if item.get("location"):
            when += f" · {item['location']}"
        texts.append(Gtk.Label(label=when, xalign=0, ellipsize=3, css_classes=["dim-label", "caption"]))
        row.append(texts)
        click = Gtk.GestureClick()
        click.connect("released", lambda *_a: self.open_hit(item))
        row.add_controller(click)
        row.set_cursor_from_name("pointer")
        return row

    def open_first_hit(self):
        if getattr(self, "search_hits", None):
            self.open_hit(self.search_hits[0])

    def open_hit(self, item):
        """Jump to the event (day view of its day), select it and open the inspector."""
        if self.search_popover is not None:
            self.search_popover.popdown()
            self.search_popover = None
        day = rules.days_covered(item)[0]
        self.set_mode("day")
        self.go_to(day)
        view = self.day_view
        view.selected_key, view.selected_item = item["key"], item
        self.day_details.show(day, item)
        tries = {"left": 40}

        def open_when_drawn():
            tries["left"] -= 1
            if view.area_of(item["key"]) is None and tries["left"] > 0:
                return True
            self.open_inspector(view, item)
            return False
        GLib.timeout_add(25, open_when_drawn)

    def show_go_to_date(self):
        """Shift+Ctrl+T: jump to a date (typed as 24.12.2026 or 24.12.)."""
        from .inspector import parse_date
        popover = Gtk.Popover()
        entry = Gtk.Entry(placeholder_text=_("Gehe zu Datum (TT.MM.JJJJ)"), width_chars=24)
        entry.set_margin_start(8)
        entry.set_margin_end(8)
        entry.set_margin_top(8)
        entry.set_margin_bottom(8)
        popover.set_child(entry)

        def go(_entry):
            day = parse_date(entry.get_text(), date.today())
            if day:
                popover.popdown()
                self.go_to(day)
        entry.connect("activate", go)
        popover.set_parent(self.title)
        popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
        popover.popup()
        entry.grab_focus()

    # ---- keyboard like the Mac (Ctrl for ⌘) ----

    def visible_range(self):
        if self.mode == "month":
            weeks = rules.month_weeks(self.day.year, self.day.month)
            return weeks[0][0], weeks[-1][-1] + timedelta(days=1)
        if self.mode == "week":
            start = rules.week_start(self.day)
            return start, start + timedelta(days=7)
        if self.mode == "day":
            return self.day, self.day + timedelta(days=1)
        return date(self.day.year, 1, 1), date(self.day.year + 1, 1, 1)

    def select_next(self, direction):
        """Tab / Shift+Tab: the next or previous event of the view."""
        view = self.current_view()
        if view is None:
            return
        items = self.store.occurrences(*self.visible_range())
        if not items:
            return
        keys = [item["key"] for item in items]
        current = getattr(view, "selected_key", None)
        index = keys.index(current) + direction if current in keys else (0 if direction > 0 else len(keys) - 1)
        item = items[index % len(items)]
        view.selected_key = item["key"]
        view.selected_item = item
        for widget in (view, getattr(view, "body", None), getattr(view, "header", None)):
            if widget is not None:
                widget.queue_draw()

    def event_menu(self, widget, item, x, y):
        """Right click on an event: open it, put it into another calendar, delete it (like the Mac)."""
        from gi.repository import Gdk
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2, margin_start=6, margin_end=6, margin_top=6, margin_bottom=6)
        popover = Gtk.Popover(child=box, has_arrow=False)

        def entry(label, action, css=None):
            button = Gtk.Button(label=label, css_classes=["flat"] + ([css] if css else []))
            button.get_child().set_xalign(0)
            button.connect("clicked", lambda _b: (popover.popdown(), action()))
            box.append(button)
        view = self.current_view()
        entry("Informationen", lambda: self.open_inspector(view, item))
        if self.store.is_read_only(item.get("calendar")):  # a holiday: nothing to move or delete
            popover.set_parent(widget)
            popover.set_pointing_to(Gdk.Rectangle(x=int(x), y=int(y), width=1, height=1))
            popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
            popover.popup()
            self.context_popover = popover
            return
        box.append(Gtk.Separator())
        for calendar in self.store.calendars:
            mark = "✓ " if calendar["id"] == item.get("calendar") else "    "
            entry(mark + calendar["name"], lambda calendar=calendar: self.set_calendar(item, calendar["id"]))
        box.append(Gtk.Separator())
        entry(_("Als QR-Code teilen …"), lambda: self.share_event(item["id"]))
        entry(_("Löschen"), lambda: self.delete_occurrence(item["id"], item.get("zoneStart", item["start"])), "lical-delete")
        popover.set_parent(widget)
        popover.set_pointing_to(Gdk.Rectangle(x=int(x), y=int(y), width=1, height=1))
        popover.connect("closed", lambda p: GLib.idle_add(lambda: p.unparent() and False))
        popover.popup()
        self.context_popover = popover

    def ask_import(self, text, name):
        """An .ics file: "Termin hinzufügen?" with its title and time and the calendar to put it in."""
        from . import ics
        found = ics.from_ics(text)
        if found is None:
            dialog = Adw.AlertDialog(heading=_("Kein Termin gefunden"), body=_("In „{name}“ steht kein Termin, den LiCal lesen kann.", name=name))
            dialog.add_response("ok", "OK")
            dialog.present(self)
            self.import_dialog = dialog
            return dialog
        first = rules.parse(found["start"])
        day = first.date() if isinstance(first, datetime) else first
        when = f"{theme.WEEKDAYS_LONG[day.weekday()]}, {day.day}. {theme.MONTHS[day.month - 1]} {day.year}"
        when += _(", ganztägig") if found["allDay"] else _(", {first:%H:%M}–{parse:%H:%M} Uhr", first=first, parse=rules.parse(found['end']))
        body = when + (f"\n{found['location']}" if found.get("location") else "")
        dialog = Adw.AlertDialog(heading=_("„{value}“ hinzufügen?", value=found['title']), body=body)
        calendars = Gtk.DropDown.new_from_strings([calendar["name"] for calendar in self.store.calendars])
        visible = [i for i, calendar in enumerate(self.store.calendars) if calendar.get("visible", True)]
        calendars.set_selected(visible[0] if visible else 0)
        calendars.set_halign(Gtk.Align.CENTER)
        dialog.set_extra_child(calendars)
        dialog.add_response("cancel", _("Abbrechen"))
        dialog.add_response("add", _("Hinzufügen"))
        dialog.set_response_appearance("add", Adw.ResponseAppearance.SUGGESTED)
        dialog.set_default_response("add")
        dialog.set_close_response("cancel")

        def answer(_dialog, response):
            if response != "add":
                return
            event = dict(found, id=uuid.uuid4().hex, calendar=self.store.calendars[calendars.get_selected()]["id"])
            self.store.put(event)
            self.set_mode("day")
            self.go_to(day)
        dialog.connect("response", answer)
        dialog.present(self)
        self.import_dialog = dialog
        self.import_calendars = calendars
        return dialog

    def show_print(self):
        from .printing import show_print
        self.print_dialog = show_print(self)

    def show_preferences(self):
        from .preferences import show_preferences
        self.preferences = show_preferences(self)

    def share_event(self, event_id):
        """The event (a repeating one as its series) as a QR code – see share.py."""
        from .share import show_qr
        series = next((event for event in self.store.events if event["id"] == event_id), None)
        if series is not None:
            self.share_dialog = show_qr(self, series)

    def on_right_click(self, gesture, widget, view, x, y):
        if widget is view:
            area = view.hit(x, y)
        else:
            area = view.find(view.header_areas if widget is view.header else view.body_areas, x, y)
        if area and area[0] == "event":
            gesture.set_state(Gtk.EventSequenceState.CLAIMED)
            view.selected_key, view.selected_item = area[1]["key"], area[1]
            widget.queue_draw()
            self.event_menu(widget, area[1], x, y)

    def set_calendar(self, item, calendar_id):
        series = next((event for event in self.store.events if event["id"] == item["id"]), None)
        if series is not None and series.get("calendar") != calendar_id:
            self.store.put({**series, "calendar": calendar_id})

    def move_event(self, item, start=None, end=None, days=0):
        """A dragged event: new start/end (timeline) or some days later/earlier (month). A repeating
        one moves as a series (like the inspector)."""
        series = next((event for event in self.store.events if event["id"] == item["id"]), None)
        if series is None:
            return
        # An event in another zone is changed in its own zone (the view shows local times).
        own = rules.for_editing(item)
        moved = {k: v for k, v in own.items() if k != "key"}
        if days:
            moved = editing.set_start(moved, rules.parse(own["start"]) + timedelta(days=days))
        if start is not None:
            moved = editing.set_start(moved, rules.to_event_zone(series, start))
            moved = editing.set_end(moved, rules.to_event_zone(series, end))
        stored = editing.apply_to_series(series, own["start"], moved) if series.get("rrule") else {**moved, "id": series["id"]}
        self.store.put(stored)

    def move_selected(self, minutes=0, days=0):
        """Ctrl+Alt+arrows: 15 minutes or a day (in the month a week), like the Mac."""
        view = self.current_view()
        item = getattr(view, "selected_item", None) if view is not None else None
        if not item:
            return
        series = next((event for event in self.store.events if event["id"] == item["id"]), None)
        if series is None:
            return
        own = rules.for_editing(item)
        start = rules.parse(own["start"])
        moved = editing.set_start({k: v for k, v in own.items() if k != "key"},
                                  (start + timedelta(minutes=minutes, days=days)) if not item.get("allDay") else start + timedelta(days=days or (minutes // 15) * 7))
        stored = editing.apply_to_series(series, own["start"], moved) if series.get("rrule") else {**moved, "id": series["id"]}
        self.store.put(stored)
        # The selection follows the event (its key holds the start in its own zone; the view shows local times).
        first = rules.parse(moved["start"])
        day = first.date() if isinstance(first, datetime) else first
        key = f"{item['id']}@{moved['start']}"
        shown = next((other for other in self.store.occurrences(day - timedelta(days=1), day + timedelta(days=2)) if other["key"] == key), None)
        new_item = shown or dict(moved, key=key)
        view.selected_key, view.selected_item = new_item["key"], new_item

    def on_key(self, _controller, keyval, _code, state):
        from gi.repository import Gdk
        view = self.current_view()
        item = getattr(view, "selected_item", None) if view is not None else None
        focus = self.get_focus()
        ctrl = bool(state & Gdk.ModifierType.CONTROL_MASK)
        alt = bool(state & Gdk.ModifierType.ALT_MASK)
        shift = bool(state & Gdk.ModifierType.SHIFT_MASK)
        # Shortcuts with Ctrl work everywhere (also while typing in a field).
        if ctrl and not alt:
            views = {Gdk.KEY_1: "day", Gdk.KEY_2: "week", Gdk.KEY_3: "month", Gdk.KEY_4: "year"}
            if keyval in views:
                self.set_mode(views[keyval])
                return True
            if keyval in (Gdk.KEY_t, Gdk.KEY_T):
                self.show_go_to_date() if shift else self.go_to(date.today())
                return True
            if keyval in (Gdk.KEY_n, Gdk.KEY_N):
                self.show_quick_add()
                return True
            if keyval in (Gdk.KEY_f, Gdk.KEY_F):
                self.search_entry.grab_focus()
                return True
            if keyval in (Gdk.KEY_e, Gdk.KEY_E) and item:
                self.open_inspector(view, item)
                return True
            if keyval in (Gdk.KEY_Right, Gdk.KEY_Left) and not isinstance(focus, (Gtk.Text, Gtk.TextView)):
                self.step(1 if keyval == Gdk.KEY_Right else -1)
                return True
        if ctrl and alt and item:
            month = self.mode == "month"
            moves = {Gdk.KEY_Up: (-15, 0) if not month else (0, -7), Gdk.KEY_Down: (15, 0) if not month else (0, 7),
                     Gdk.KEY_Right: (0, 1 if not month else 7), Gdk.KEY_Left: (0, -1 if not month else -7)}
            if keyval in moves:
                self.move_selected(*moves[keyval])
                return True
        if isinstance(focus, (Gtk.Text, Gtk.TextView, Gtk.Entry)):
            return False
        if keyval == Gdk.KEY_Tab or keyval == Gdk.KEY_ISO_Left_Tab:
            self.select_next(-1 if shift or keyval == Gdk.KEY_ISO_Left_Tab else 1)
            return True
        if item and keyval in (Gdk.KEY_Delete, Gdk.KEY_BackSpace):
            self.delete_occurrence(item["id"], item.get("zoneStart", item["start"]))
            return True
        if item and keyval in (Gdk.KEY_Return, Gdk.KEY_KP_Enter):
            self.open_inspector(view, item)
            return True
        return False

    def go_to(self, day):
        self.day = day
        self.month_view.selected_day = day
        self.refresh()

    def select_day(self, day):
        self.day = day
        self.sidebar.mini.show(day)

    def refresh(self):
        month = theme.MONTHS[self.day.month - 1]
        if self.mode == "year":
            self.title.set_markup(f"<b>{self.day.year}</b>")
        elif self.mode == "day":
            self.title.set_markup(f"<b>{self.day.day}. {month}</b> {self.day.year}  "
                                  f"<span size='60%' foreground='#8e8e93'>{theme.WEEKDAYS_LONG[self.day.weekday()]}</span>")
        else:
            self.title.set_markup(f"<b>{month}</b> {self.day.year}")
        self.month_view.show_month(self.day)
        self.week_view.show_day(self.day)
        self.day_view.show_day(self.day)
        selected = self.day_view.selected_item if getattr(self.day_view, "selected_item", None) and \
            rules.days_covered(self.day_view.selected_item)[0] <= self.day <= rules.days_covered(self.day_view.selected_item)[1] else None
        self.day_details.show(self.day, selected)
        self.year_view.show_year(self.day.year)
        self.sidebar.mini.show(self.day)
        self.sidebar.refresh()
