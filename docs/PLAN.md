# LiCal – Plan

Board „Projekt LiCal“ auf LiNotes (Karten-IDs in Commits). Maßstab: Apples Kalender, Referenzbilder in
`docs/reference`. Tante Erna zuerst: ab Werk einfach, Profi-Funktionen zuschaltbar.

## Reihenfolge
1. Ansichten (Karte 93f6a101): Monat, Woche/Tag, Jahr – Ubuntu wie macOS, Android wie iOS. ← jetzt
2. Termin anlegen/bearbeiten: Mac-Inspektor am Termin, iPhone-Blatt; Wiederholung, Erinnerung, Ort, Notiz.
3. Speicherung und Übernahme der Teamkalender-Daten (Android: App-Daten, Ubuntu: SQLite).
4. Eigener Kalenderdienst auf dem Pi (1445caee) und Abgleich; Teams/Abwesenheiten (471febc2).

## Gemeinsame Regeln (Zwilling)
`linux/lical/rules.py` ↔ `android/…/Rules.kt`: Monatsraster, Wiederholungen (RRULE-Teilmenge),
Lage der Termine in Monatszeilen, Überlappung in Tag/Woche. Testfälle: `shared/cases/rules.json`.

## Datenformat
Siehe `docs/DATA.md` (iCalendar-nah, damit ein CalDAV-Dienst später passt).
