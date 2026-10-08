#!/usr/bin/env python3
"""Pictures of the Ubuntu app with example data, light and dark – never touches real data:

    python3 tools/shots-ubuntu.py <folder> [month|week|day|year …]
    LICAL_LANGUAGE=en|fr …    – app and example events in English or French"""

import os
import sys
import tempfile
import time
from datetime import date
from pathlib import Path

SCRATCH = tempfile.mkdtemp(prefix="lical-shots-")
os.environ["XDG_DATA_HOME"] = os.environ["XDG_CONFIG_HOME"] = os.environ["XDG_CACHE_HOME"] = SCRATCH
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "linux"))

import gi  # noqa: E402

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
gi.require_version("Graphene", "1.0")
from gi.repository import Adw, GLib, Graphene, Gtk  # noqa: E402

from lical import app as app_module, store as store_module  # noqa: E402

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "shots")
MODES = sys.argv[2:] or ["month"]
OUT.mkdir(parents=True, exist_ok=True)


def settle(seconds=0.6):
    context = GLib.MainContext.default()
    end = time.time() + seconds
    while time.time() < end:
        context.iteration(False)
        time.sleep(0.005)


def shot(widget, name):
    width, height = widget.get_width(), widget.get_height()
    paintable = Gtk.WidgetPaintable.new(widget)
    snapshot = Gtk.Snapshot()
    paintable.snapshot(snapshot, width, height)
    node = snapshot.to_node()
    texture = widget.get_native().get_renderer().render_texture(node, Graphene.Rect().init(0, 0, width, height))
    texture.save_to_png(str(OUT / name))
    print("Bild", name, flush=True)


# The example events in the other languages (made up, like the German ones).
DEMO = {
    "en": {"Privat": "Personal", "Arbeit": "Work", "Familie": "Family",
           "Jour fixe Team": "Team meeting", "Besprechungsraum 2": "Meeting room 2", "Kundentermin Müller": "Client meeting Miller",
           "Köln": "Cologne", "Zahnarzt": "Dentist", "Mittag mit Jens": "Lunch with Jens", "Elternabend": "Parents’ evening",
           "Grundschule": "Primary school", "Laufen": "Running", "Kino mit Mia": "Cinema with Mia", "Mias Geburtstag": "Mia’s birthday",
           "Urlaub Kreta": "Holiday in Crete", "Messe Köln": "Cologne trade fair", "Koelnmesse": "Koelnmesse",
           "Sprint-Planung": "Sprint planning", "Steuerberater": "Tax adviser", "Oma besuchen": "Visit Grandma",
           "Tag der offenen Tür": "Open day", "Wochenmarkt": "Farmers’ market", "1:1 mit Sabine": "1:1 with Sabine",
           "Abgabe Konzept": "Concept due"},
    "fr": {"Privat": "Personnel", "Arbeit": "Travail", "Familie": "Famille",
           "Jour fixe Team": "Réunion d’équipe", "Besprechungsraum 2": "Salle de réunion 2", "Kundentermin Müller": "Rendez-vous client Martin",
           "Köln": "Cologne", "Zahnarzt": "Dentiste", "Mittag mit Jens": "Déjeuner avec Jens", "Elternabend": "Réunion parents-profs",
           "Grundschule": "École primaire", "Laufen": "Course à pied", "Kino mit Mia": "Cinéma avec Mia", "Mias Geburtstag": "Anniversaire de Mia",
           "Urlaub Kreta": "Vacances en Crète", "Messe Köln": "Salon de Cologne", "Koelnmesse": "Koelnmesse",
           "Sprint-Planung": "Planification du sprint", "Steuerberater": "Conseiller fiscal", "Oma besuchen": "Visite chez mamie",
           "Tag der offenen Tür": "Portes ouvertes", "Wochenmarkt": "Marché", "1:1 mit Sabine": "Point avec Sabine",
           "Abgabe Konzept": "Remise du concept", "Review Release 2.2": "Revue de la version 2.2"},
}.get(os.environ.get("LICAL_LANGUAGE"), {})


class Shots(app_module.Application):
    def do_activate(self):
        try:
            app_module.load_css()
            store = store_module.Store(Path(SCRATCH) / "kalender.json")
            store.events = store_module.demo_events(date.today())
            for event in store.events:
                for key in ("title", "location"):
                    if event.get(key) in DEMO:
                        event[key] = DEMO[event[key]]
            for calendar in store.calendars:
                calendar["name"] = DEMO.get(calendar.get("name"), calendar.get("name"))
            store.save()
            window = app_module.CalendarWindow(self, store)
            window.present()
            settle()
            for dark in (False, True):
                Adw.StyleManager.get_default().set_color_scheme(Adw.ColorScheme.FORCE_DARK if dark else Adw.ColorScheme.FORCE_LIGHT)
                for mode in MODES:
                    if mode == "day":  # a busy day of the example week
                        window.go_to(date.today() - __import__("datetime").timedelta(days=date.today().weekday() - 1))
                    else:
                        window.go_to(date.today())
                    window.set_mode(mode)
                    settle()
                    shot(window, f"{mode}{'-dunkel' if dark else ''}.png")
        except Exception:
            import traceback
            traceback.print_exc()
        sys.stdout.flush()
        os._exit(0)


Shots(application_id="io.github.veritasx1.LiCalShots").run([])
