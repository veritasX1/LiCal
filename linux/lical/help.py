"""Hilfe (card d38290c7, like LiMail): every function in short entries to open. The texts come from
shared/hilfe/hilfe.json (the same for Android) and are translated through the catalogue."""

import json
from pathlib import Path

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, GLib, Gtk

from .i18n import _

HERE = Path(__file__).resolve().parent
SOURCES = (HERE.parents[1] / "shared" / "hilfe" / "hilfe.json", HERE / "hilfe.json")


def content():
    for path in SOURCES:
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
    return {"abschnitte": []}


def sections(platform="ubuntu"):
    """[(section title, [(title, text)])], translated, only what concerns this platform."""
    out = []
    for section in content().get("abschnitte", []):
        entries = [(_(e["titel"]), _(e["text"])) for e in section.get("eintraege", []) if e.get("nur") in (None, platform)]
        if entries:
            out.append((_(section["titel"]), entries))
    return out


def show_help(parent):
    dialog = Adw.PreferencesDialog(title=_("Hilfe"), search_enabled=True, content_width=640, content_height=760)
    page = Adw.PreferencesPage(title=_("Hilfe"), icon_name="help-browser-symbolic")
    for title, entries in sections():
        group = Adw.PreferencesGroup(title=title)
        for entry_title, text in entries:
            row = Adw.ExpanderRow(title=entry_title)
            row.add_css_class("lical-help-entry")  # css_classes= würde „expander“/„empty“ von libadwaita löschen
            label = Gtk.Label(label=text, wrap=True, xalign=0, selectable=True, margin_top=10, margin_bottom=12, margin_start=12, margin_end=12)
            row.add_row(Adw.PreferencesRow(child=label, activatable=False))
            group.add(row)
        page.add(group)
    dialog.add(page)
    dialog.present(parent)
    return dialog


def load_licenses():
    """shared/lizenzen/lizenzen.json (tools/lizenzen.py), next to the code when installed."""
    here = Path(__file__).resolve().parent
    for path in (here.parents[1] / "shared" / "lizenzen" / "lizenzen.json", here / "lizenzen.json"):
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
    return None


def show_about(parent, version):
    about = Adw.AboutDialog(application_name="LiCal", application_icon="io.github.veritasx1.LiCal", version=version,
                            developer_name="Olaf Winkler", license_type=Gtk.License.GPL_3_0,
                            comments=_("Ein Kalender nach dem Vorbild von Apples Kalender – ohne Konto, ohne Werbung, ohne Datensammlung."),
                            website="https://lisoftware.de/lical/", issue_url="https://github.com/veritasX1/LiCal/issues")
    about.set_copyright("© 2026 Olaf Winkler")
    # Rechtliches (card 29746c85, wie LiMail): was LiCal mitliefert und unter Ubuntu nutzt – aus shared/lizenzen
    data = load_licenses()
    if data:
        texts = data["texts"]
        for lib in data["ubuntu"]:
            if lib["text"] and texts.get(lib["text"], "").strip():
                about.add_legal_section(lib["name"], " · ".join(part for part in (lib["version"], lib["license"]) if part), Gtk.License.CUSTOM, GLib.markup_escape_text(texts[lib["text"]]))
            else:
                about.add_legal_section(lib["name"], " · ".join(part for part in (lib["version"], lib["license"]) if part), Gtk.License.UNKNOWN, None)
    about.present(parent)
    return about
