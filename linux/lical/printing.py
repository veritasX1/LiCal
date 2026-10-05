"""Printing like the Mac's Calendar (card 7a9187d6): ⌘P (here Ctrl+P) opens a sheet – Monat or Liste,
from which month and for how many, notes in the list or not – then the system's print dialog (which
also prints to a PDF file) or straight "Als PDF sichern". Only the calendars shown are printed; paper
is always light. Months come out landscape, one per page; the list portrait, as many pages as needed."""

from datetime import date, timedelta

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, GLib, Gtk

from . import rules, theme

MARGIN = 28          # points inside the printable area
TITLE = 34
LIST_LINE = 15


def months_from(first, count):
    month = first.replace(day=1)
    for _index in range(count):
        yield month
        month = rules.add_months(month, 1)


def list_lines(store, first, last, notes):
    """The list's lines: ("day", text) headings and ("event", time, title, detail) rows."""
    lines = []
    items = store.occurrences(first, last + timedelta(days=1))
    day = first
    while day <= last:
        today = [item for item in items if rules.days_covered(item)[0] <= day <= rules.days_covered(item)[1]]
        if today:
            lines.append(("day", f"{theme.WEEKDAYS_LONG[day.weekday()]}, {day.day}. {theme.MONTHS[day.month - 1]} {day.year}"))
            for item in today:
                if item.get("allDay") or rules.days_covered(item)[0] != rules.days_covered(item)[1]:
                    when = "ganztägig"
                else:
                    when = f"{rules.parse(item['start']):%H:%M}–{rules.parse(item['end']):%H:%M}"
                detail = item.get("location", "")
                lines.append(("event", when, item.get("title", ""), detail, store.calendar(item.get("calendar")) or {}))
                if notes and item.get("notes"):
                    for note in item["notes"].splitlines():
                        lines.append(("note", note))
        day += timedelta(days=1)
    return lines


