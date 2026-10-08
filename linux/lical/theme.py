"""Colors and type like macOS 26 Calendar: Apple's system colors (light and dark), label and separator
grays, and Inter (free, close to SF Pro) for all text. Android: Theme.kt."""

import os
from pathlib import Path

import gi

gi.require_version("Pango", "1.0")
gi.require_version("PangoCairo", "1.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Pango, PangoCairo
from .i18n import _, language

FONT = "Inter"
FONT_FILE = Path(__file__).resolve().parent / "fonts" / "InterVariable.ttf"

# Apple system colors: (light, dark) as 0…1 RGB.
SYSTEM = {
    "red": ((1.0, 0.231, 0.188), (1.0, 0.271, 0.227)),
    "orange": ((1.0, 0.584, 0.0), (1.0, 0.624, 0.039)),
    "yellow": ((1.0, 0.8, 0.0), (1.0, 0.839, 0.039)),
    "green": ((0.204, 0.78, 0.349), (0.188, 0.82, 0.345)),
    "mint": ((0.0, 0.78, 0.745), (0.388, 0.902, 0.886)),
    "teal": ((0.188, 0.69, 0.78), (0.251, 0.784, 0.878)),
    "blue": ((0.0, 0.478, 1.0), (0.039, 0.518, 1.0)),
    "indigo": ((0.345, 0.337, 0.839), (0.369, 0.361, 0.902)),
    "purple": ((0.686, 0.322, 0.871), (0.749, 0.353, 0.949)),
    "pink": ((1.0, 0.176, 0.333), (1.0, 0.216, 0.373)),
    "brown": ((0.635, 0.518, 0.369), (0.675, 0.557, 0.408)),
}
CALENDAR_COLORS = ("red", "orange", "yellow", "green", "blue", "purple", "brown", "teal", "pink", "indigo", "mint")


def load_font():
    """Inter ships with LiCal – registered with the font map, nothing installed on the system."""
    if FONT_FILE.exists():
        try:
            PangoCairo.FontMap.get_default().add_font_file(str(FONT_FILE))
        except Exception as error:  # an old Pango: fall back to the system font
            print("LiCal: Schrift nicht geladen:", error)


# Paper is white: while printing everything draws in the light colors (printing.py).
PRINTING = False


def dark():
    return not PRINTING and Adw.StyleManager.get_default().get_dark()


def system(name):
    light, night = SYSTEM.get(name, SYSTEM["blue"])
    return night if dark() else light


class Palette:
    """The colors of the moment (light or dark), as the views draw them."""

    def __init__(self):
        night = dark()
        self.dark = night
        self.background = (0.118, 0.118, 0.118) if night else (1.0, 1.0, 1.0)
        self.weekend = (0.141, 0.141, 0.145) if night else (0.965, 0.965, 0.969)
        self.label = (1.0, 1.0, 1.0) if night else (0.0, 0.0, 0.0)
        self.secondary = (0.596, 0.596, 0.616) if night else (0.525, 0.525, 0.545)
        self.tertiary = (0.388, 0.388, 0.4) if night else (0.737, 0.737, 0.753)
        self.separator = (0.235, 0.235, 0.243) if night else (0.882, 0.882, 0.89)
        self.red = system("red")
        self.today_text = (1.0, 1.0, 1.0)

    def event_fill(self, color, selected=False):
        """Tinted background of an event (all-day pill, timeline block)."""
        if selected:
            return color + (1.0,)
        return color + ((0.32,) if self.dark else (0.2,))

    def event_text(self, color, selected=False):
        """Text on a tinted event: a deep shade of the color in light mode, a light one in dark."""
        if selected:
            return (1.0, 1.0, 1.0)
        if self.dark:
            return tuple(min(1.0, channel * 0.45 + 0.55) for channel in color)
        return tuple(channel * 0.55 for channel in color)


# ============================================================
# TEXT
# ============================================================

_fonts = {}


def font(size, weight=400):
    key = (size, weight)
    description = _fonts.get(key)
    if description is None:
        description = Pango.FontDescription.from_string(f"{FONT}, Ubuntu Sans, sans-serif")
        description.set_absolute_size(size * Pango.SCALE)
        description.set_weight(weight)
        description.set_variations(f"wght={weight}")
        _fonts[key] = description
    return description


def layout(cr, text, size, weight=400, width=None, align="left", tabular=False):
    lay = PangoCairo.create_layout(cr)
    lay.set_font_description(font(size, weight))
    if tabular:
        attributes = Pango.AttrList()
        attributes.insert(Pango.attr_font_features_new("tnum"))
        lay.set_attributes(attributes)
    lay.set_text(text, -1)
    if width is not None:
        lay.set_width(int(max(1, width) * Pango.SCALE))
        lay.set_ellipsize(Pango.EllipsizeMode.END)
        lay.set_alignment({"left": Pango.Alignment.LEFT, "right": Pango.Alignment.RIGHT, "center": Pango.Alignment.CENTER}[align])
    return lay


def text(cr, value, x, y, size, color, weight=400, width=None, align="left", tabular=False, baseline=False):
    """Draw text with its top (or, baseline=True, its baseline) at y. Returns its width."""
    lay = layout(cr, value, size, weight, width, align, tabular)
    ink, logical = lay.get_pixel_extents()
    if width is None and align != "left":
        x -= logical.width if align == "right" else logical.width / 2
    cr.set_source_rgba(*(color if len(color) == 4 else color + (1.0,)))
    cr.move_to(x, y - (lay.get_baseline() / Pango.SCALE if baseline else 0))
    PangoCairo.show_layout(cr, lay)
    return logical.width


def text_width(cr, value, size, weight=400):
    return layout(cr, value, size, weight).get_pixel_extents()[1].width


def rounded(cr, x, y, width, height, radius):
    import math
    radius = max(0.0, min(radius, width / 2, height / 2))
    cr.new_sub_path()
    cr.arc(x + width - radius, y + radius, radius, -math.pi / 2, 0)
    cr.arc(x + width - radius, y + height - radius, radius, 0, math.pi / 2)
    cr.arc(x + radius, y + height - radius, radius, math.pi / 2, math.pi)
    cr.arc(x + radius, y + radius, radius, math.pi, 1.5 * math.pi)
    cr.close_path()


def circle(cr, x, y, radius):
    import math
    cr.new_sub_path()
    cr.arc(x, y, radius, 0, 2 * math.pi)
    cr.close_path()


MONTHS = (_("Januar"), _("Februar"), _("März"), _("April"), _("Mai"), _("Juni"), _("Juli"), _("August"), _("September"), _("Oktober"), _("November"), _("Dezember"))
WEEKDAYS_SHORT = tuple(_("Mo Di Mi Do Fr Sa So").split())
WEEKDAYS_LONG = (_("Montag"), _("Dienstag"), _("Mittwoch"), _("Donnerstag"), _("Freitag"), _("Samstag"), _("Sonntag"))
WEEKDAYS_LETTER = tuple(_("M D M D F S S").split())


# Dates as each language writes them (de: „Montag, 5. Oktober 2026“, en: „Monday, October 5, 2026“,
# fr: „lundi 5 octobre 2026“) – never glue „{day}. {month}“ together elsewhere.
def short_month(month):
    name = MONTHS[month - 1]
    if len(name) <= 4:
        return name
    return name[:3] + ("" if language() == "en" else ".")


def day_month(day, short=False):
    month = short_month(day.month) if short else MONTHS[day.month - 1]
    if language() == "en":
        return f"{month} {day.day}"
    if language() == "fr":
        return f"{'1er' if day.day == 1 and not short else day.day} {month}"
    return f"{day.day}. {month}"


def date_text(day, weekday=True, year=True, short=False):
    """A whole date: weekday (long, or short with short=True), day, month, year."""
    lang = language()
    text = day_month(day, short)
    if year:
        text += (", " if lang == "en" else " ") + str(day.year)
    if weekday:
        long_name = WEEKDAYS_LONG[day.weekday()]
        if not short:
            name = long_name
        elif lang == "de":
            name = WEEKDAYS_SHORT[day.weekday()] + "."
        else:  # Mon · lun.
            name = long_name[:3] + ("." if lang == "fr" else "")
        text = f"{name}{',' if lang != 'fr' else ''} {text}"
    return text


def numeric_date(day, year=True):
    """5.10.2026 · 10/5/2026 · 05/10/2026"""
    lang = language()
    if lang == "en":
        return f"{day.month}/{day.day}" + (f"/{day.year}" if year else "")
    if lang == "fr":
        return f"{day.day:02d}/{day.month:02d}" + (f"/{day.year}" if year else "")
    return f"{day.day}.{day.month}." + (str(day.year) if year else "")

if os.environ.get("LICAL_NO_FONT") != "1":
    load_font()


def hatch(cr, x, y, width, height, color, alpha=0.35):
    """Diagonal stripes over an absence (Urlaub, Krank …), so it reads as "away" at a glance."""
    cr.save()
    rounded(cr, x, y, width, height, 4)
    cr.clip()
    cr.set_source_rgba(*color, alpha)
    cr.set_line_width(2)
    step = 7
    position = x - height
    while position < x + width:
        cr.move_to(position, y + height)
        cr.line_to(position + height, y)
        position += step
    cr.stroke()
    cr.restore()
