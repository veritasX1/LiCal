# Datenformat (Ubuntu und Android gleich)

Kalender: `{"id", "name", "color": "red|orange|yellow|green|blue|purple|brown|teal|pink|indigo|mint", "visible": bool}`

Termin: `{"id", "calendar", "title", "allDay": bool, "start", "end", "location"?, "notes"?, "url"?, "rrule"?, "exdates"?,
"absence"?, "deputy"?, "alerts"?, "tz"?}`
- Zeiten als Wanduhrzeit `2026-10-04T09:00`, ganztägig als Datum `2026-10-04`.
- Ganztägiges `end` ist der Tag nach dem letzten Tag (wie iCalendar DTEND).
- `rrule`: Teilmenge von iCalendar RRULE – FREQ=DAILY|WEEKLY|MONTHLY|YEARLY, INTERVAL, COUNT, UNTIL=YYYYMMDD,
  BYDAY (wöchentlich). `exdates`: ausgelassene Tage `YYYY-MM-DD`.
- `absence`: Abwesenheit (urlaub, krank, weiterbildung, freistellung, abwesend, dienstreise, anderer_ort, homeoffice,
  office, anwesend) – immer ganztägig; `deputy`: Name der Vertretung.
- `alerts`: bis zu zwei Hinweise in Minuten vor Beginn (0 = zum Zeitpunkt). Ganztägig ab Mitternacht des ersten
  Tags gezählt: −540 = am Tag um 9:00, 900 = 1 Tag vorher um 9:00. Termine aus Handy-Kalendern (`dev:`) behalten
  ihre Hinweise in Androids Kalenderspeicher.
- Termin als QR-Code: iCalendar (VEVENT) ohne id, Kalender, Abwesenheit, Vertretung; Notizen nur auf Wunsch.
- `tz`: Zeitzone eines Termins mit Uhrzeit (IANA, z. B. `America/New_York`); `start`/`end` sind dann Uhrzeiten dort.
  Wiederholungen laufen in dieser Zone (Sommerzeit dort), angezeigt wird in der Ortszeit. Ohne `tz`: schwebend (Ortszeit).
