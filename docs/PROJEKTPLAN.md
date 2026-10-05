# Projektplan LiCal

Stand: 5. Oktober 2026 · Version 0.1 Beta (Ubuntu und Android)

## Ziel
Ein Kalender im Aussehen und Bedienkonzept von Apples Kalender – auf Ubuntu wie auf dem Mac, auf Android wie auf dem iPhone –
mit Abstimmung im Team, ohne dass Termine bei einem fremden Anbieter liegen.

## Grundsätze
- **Datenschutz ist das oberste Gebot:** Ende-zu-Ende verschlüsselt, Freigabe nur für ein Mitglied und einen Zeitraum, nichts ohne Zustimmung.
- **Apples Gestaltungsrichtlinien** in Aussehen und Bedienung.
- **Kein Konto-Zwang:** funktioniert vollständig auf dem Gerät; ein eigener Server ist nur Rückfallebene.

## Erreicht (0.1 Beta)
- Ansichten Tag, Woche, Monat, Jahr; Schnelleingabe („Mittag mit Jens morgen 12:30“); Wiederholungen mit Ausnahmen.
- Hinweise und Erinnerungen (Ubuntu als Benutzerdienst, Android mit exakten Weckern).
- Feiertage je Bundesland, Zeitzonen, Drucken; Abwesenheiten mit Vertretung.
- Termine per QR-Code weitergeben; Kalender des Android-Geräts einbinden (z. B. per DAVx⁵).
- Widgets für den Startbildschirm; LiCal als Standard-App für Kalender auf Ubuntu und Android.

## Nächste Meilensteine
1. **Team-Abgleich ohne Server** (ad hoc, WLAN/Bluetooth/NFC, Ende-zu-Ende verschlüsselt) und zeitlich begrenzte Freigabe
   eines Kalenders per QR-Code – Entwurf steht in `docs/TEAMSYNC.md`, Umsetzung nach Freigabe der offenen Fragen (§ 9).
2. **Konten:** eigener Server, später Google/Microsoft über den eigenen Server.
3. **Mehrsprachig:** Englisch und Französisch.
4. **1.0:** Tests auf mehreren Geräten, F-Droid.

## Risiken und offene Fragen
- Standard-Sichtbarkeit, Vertretungsrechte, Gäste im Browser und maximale Freigabedauer (TEAMSYNC § 9) sind noch zu entscheiden.
- Kalender anderer Anbieter: Abgleich ohne die Privatsphäre aufzuweichen.

## Arbeitsweise
Board „Projekt LiCal“; Kalenderregeln als gemeinsame Testfälle (`shared/cases`), Python schreibt, Kotlin prüft.
Tests: `python3 linux/tests/test_rules.py` und die übrigen Dateien in `linux/tests`.
