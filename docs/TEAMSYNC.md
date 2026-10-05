# Team-Abgleich ohne Server und zeitlich begrenzte Freigabe – Entwurf

Karten ac0d1e1a (Team-Abgleich ad hoc, jede Verbindung, NFC; Server als Rückfallebene) und c44671e4 (Kalender
eines Teammitglieds zeitlich begrenzt per QR-Code freigeben). Stand 04.10.2026, **Entwurf zur Freigabe durch
Olaf**. Der lokale Kern (Abschnitt 8, Schritt 1 – ohne Netz, nichts verlässt das Gerät) entsteht schon;
alles, was Verbindungen öffnet oder Daten weitergibt, erst nach seinem OK. Leitsatz (Olaf): „Datenschutz ist hier höchstes Gebot!“

## 1. Was erreicht werden soll

- Ein Team (Familie, Büro) sieht die Termine seiner Mitglieder – **ohne dass ein Server sie je lesen kann**.
- Abgleich über **jede gerade verfügbare Verbindung**: gleiches WLAN, Bluetooth, NFC zum Anstoßen, Datei/USB,
  und nur als Rückfallebene ein Server (eigener Pi), der lediglich verschlüsselte Päckchen zwischenlagert.
- Ein Mitglied gibt **seinen** Kalender **zeitlich begrenzt** frei (Beispiel: der Partner plant den Urlaub) –
  der Gast sieht **nur dieses Mitglied**, nur im gewählten Zeitraum, nur lesend, und nach Ablauf nichts mehr.
- Alles ist **abschaltbar**; ohne ausdrückliche Zustimmung verlässt nichts das Gerät.

## 2. Datenschutz-Grundsätze (gelten für jede Entscheidung unten)

1. **Ende-zu-Ende**: Termine verlassen ein Gerät nur verschlüsselt (AES-256-GCM). Schlüssel kennt nur, wer
   lesen darf. Der Server (falls genutzt) sieht zufällige Kennungen, Größen und Zeitpunkte – keine Namen,
   keine Titel, keine Teamzugehörigkeit im Klartext.
2. **Getrennte Schlüssel je Bereich** („scope keys“): jedes Mitglied hat einen eigenen Stream-Schlüssel; eine
   Gastfreigabe bekommt einen **eigenen, neuen** Schlüssel nur für die freigegebenen Termine. Wer einen
   Schlüssel hat, kann nie mehr entschlüsseln als diesen Bereich.
3. **Datensparsamkeit**: Jedes Mitglied wählt, wie viel das Team sieht – **Standard: nur „belegt“ und
   Abwesenheiten** (ohne Titel, Ort, Notizen). Details sind ein bewusstes Einschalten. Notizen gehen nie
   automatisch mit (wie beim QR-Teilen).
4. **Zustimmung**: Beitritt nur mit Bestätigung auf beiden Geräten (Code-Vergleich). Jede Freigabe wird vom
   Freigebenden erstellt; nichts wird „angefragt und automatisch erteilt“.
5. **Abschaltbar ohne Spuren**: „Team-Abgleich aus“ beendet Suchen, Lauschen und Senden sofort. Solange aus,
   kündigt sich das Gerät nirgends an (kein mDNS, kein Bluetooth-Advertising).
6. **Keine Cloud-Kopien**: Team- und Gastdaten liegen nur in den App-Ordnern (Android: no_backup, Ubuntu:
   Datei 600 im Ordner 700). Kein Android-Backup, keine Synchronisation über Google.
7. **Ablauf und Widerruf werden durchgesetzt** – technisch, nicht nur per Anzeige (Abschnitt 6). Ehrlich in der
   Oberfläche: Was ein Gast bereits gesehen hat, kann niemand zurückholen.

## 3. Bausteine (dieselben wie LiNotes, auf Android ohne Zusatzbibliothek)

