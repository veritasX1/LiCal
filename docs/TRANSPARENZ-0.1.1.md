# LiCal 0.1.1 Beta – Transparenzbericht

> Erstellt von Richard 🟡 (Tester), Stand 08.10.2026 16:45, Vier-Augen-Prüfung durch Michelle 🔵 (Stichproben bestätigt). Mit „⏳“ markierte Stellen sind bekannte, noch nicht geprüfte Punkte.
> Geprüfte Fassungen: Quelltext 25d3465 (sauberer Klon), Android-Testfassung 11:43:16 (d5bc5d45…, debug, gleiche Manifeste und Bibliotheken wie Release). Release-APK 0.1.1 (Code 2, 16:09:51, 4208747): SHA-256 ba043dc212c77a637dc14dc20a6348565549eab30b94e7084bcc319aa1dd7c11, Release-Schlüssel b53ba4b1…, R8, nicht debuggable, kein INTERNET (geprüft 16:22).

Dieser Bericht erscheint zu jeder Veröffentlichung von LiCal. Er zeigt, was die App mit dem Netz macht, welche Rechte sie hat, woraus sie besteht und wie sie den **Wertekompass von LiSoftware** einhält. Jede Aussage hat einen Nachweis. Geprüft hat ein Tester des Projekts, nicht der Entwickler.

## 1. Kurz gesagt

- **LiCal hat keinen Internetzugang.** Die Android-App hat keine INTERNET-Berechtigung, Android lässt sie also keine Verbindung aufbauen. Die Ubuntu-App enthält keinen Netzwerk-Code.
- Keine Werbung, keine Käufe, keine Abos, kein Konto.
- Keine Tracker, keine Analyse, keine Absturzberichte.
- Keine KI, kein Modell.
- Termine bleiben auf dem Gerät. Weitergegeben wird nur ein einzelner Termin, den du selbst als QR-Code zeigst.

## 2. Netzverbindungen

**Keine.** Nachweise:
- Android: Im Manifest der Testfassung (aapt2) gibt es keine INTERNET-Berechtigung. Die einzige Webadresse im Code, die die App selbst nutzt, ist lisoftware.de/lical/ unter „Über LiCal“. Ein Tipp darauf öffnet den Browser, LiCal selbst verbindet sich nicht. Die übrigen Adressen in der APK sind Text aus Bibliotheken (Google-Fehlerberichte zu Compose, w3.org, Adobe-XMP-Namensraum, JetBrains), die nie aufgerufen werden.
- Ubuntu: Der Quelltext (linux/lical) importiert kein Netzmodul (urllib, http, socket, requests), nutzt kein WebKit und keinen DNS-Resolver. Externe Programme: nur gtk-update-icon-cache (Symbol mit dem heutigen Datum). Webadressen nur im Info-Fenster (lisoftware.de/lical/, GitHub-Issues), nur auf Klick über den Browser des Systems.
- Messung Ubuntu (strace -f, connect und sendto) an allen neun Testsuiten ohne Fenster (Hinweise, Bearbeitung, Feiertage, iCalendar, Schnelleingabe, Erinnerungsdienst, Regeln, Team-Kern, Zeitzonen), 08.10.2026 16:03, Commit 25d3465: 0 IPv4, 0 IPv6, 0 DNS, auch keine Verbindung zum Sitzungsbus. Messung der laufenden App mit Fenster, 08.10.2026 16:39, Commit c43ae29: test_mac_gui (Ansichten, Bedienung), test_editor_gui (Termine anlegen und bearbeiten), Einstellungen, Info › Rechtshinweis und Druckblatt unter strace -f: 0 IPv4, 0 IPv6, 0 DNS. Verbindungen nur zu lokalen Sockets (Sitzungsbus, Bedienungshilfen, Wayland).
- Feiertage rechnet LiCal selbst aus, es fragt dafür keinen Dienst.

**Kalender anderer Apps (nur Android):** LiCal liest und schreibt auch die Kalender, die schon auf dem Gerät sind, etwa aus DAVx⁵ oder Google. Diese Kalender gleicht die jeweilige App mit ihrem Server ab, nicht LiCal. Was du dort einträgst, geht also über deren Abgleich ins Netz.

**Team-Abgleich:** Es gibt einen lokalen Kern (team.py, Team.kt) ohne Netz. Verbindungen gibt es noch keine. Der Entwurf (docs/TEAMSYNC.md) braucht Olafs Freigabe, bevor etwas das Gerät verlässt.

## 3. Berechtigungen (Android)

| Berechtigung | Wozu | Wann gefragt |
|---|---|---|
| READ_CALENDAR, WRITE_CALENDAR | Kalender des Geräts anzeigen und bearbeiten | erst beim Einschalten von „Kalender des Handys zeigen“; beim Start fragt LiCal nach nichts (Michelle am Gerät, 08.10. 16:2x, „Nicht zulassen“ gewählt) |
| CAMERA | QR-Code eines Termins scannen | erst beim Scannen |
| POST_NOTIFICATIONS | Hinweise vor Terminen | ⏳ Zeitpunkt am Gerät bestätigen |
| USE_EXACT_ALARM (SCHEDULE_EXACT_ALARM bis Android 12L) | Hinweise pünktlich zur eingestellten Minute | – (Systemrecht) |
| RECEIVE_BOOT_COMPLETED | Hinweise und Widgets nach einem Neustart wieder planen | – |

