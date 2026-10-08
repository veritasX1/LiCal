#!/usr/bin/env python3
"""Rechtshinweis (Richard/Michelle 08.10., card 29746c85; wie LiMail 91b79ccc): baut shared/lizenzen/lizenzen.json –
LiCal selbst, was es mitliefert (Inter, QR-Code-Bibliothek) und was es unter Ubuntu vom System nutzt, jeweils mit Lizenz.
Ubuntu zeigt es im Info-Dialog unter „Rechtliches“, Android unter Über LiCal › Lizenzen.

    python3 tools/lizenzen.py

Die Texte kommen aus den mitgelieferten Dateien selbst (Inter-LICENSE.txt, Kopf von qrcodegen.py) und aus
/usr/share/common-licenses (GPL-3). Neue Bibliothek: unten eintragen, Skript laufen lassen, Datei committen."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "shared" / "lizenzen" / "lizenzen.json"
LICAL = ROOT / "linux" / "lical"

# Name, Version, Lizenz (SPDX), Text-Schlüssel ("" = kein Text, Systempaket), Verwendung
SHIPPED = [
    ("Inter", "4.001", "OFL-1.1", "Inter", "Schrift des App-Symbols (mitgeliefert)"),
    ("QR Code generator library (Project Nayuki)", "", "MIT", "qrcodegen", "QR-Codes zum Teilen (mitgeliefert)"),
]
# Android (card 86f796be): what the app ships, shown under Über LiCal › Lizenzen
ANDROID = [
    ("Inter", "4.001", "OFL-1.1", "Inter", "Schrift der App"),
    ("ZXing core", "3.5.3", "Apache-2.0", "Apache-2.0", "QR-Codes zeichnen und lesen"),
    ("CameraX", "1.4.0", "Apache-2.0", "Apache-2.0", "Kamera für QR-Codes"),
    ("Android Jetpack (androidx.*, Compose)", "BOM 2024.09.03", "Apache-2.0", "Apache-2.0", "Oberfläche"),
    ("Kotlin, Guava ListenableFuture", "2.0.21 / 1.0", "Apache-2.0", "Apache-2.0", "Programmiersprache und Hilfsbibliotheken"),
]
UBUNTU = [
    ("GTK 4, libadwaita, GLib, Pango, GdkPixbuf", "Ubuntu", "LGPL-2.1-or-later", "", "Oberfläche (Ubuntu-Pakete, nicht mitgeliefert)"),
    ("Graphene", "Ubuntu", "MIT", "", "Grafik der Oberfläche (Ubuntu-Paket)"),
    ("PyGObject", "Ubuntu", "LGPL-2.1-or-later", "", "Python-Anbindung (Ubuntu-Paket)"),
    ("python3-cryptography", "Ubuntu", "Apache-2.0 / BSD", "", "Verschlüsselung (Ubuntu-Paket)"),
    ("Python 3", "Ubuntu", "PSF-2.0", "", "Programmiersprache (Ubuntu-Paket)"),
]


def reflow(text):
    """Calm paragraphs like iOS „Rechtliches“ (Richard 08.10., Michelle 486dbb6a): hard line breaks inside a paragraph
    become spaces, indentation goes, separator lines (----) are dropped; headings (ALL CAPS or centred) and list
    items start their own line, the lines below an item join it."""
    out, para = [], []
    def flush():
        if para:
            out.append(" ".join(para))
            para.clear()
    def blank():
        flush()
        if out and out[-1]:
            out.append("")
    for line in text.splitlines():
        stripped = line.strip()
        indent = len(line) - len(line.lstrip())
        letters = [c for c in stripped if c.isalpha()]
        if not stripped:
            blank()
        elif re.match(r"^#{1,6}\s", stripped):
            blank(); out.append(stripped.lstrip("#").strip()); out.append("")   # Markdown heading, without the #
        elif len(stripped) >= 3 and not set(stripped) - set("-=_*~ "):
            blank()                                   # ---------- separator
        elif (letters and all(c.isupper() for c in letters) and len(stripped) <= 80) or (indent >= 8 and len(stripped) <= 60 and not para):
            blank(); out.append(stripped); out.append("")   # heading
        elif re.match(r"^([-*•]|\(?[0-9]{1,2}[.)]|\([a-z]{1,3}\))\s", stripped):
            flush(); para.append(stripped)            # list item: own line, following lines join it
        else:
            para.append(stripped)
    flush()
    while out and not out[-1]:
        out.pop()
    text = "\n".join(out)
    return re.sub(r"\n{3,}", "\n\n", text).strip() + "\n"


def qrcodegen_text():
    """The MIT notice from the head of qrcodegen.py, without the comment marks."""
    lines = []
    for line in (LICAL / "qrcodegen.py").read_text(encoding="utf-8").splitlines():
        if not line.startswith("#"):
            break
        lines.append(line[1:].strip())
    return "\n".join(lines).strip()


def main():
    texts = {
        "GPL-3": Path("/usr/share/common-licenses/GPL-3").read_text(encoding="utf-8"),
        "Apache-2.0": Path("/usr/share/common-licenses/Apache-2.0").read_text(encoding="utf-8"),
        "Inter": (LICAL / "fonts" / "Inter-LICENSE.txt").read_text(encoding="utf-8"),
        "qrcodegen": qrcodegen_text(),
    }
    texts = {name: reflow(text) for name, text in texts.items()}
    entry = lambda e: dict(zip(("name", "version", "license", "text", "use"), e))
    data = {
        "app": {"name": "LiCal", "license": "GPL-3.0-or-later", "text": "GPL-3", "copyright": "© 2026 Olaf Winkler"},
        "android": [entry(e) for e in ANDROID],
        "ubuntu": [entry(e) for e in SHIPPED + UBUNTU],
        "texts": texts,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}: {len(data['android'])} Android, {len(data['ubuntu'])} Ubuntu, {len(texts)} Lizenztexte, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