| Zweck | Verfahren |
|---|---|
| Identität je Gerät | P-256-Schlüsselpaar, privater Teil nur auf dem Gerät (Android Keystore / Datei 600) |
| Schlüssel austauschen | ECDH (P-256) + HKDF-SHA256 |
| Inhalte verschlüsseln | AES-256-GCM, Zusatzdaten (AAD) = Stream-Kennung + Folgenummer |
| Urheberschaft | ECDSA-P-256-Signatur jeder Änderung – nur der Besitzer eines Streams kann darin schreiben |
| Kopplung per Code/QR | SPAKE2 (RFC 9382) mit 6-stelligem Code bzw. Einmal-Token aus dem QR-Code |
| Sicherheitsnummer | wie LiNotes: aus beiden öffentlichen Schlüsseln, zum Vergleichen |

Python (`cryptography`) und Kotlin (`java.security`/`javax.crypto`) mit **gemeinsamen Testvektoren** in
`shared/cases/`, wie bei allen LiCal-Regeln.

## 4. Datenmodell

- **Team**: zufällige Kennung, Name (nur lokal und verschlüsselt im Team-Stream), Mitglieder mit
  öffentlichem Schlüssel und Rolle.
- **Stream**: jedes Mitglied besitzt einen Stream – ein nur anwachsendes Protokoll seiner Änderungen
  (`put`/`delete` eines Termins). Jeder Eintrag: Folgenummer, Lamport-Uhr, Geräte-Kennung, verschlüsselter
  Inhalt, Signatur. Löschungen bleiben als „Grabstein“ erhalten, damit sie überall ankommen.
- **Zusammenführen**: pro Termin gewinnt der Eintrag mit der höchsten (Lamport-Uhr, Geräte-Kennung) –
  deterministisch, reihenfolgeunabhängig, idempotent. Dadurch ist es **egal, über welchen Weg** und in welcher
  Reihenfolge Einträge ankommen (WLAN heute, Bluetooth morgen, Datei übermorgen).
- **Abgleich**: Geräte tauschen „Stand je Stream“ (höchste Folgenummer) und schicken nur Fehlendes.
- **Sichtweite**: ein Mitglied veröffentlicht in den Team-Stream eine *Fassung* seiner Termine nach seiner
  Datenschutz-Einstellung (belegt / Abwesenheit / Details). Die eigenen Kalender bleiben unverändert privat;
  der Team-Stream ist eine Ableitung, keine Kopie des privaten Kalenders.

## 5. Verbindungswege

| Weg | Wofür | Ubuntu | Android | Datenschutz-Detail |
|---|---|---|---|---|
| Gleiches WLAN | normaler Abgleich in der Nähe | Avahi (mDNS) + TCP | NSD + TCP | Dienstname = wechselnde Zufallskennung, nie Name/Team; nur während die App offen ist und der Abgleich an ist |
| Bluetooth | unterwegs, ohne WLAN | BlueZ RFCOMM | RFCOMM/BLE | nur sichtbar, solange „Abgleichen“-Blatt offen ist |
| NFC | Telefone aneinanderhalten **zum Anstoßen** | – (selten vorhanden) | HCE + Reader-Mode | NFC trägt nur Einladung/Einmal-Token; die Daten fließen danach über WLAN/Bluetooth |
| QR-Code | Einladung, Gastfreigabe | Anzeige | Anzeige + Scanner (vorhanden) | Token gilt einmal und 10 Minuten |
| Datei / USB | ganz ohne Netz | „Abgleich-Datei sichern/öffnen“ | Teilen/Öffnen | Datei ist verschlüsselt; ohne Schlüssel wertlos |
| Server (Rückfall) | wenn sich Geräte nie treffen | HTTPS | HTTPS | „Briefkasten“ auf dem eigenen Pi: speichert nur verschlüsselte Einträge unter Zufallskennungen, löscht nach Ablauf (TTL) |

Jede Verbindung beginnt mit einem gegenseitig authentifizierten Handschlag (ECDH aus Identitäts- und
Einmalschlüsseln, Muster wie Noise „KK“): Ein fremdes Gerät im selben WLAN erfährt nicht einmal, welches Team
hier abgleicht.

## 6. Zeitlich begrenzte Freigabe eines Mitglieds (c44671e4)