**Nicht** angefordert: Internet, Standort, Mikrofon, Kontakte, Speicher/Fotos, Telefon, Konten.
Android-Backup: aus (allowBackup=false, dataExtractionRules schließt cloud-backup und device-transfer aus). Von außen erreichbar: nur die Startseite und die Einstiege „Termin anlegen/ansehen“ und „.ics öffnen“ aus anderen Apps (Intent-Filter INSERT/EDIT/VIEW für Kalendereinträge und text/calendar). Die Widgets sind nicht exportiert.

## 4. Speicher

LiCal speichert deine Termine als Datei kalender.json. Unter Ubuntu liegt sie in ~/.local/share/lical in einem Ordner, den nur du lesen kannst (Rechte 700), unter Android im privaten Speicher der App, auf den andere Apps keinen Zugriff haben. **Die Datei selbst ist nicht zusätzlich verschlüsselt.** Sie ist so geschützt wie dein Benutzerkonto bzw. die Geräteverschlüsselung von Android. (LiMail verschlüsselt zusätzlich, LiCal bisher nicht. Das Nachholen ist als Karte 401b90b4 geplant.)

## 5. Bibliotheken

**Android:** Android Jetpack/Compose (BOM 2024.09.03, Apache-2.0), CameraX 1.4.0 (Apache-2.0, Kamera für QR-Codes), ZXing core 3.5.3 (Apache-2.0, QR-Codes zeichnen und lesen), Kotlin 2.0.21 und Guava ListenableFuture 1.0 (Apache-2.0), Schrift Inter 4.001 (OFL-1.1). Alle stehen mit Lizenztext in der App unter „Über LiCal › Lizenzen“.
**Ubuntu:** mitgeliefert: Inter 4.001 (OFL-1.1), QR Code generator library von Project Nayuki (MIT). Aus Ubuntu: GTK 4, libadwaita, GLib, Pango, GdkPixbuf, Graphene, PyGObject, python3-cryptography, Python 3. Angezeigt unter „Über LiCal › Rechtshinweis“ (geprüft 08.10., Karten 29746c85, 486dbb6a).

Keine Werbe-, Analyse-, Absturzmelde- oder Bezahl-Bibliothek.

## 6. Tracker

**Keine.** Gegenprobe am 08.10.2026 16:03 an der Testfassung 11:43:16 (d5bc5d45…): 51 Präfixe bekannter Tracker-, Analyse-, Absturzmelde- und Werbe-SDKs (u. a. Firebase, Play Services, Crashlytics, Facebook, Sentry, AppsFlyer, Adjust, Mixpanel, ACRA), zwei Suchwege (dexdump über alle definierten Klassen und Rohsuche nach Typ-Deskriptoren): **0 Treffer**. Positivkontrolle: Compose, CameraX, ZXing (com.google.zxing) werden gefunden. Nachweis tracker-suche.txt. Wiederholt an der Release-APK 0.1.1 (R8), 08.10. 16:22: ebenfalls 0 Treffer (nachweise/lical-release-011/tracker-suche.txt).

## 7. Abgleich mit dem Wertekompass

| Wert / Grundsatz | Erfüllt? | Nachweis |
|---|---|---|
| **1. Privatsphäre:** keine Datensammlung, keine Telemetrie, keine Tracker, kein Cloud-Backup | ✔ mit einer Einschränkung | Abschnitte 2, 3, 6. Einschränkung: Termine liegen nicht zusätzlich verschlüsselt auf dem Gerät (Abschnitt 4). Nach „Privatsphäre zuerst“ wird das wie bei LiMail nachgeholt (Karte 401b90b4). |
| **2. Kontrolle:** alles abschaltbar, nichts ohne Zustimmung, Netz nur wenn nötig | ✔ | kein Netz; Kamera erst beim Scannen; Hinweise und Feiertage abschaltbar; QR-Teilen nur auf Tipp, Notizen nur auf Wunsch |
| **3. Einfachheit:** Apples Bedienkonzept, Bedienungshilfen | ✔ / ⏳ | HIG-Prüfungen der Einstellungen, Hilfe, Druckblatt (08.10.); TalkBack/Orca-Durchgang ⏳ |
| **4. Kostenlos für immer** | ✔ | keine Billing-Bibliothek, keine Käufe |
| **KI** aus, kein Modell | ✔ | Schnelleingabe „Mittag mit Jens morgen 12:30“ arbeitet mit festen Regeln (quick.py, test_quick 20 Beispiele). KI-Suche an der Release-APK 0.1.1 (08.10. 16:24): 0 Modelldateien, 0 von 16 KI-Bibliotheken, 0 von 11 KI-Dienst-Adressen, Positivkontrolle Compose 123 (ki-suche.txt) |
| **Dienste Dritter** nur als Angebot | ✔ | Kalender anderer Apps nur, wenn du die Berechtigung gibst |
| **Transparenz:** offener Quelltext, dieser Bericht | ⏳ | GPL-3.0, Repo-Veröffentlichung über den bereinigten Spiegel |

## 8. Bekannte Grenzen
- Termine aus Kalendern anderer Apps (DAVx⁵, Google) gehen über deren Abgleich ins Netz, nicht über LiCal.
- kalender.json ist nicht zusätzlich verschlüsselt (Abschnitt 4).
- Die Anleitung „Team-Abgleich“ ist ein Entwurf, nichts davon ist eingeschaltet.

---
Erstellt von Richard 🟡 (Tester). Rohdaten und Prüfprotokolle: ~/Team/Richard/nachweise/transparenz-lical/ (ablauf.txt, tracker-suche.txt), APK-Prüfung nachweise/lical-apk/pruefung-1143.txt. Ablage im Repo: docs/TRANSPARENZ-0.1.1.md.
