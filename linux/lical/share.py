"""Pass an event on as a QR code (card b483dadf): the iCalendar text of this one event – an iPhone's
camera, LiCal on Android and most calendar apps read it. Privacy first: nothing leaves the screen
unless someone scans it, the code holds only this event (no calendar, no absence/deputy, no id), and
the notes go in only when switched on."""

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gio, GLib, Graphene, Gtk

from . import ics
from .qrcodegen import DataTooLongError, QrCode


class QrView(Gtk.Widget):
    """A QR code on white with the quiet zone of four modules."""

    def __init__(self, size=240):
        super().__init__(halign=Gtk.Align.CENTER)
        self.qr = None
        self.size = size

    def set_text(self, text):
        try:
            self.qr = QrCode.encode_text(text, QrCode.Ecc.MEDIUM)
        except DataTooLongError:
            self.qr = None
        self.queue_draw()
        return self.qr is not None

    def do_measure(self, orientation, for_size):
        return self.size, self.size, -1, -1

    def do_snapshot(self, snapshot):
        if self.qr is None:
            return
        size = self.size
        cr = snapshot.append_cairo(Graphene.Rect().init(0, 0, size, size))
        count = self.qr.get_size()
        scale = size / (count + 8)
        cr.set_source_rgb(1, 1, 1)
        cr.rectangle(0, 0, size, size)
        cr.fill()
        cr.set_source_rgb(0, 0, 0)
        for y in range(count):
            for x in range(count):
                if self.qr.get_module(x, y):
                    cr.rectangle((x + 4) * scale, (y + 4) * scale, scale + 0.3, scale + 0.3)
        cr.fill()


def shared_event(series, with_notes):
    """What goes into the code: title, time, place, repetition – the notes only on request."""
    event = {key: series[key] for key in ("title", "allDay", "start", "end", "location", "rrule", "tz") if series.get(key)}
    event["allDay"] = bool(series.get("allDay"))
    if with_notes and series.get("notes"):
        event["notes"] = series["notes"]
    return event


def show_qr(window, series):
    """A sheet with the event's QR code (like Apple's share sheets: title, code, one switch)."""
    dialog = Adw.Dialog(title="Als QR-Code teilen", content_width=340)
    header = Adw.HeaderBar(show_end_title_buttons=True)
    box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12, margin_start=24, margin_end=24, margin_top=6, margin_bottom=24)
    title = Gtk.Label(label=series.get("title", ""), css_classes=["title-3"], wrap=True, justify=Gtk.Justification.CENTER)
    box.append(title)
    code = QrView()
    box.append(code)
    hint = Gtk.Label(wrap=True, justify=Gtk.Justification.CENTER, css_classes=["dim-label", "caption"], max_width_chars=36)
    box.append(hint)
    notes_row = Gtk.Box(spacing=12, margin_top=6)
    notes_row.append(Gtk.Label(label="Notizen mitgeben", hexpand=True, xalign=0))
    notes = Gtk.Switch(active=False, valign=Gtk.Align.CENTER, sensitive=bool(series.get("notes")))
    notes_row.append(notes)
    box.append(notes_row)
    save = Gtk.Button(label="Als Datei sichern …", css_classes=["flat"], halign=Gtk.Align.CENTER)
    box.append(save)

    def refresh(*_args):
        text = ics.to_ics(shared_event(series, notes.get_active()))
        dialog.text = text
        if code.set_text(text):
            hint.set_label("Mit der Kamera eines anderen Telefons scannen – nur dieser Termin ist im Code.")
        else:
            hint.set_label("Zu viel Text für einen QR-Code. Ohne Notizen teilen oder als Datei sichern.")
    notes.connect("notify::active", refresh)
    refresh()

    def save_file(_button):
        chooser = Gtk.FileDialog(initial_name=f"{series.get('title') or 'Termin'}.ics")

        def done(source, result):
            try:
                target = source.save_finish(result)
            except GLib.Error:
                return
            target.replace_contents(dialog.text.encode(), None, False, Gio.FileCreateFlags.PRIVATE, None)
        chooser.save(window, None, done)
    save.connect("clicked", save_file)

    toolbar = Adw.ToolbarView(content=box)
    toolbar.add_top_bar(header)
    dialog.set_child(toolbar)
    dialog.present(window)
    return dialog