Beispiel: Olaf gibt Mia für die Urlaubsplanung seine Termine von Juni bis August frei, 14 Tage lang.

1. **Olaf** wählt „Meinen Kalender freigeben …“: Zeitraum (1.6.–31.8.), Umfang (**nur frei/belegt** oder
   Details, Standard: frei/belegt), gültig bis (Standard 14 Tage, höchstens 90), nur lesen (immer).
2. LiCal erzeugt einen **neuen Freigabe-Schlüssel** und einen **Gast-Stream**, der *nur* Olafs Termine im
   Zeitraum in der gewählten Fassung enthält – nichts von anderen Teammitgliedern, nichts außerhalb des
   Zeitraums. Der Team-Schlüssel verlässt das Gerät nie.
3. Ein **QR-Code** zeigt: Olafs Schlüssel-Fingerabdruck, ein Einmal-Token (128 Bit, 10 Minuten gültig),
   Verbindungshinweise. **Mia** scannt; beide Geräte verbinden sich (WLAN/Bluetooth), prüfen sich über
   Token + Fingerabdruck (SPAKE2), Mia erhält Schlüssel und Gast-Stream. Ein abfotografierter QR-Code ist
   danach wertlos (Token verbraucht).
4. Bei Mia erscheint ein nur-lesbarer Kalender **„Olaf (bis 18.10.)“**. Sie kann nichts ändern – jede Änderung
   müsste Olafs Signatur tragen.
5. **Ablauf**: Mias LiCal löscht Kalender und Schlüssel zum Ablaufzeitpunkt (auch offline; Ablauf steht
   signiert in der Freigabe). Olafs Gerät veröffentlicht nichts mehr für diese Freigabe; der Server-Briefkasten
   (falls genutzt) löscht per TTL. **Widerruf**: „Freigabe beenden“ wirkt sofort für alles Weitere.
6. Ehrlicher Hinweis in beiden Blättern: „Was bereits angezeigt wurde, kann nicht zurückgeholt werden.“

Ohne Server muss Olafs Gerät Mia für spätere Änderungen wieder „treffen“ (WLAN zu Hause, Bluetooth). Mit
Briefkasten auf dem Pi kommen Änderungen auch aus der Ferne an – weiterhin nur verschlüsselt.

## 7. Bedienung (Apple-Stil)

- Seitenleiste / Kalenderblatt: Abschnitt **„Team“** mit Mitgliedern als Kalender (Farbe je Person), darunter
  **„Geteilt mit mir“** (Gastfreigaben mit Ablaufdatum).
- **Einstellungen › Team-Abgleich**: Schalter (aus = komplett still), Verbindungswege einzeln abwählbar,
  „Was das Team sieht: Belegt · Abwesenheiten · Details“.
- „Abgleichen“-Blatt beim Zusammentreffen: zeigt, mit wem und worüber gerade abgeglichen wird.
- Vertretung (471febc2) wird dann ein Teammitglied statt nur ein Name.

## 8. Reihenfolge der Umsetzung

1. **Kern** (beide Plattformen, Zwillingstests): Krypto-Bausteine mit Testvektoren, Streams, Signaturen,
   Zusammenführen, Grabsteine, Sichtweite-Fassungen.
2. **Gastfreigabe über QR + WLAN** und Abgleich-**Datei** – damit ist c44671e4 ohne Server nutzbar.
3. **Team über WLAN** (mDNS/NSD), Einladung per QR/Code.
4. **Bluetooth**, **NFC-Anstoßen** (Android).
5. **Briefkasten-Server** auf dem Pi (Karte 1445caee) – nur nach Olafs OK, Sicherung vorher.

## 9. Fragen an Olaf

1. Standard für „Was das Team sieht“: **nur belegt + Abwesenheiten** (Vorschlag) oder gleich Details?
2. Darf ein Mitglied Termine anderer anlegen/ändern (wie Apples Delegierung) – oder vorerst nur lesen?
3. Gäste **ohne LiCal** (Ansicht im Browser) bräuchten den Server – später oder gar nicht?
4. Höchstdauer einer Gastfreigabe: 90 Tage in Ordnung?
