"""Settings like the Mac's Calendar > Settings (Ctrl+,): time zone support and the holidays' Bundesland."""

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gtk

from . import holidays, settings
from .i18n import _


def show_preferences(window):
    dialog = Adw.PreferencesDialog(title=_("Einstellungen"))
    page = Adw.PreferencesPage(title=_("Allgemein"), icon_name="preferences-system-symbolic")
    general = Adw.PreferencesGroup(title=_("Erweitert"))
    zones = Adw.SwitchRow(title=_("Zeitzonen-Unterstützung"),
                          subtitle=_("Termine können eine eigene Zeitzone haben und erscheinen in der Ortszeit dieses Computers."))
    zones.set_active(bool(settings.get("timeZones")))
    zones.connect("notify::active", lambda row, _p: (settings.put("timeZones", row.get_active()), window.refresh()))
    general.add(zones)
    # Language: like the system or chosen here – takes effect at the next start.
    from . import i18n
    codes = [None] + list(i18n.LANGUAGES)
    language = Adw.ComboRow(title=_("Sprache"), subtitle=_("Die Sprache wechselt beim nächsten Start von LiCal."),
                            model=Gtk.StringList.new([_("Wie das System")] + list(i18n.LANGUAGES.values())))
    chosen = settings.get("sprache")
    language.set_selected(codes.index(chosen) if chosen in codes else 0)
    language.connect("notify::selected", lambda row, _p: i18n.set_language(codes[row.get_selected()]))
    general.add(language)
    page.add(general)
    days = Adw.PreferencesGroup(title=_("Feiertage"), description=_("Gesetzliche Feiertage, auf diesem Computer berechnet."))
    keys = [key for key, _name in holidays.STATES]
    state = Adw.ComboRow(title="Bundesland", model=Gtk.StringList.new([name for _key, name in holidays.STATES]))
    state.set_selected(keys.index(window.store.holidays.get("state", "")) if window.store.holidays.get("state", "") in keys else 0)
    state.connect("notify::selected", lambda row, _p: window.store.set_holidays(state=keys[row.get_selected()]))
    shown = Adw.SwitchRow(title=_("Feiertage zeigen"))
    shown.set_active(bool(window.store.holidays.get("visible", True)))
    shown.connect("notify::active", lambda row, _p: window.store.set_holidays(visible=row.get_active()))
    days.add(shown)
    days.add(state)
    page.add(days)
    dialog.add(page)
    dialog.controls = {"zones": zones, "state": state, "shown": shown}
    dialog.present(window)
    return dialog
