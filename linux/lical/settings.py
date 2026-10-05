"""LiCal's settings on this computer (~/.config/lical/einstellungen.json, private) – like the Mac's
Calendar > Settings: so far the time zone support (card 7a9187d6). The holidays' Bundesland lives with
the calendars (store)."""

import json
import os
from pathlib import Path
from .i18n import _

DEFAULTS = {"timeZones": False}


def path():
    return Path(os.environ.get("XDG_CONFIG_HOME") or Path.home() / ".config") / "lical" / "einstellungen.json"


def load():
    try:
        return {**DEFAULTS, **json.loads(path().read_text())}
    except (OSError, ValueError):
        return dict(DEFAULTS)


def save(values):
    target = path()
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = target.with_suffix(".tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as handle:
        json.dump(values, handle)
    os.replace(temporary, target)


def get(key):
    return load().get(key, DEFAULTS.get(key))


def put(key, value):
    values = load()
    values[key] = value
    save(values)


def zone_label(name):
    """"America/New_York" → "New York (Amerika)" – how the inspector lists zones."""
    regions = {"Africa": "Afrika", "America": "Amerika", "Antarctica": "Antarktis", "Asia": "Asien", "Atlantic": "Atlantik",
               "Australia": "Australien", "Europe": "Europa", "Indian": _("Indischer Ozean"), "Pacific": "Pazifik"}
    if "/" not in name:
        return name
    region, city = name.split("/", 1)
    return f"{city.split('/')[-1].replace('_', ' ')} ({regions.get(region, region)})"


def zones():
    """All IANA zones that are places (no "Etc/…"), sorted by their label."""
    from zoneinfo import available_timezones
    names = [name for name in available_timezones()
             if "/" in name and name.split("/")[0] in ("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")]
    return sorted(names, key=zone_label)
