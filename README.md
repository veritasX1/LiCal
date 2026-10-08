# LiCal

Ein Kalender für Ubuntu und Android im Aussehen und Bedienkonzept von Apples Kalender
(macOS 26 auf Ubuntu, iOS 26 auf Android). Ohne Konto, ohne Werbung, ohne Datensammlung.
Seite: https://lisoftware.de/lical/

- `linux/` – Ubuntu-App (Python, GTK4/libadwaita, Ansichten selbst gezeichnet mit Cairo)
- `android/` – Android-App (Kotlin, Jetpack Compose)
- `shared/cases/` – gemeinsame Testfälle der Kalenderregeln (Python schreibt, Kotlin prüft)
- `docs/` – Plan, Datenformat, Beschreibung von Apples Kalender als Vorbild (die Bildvorlagen bleiben privat)

Start: `cd linux && python3 -m lical` · Bilder mit Beispieldaten: `python3 tools/shots-ubuntu.py <ordner> month week day year`
· Tests: `python3 linux/tests/test_rules.py`

Schrift: Inter (SIL Open Font License, `linux/lical/fonts/Inter-LICENSE.txt`).
