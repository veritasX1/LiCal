"""LiCal's icon like Apple's Calendar: a white squircle with today's weekday bold in red and the day
of the month very large below it. Rewritten at every start and by a daily timer (install.sh), so the
launcher always shows today."""

import subprocess
from datetime import date
from pathlib import Path
import os

APP_ID = "io.github.veritasx1.LiCal"
# As Apple writes it on the German icon: the weekday short, in title case ("So.", "Mo."), bold and red.
WEEKDAYS = ("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So.")


def squircle(x, y, size, exponent=5.0, steps=96):
    """Apple's continuous corners: a superellipse |x|^n + |y|^n = 1 instead of a rounded rectangle."""
    import math
    half = size / 2
    points = []
    for step in range(steps):
        angle = 2 * math.pi * step / steps
        c, s = math.cos(angle), math.sin(angle)
        px = half * math.copysign(abs(c) ** (2 / exponent), c)
        py = half * math.copysign(abs(s) ** (2 / exponent), s)
        points.append(f"{x + half + px:.1f},{y + half + py:.1f}")
    return "M" + " L".join(points) + " Z"


def svg(day=None):
    """Like Apple's Calendar icon (macOS 26): white face, the weekday bold in red on top, the day of the
    month very large and black below it – today's date, renewed every day."""
    day = day or date.today()
    face = squircle(40, 40, 432)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="face" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#ffffff"/>
      <stop offset="1" stop-color="#ececef"/>
    </linearGradient>
    <filter id="shadow" x="-10%" y="-10%" width="120%" height="125%">
      <feDropShadow dx="0" dy="5" stdDeviation="8" flood-color="#000000" flood-opacity="0.24"/>
    </filter>
  </defs>
  <path d="{face}" fill="url(#face)" filter="url(#shadow)"/>
  <text x="256" y="170" text-anchor="middle" font-family="Inter, Inter Variable, Ubuntu Sans, sans-serif" font-size="98" font-weight="700"
        fill="#ff3b30">{WEEKDAYS[day.weekday()]}</text>
  <text x="256" y="408" text-anchor="middle" font-family="Inter, Inter Variable, Ubuntu Sans, sans-serif" font-size="276" font-weight="500"
        fill="#000000" letter-spacing="-6">{day.day}</text>
</svg>
"""


def data_home():
    return Path(os.environ.get("XDG_DATA_HOME") or Path.home() / ".local" / "share")


def update(day=None):
    """Write today's icon into the user's icon theme (only if the launcher is installed)."""
    base = data_home() / "icons" / "hicolor"
    target = base / "scalable" / "apps" / f"{APP_ID}.svg"
    if not (data_home() / "applications" / f"{APP_ID}.desktop").exists():
        return False
    text = svg(day)
    if target.exists() and target.read_text() == text:
        return False
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
    try:
        import gi
        gi.require_version("GdkPixbuf", "2.0")
        from gi.repository import GdkPixbuf
        for size in (32, 48, 64, 128, 256, 512):
            folder = base / f"{size}x{size}" / "apps"
            folder.mkdir(parents=True, exist_ok=True)
            GdkPixbuf.Pixbuf.new_from_file_at_size(str(target), size, size).savev(str(folder / f"{APP_ID}.png"), "png", [], [])
    except Exception as error:
        print("LiCal: PNG-Icons nicht erzeugt:", error)
    subprocess.run(["gtk-update-icon-cache", "-q", "-f", "-t", str(base)], check=False)
    return True