class Printer:
    """One print job: what to print and how to draw each page."""

    def __init__(self, window, view, first, count, notes):
        self.window = window
        self.store = window.store
        self.view = view
        self.months = list(months_from(first, count))
        self.notes = notes
        self.lines = list_lines(self.store, self.months[0], rules.add_months(self.months[-1], 1) - timedelta(days=1), notes)
        self.per_page = 1

    def operation(self):
        operation = Gtk.PrintOperation(job_name="LiCal", unit=Gtk.Unit.POINTS, embed_page_setup=True)
        setup = Gtk.PageSetup()
        setup.set_orientation(Gtk.PageOrientation.LANDSCAPE if self.view == "month" else Gtk.PageOrientation.PORTRAIT)
        operation.set_default_page_setup(setup)
        operation.connect("begin-print", self.begin)
        operation.connect("draw-page", self.draw_page)
        return operation

    def begin(self, operation, context):
        if self.view == "month":
            operation.set_n_pages(len(self.months))
        else:
            usable = context.get_height() - 2 * MARGIN - TITLE
            self.per_page = max(1, int(usable // LIST_LINE))
            operation.set_n_pages(max(1, -(-len(self.lines) // self.per_page)))

    def draw_page(self, _operation, context, number):
        cr = context.get_cairo_context()
        width, height = context.get_width(), context.get_height()
        theme.PRINTING = True
        try:
            palette = theme.Palette()
            cr.set_source_rgb(1, 1, 1)
            cr.paint()
            if self.view == "month":
                self.draw_month(cr, palette, self.months[number], width, height)
            else:
                self.draw_list(cr, palette, number, width)
        finally:
            theme.PRINTING = False

    def draw_month(self, cr, palette, month, width, height):
        from .month_view import MonthView
        theme.text(cr, f"{theme.MONTHS[month.month - 1]}", MARGIN, MARGIN - 4, 22, palette.label, weight=700)
        theme.text(cr, f" {month.year}", MARGIN + theme.text_width(cr, theme.MONTHS[month.month - 1], 22, 700), MARGIN - 4, 22,
                   palette.label, weight=400)
        grid = MonthView(self.store)
        grid.show_month(month)
        grid.selected_day = None
        grid.mark_today = False  # paper has no "today"
        cr.save()
        cr.translate(MARGIN, MARGIN + TITLE)
        cr.rectangle(0, 0, width - 2 * MARGIN, height - 2 * MARGIN - TITLE)
        cr.clip()  # the grid paints its own ground – only there, not over the title
        grid.draw(None, cr, width - 2 * MARGIN, height - 2 * MARGIN - TITLE)
        cr.restore()

    def draw_list(self, cr, palette, number, width):
        first, last = self.months[0], rules.add_months(self.months[-1], 1) - timedelta(days=1)
        theme.text(cr, f"{first.day}. {theme.MONTHS[first.month - 1]} {first.year} – {last.day}. {theme.MONTHS[last.month - 1]} {last.year}",
                   MARGIN, MARGIN - 4, 15, palette.label, weight=700)
        y = MARGIN + TITLE
        if not self.lines:
            theme.text(cr, "Keine Termine", MARGIN, y, 11, palette.secondary)
        for line in self.lines[number * self.per_page:(number + 1) * self.per_page]:
            if line[0] == "day":
                theme.text(cr, line[1], MARGIN, y + 2, 10.5, palette.label, weight=700)
            elif line[0] == "event":
                _kind, when, title, detail, calendar = line
                color = theme.system(calendar.get("color", "blue")) if not str(calendar.get("color", "")).startswith("#") else (0.5, 0.5, 0.5)
                cr.set_source_rgb(*color)
                cr.new_sub_path()
                cr.arc(MARGIN + 4, y + 7.5, 3, 0, 6.2832)
                cr.fill()
                theme.text(cr, when, MARGIN + 14, y + 1, 10, palette.secondary, tabular=True)
                theme.text(cr, title + (f"  ·  {detail}" if detail else ""), MARGIN + 100, y + 1, 10, palette.label,
                           width=int(width - 2 * MARGIN - 100))
            else:
                theme.text(cr, line[1], MARGIN + 100, y + 1, 9, palette.secondary, width=int(width - 2 * MARGIN - 100))
            y += LIST_LINE

    def export(self, path):
        operation = self.operation()
        operation.set_export_filename(str(path))
        return operation.run(Gtk.PrintOperationAction.EXPORT, self.window)

    def print(self):
        return self.operation().run(Gtk.PrintOperationAction.PRINT_DIALOG, self.window)


def show_print(window):
    """The sheet before printing (like the Mac's: view, range, options, preview of the count)."""
    dialog = Adw.Dialog(title="Drucken", content_width=380)
    header = Adw.HeaderBar()
    page = Adw.PreferencesPage()
    group = Adw.PreferencesGroup()
    view = Adw.ComboRow(title="Ansicht", model=Gtk.StringList.new(["Monat", "Liste"]))
    view.set_selected(1 if window.mode in ("day", "week") else 0)
    start = window.day.replace(day=1)
    months = [rules.add_months(start, offset) for offset in range(-12, 25)]
    first = Adw.ComboRow(title="Ab", model=Gtk.StringList.new([f"{theme.MONTHS[m.month - 1]} {m.year}" for m in months]))
    first.set_selected(12)
    count = Adw.SpinRow.new_with_range(1, 12, 1)
    count.set_title("Monate")
    notes = Adw.SwitchRow(title="Notizen einbeziehen", subtitle="Nur in der Liste")
    for row in (view, first, count, notes):
        group.add(row)
    page.add(group)
    hint = Gtk.Label(label="Gedruckt werden die eingeblendeten Kalender.", css_classes=["dim-label", "caption"], margin_top=4)
    buttons = Gtk.Box(spacing=8, halign=Gtk.Align.END, margin_start=18, margin_end=18, margin_bottom=18)
    pdf = Gtk.Button(label="Als PDF sichern …")
    print_button = Gtk.Button(label="Drucken …", css_classes=["suggested-action"])
    buttons.append(pdf)
    buttons.append(print_button)
    content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
    content.append(page)
    content.append(hint)
    content.append(buttons)
    toolbar = Adw.ToolbarView(content=content)
    toolbar.add_top_bar(header)
    dialog.set_child(toolbar)

    def job():
        return Printer(window, "month" if view.get_selected() == 0 else "list", months[first.get_selected()],
                       int(count.get_value()), notes.get_active())

    def to_pdf(_button):
        chosen = job()
        name = f"LiCal {theme.MONTHS[chosen.months[0].month - 1]} {chosen.months[0].year}.pdf"
        chooser = Gtk.FileDialog(initial_name=name)

        def done(source, result):
            try:
                target = source.save_finish(result)
            except GLib.Error:
                return
            dialog.close()
            chosen.export(target.get_path())
        chooser.save(window, None, done)

    pdf.connect("clicked", to_pdf)
    print_button.connect("clicked", lambda _b: (dialog.close(), job().print()))
    dialog.job = job
    dialog.controls = {"view": view, "first": first, "count": count, "notes": notes}
    dialog.present(window)
    return dialog
