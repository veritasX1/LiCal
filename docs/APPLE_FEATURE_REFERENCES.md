# Verbindliche Funktionsreferenzen und Datenschutz

Nutzerergänzungen vom 4. Oktober 2026. Die Seiten wurden in dieser Sitzung geöffnet.
Sie konkretisieren den Zielumfang; diese Liste behauptet keine fertige Umsetzung.

| Bereich | Originalreferenz |
|---|---|
| Erstellen und Bearbeiten | https://support.apple.com/de-de/guide/iphone/iph3d110f84/ios |
| Ansichten | https://support.apple.com/de-de/guide/iphone/iphfd1054569/ios |
| Einladungen senden | https://support.apple.com/de-de/guide/iphone/iph82c5721ca/ios |
| Einladungen beantworten | https://support.apple.com/de-de/guide/iphone/iphc0eddfe3c/ios |
| Suche | https://support.apple.com/de-de/guide/iphone/iph2c9ef44ad/ios |
| Einstellungen | https://support.apple.com/de-de/guide/iphone/iphc37be2016/ios |
| Zeitzonen | https://support.apple.com/de-de/guide/iphone/iph69525c028/ios |
| Ereignisse im Blick behalten | https://support.apple.com/de-de/guide/iphone/iphdafdf98a1/ios |
| Mehrere Kalender | https://support.apple.com/de-de/guide/iphone/iph3d1110d4/ios |
| Erinnerungen | https://support.apple.com/de-de/guide/iphone/iph14f1d32a5/ios |
| Feiertage | https://support.apple.com/de-de/guide/iphone/iph80d93ac49/ios |
| Kalender teilen | https://support.apple.com/de-de/guide/iphone/iph7613c4fb/ios |
| Ubuntu: Kalenderhandbuch für Mac | https://support.apple.com/en-ca/guide/calendar/welcome/mac |

## Verbindliche Abweichung von Apples Infrastruktur

Die App ist unabhängig von iCloud. Gemeinsame Daten werden über den eigenen
Server des Nutzers synchronisiert. Apples Konten oder Cloud-Dienste sind keine
Voraussetzung. Bestehender Direktabgleich bleibt eine separat erkennbare Option.

Privatsphäre ist extrem wichtig. Keine Übertragung am Nutzer vorbei, keine
Telemetrie und keine ungefragten Drittanbieter-Verbindungen. Vor der Freigabe
müssen Zweck, Ziel und betroffene Daten verständlich sein. Private lokale Termine
werden nicht automatisch zu Teamdaten. Fotos und Anhänge benötigen passende Freigaben.

**Präzisierung des Nutzers:** Hintergrundabgleich ist ausdrücklich gewollt und
muss jederzeit abschaltbar sein. Das bedeutet kein generelles Verbot von
Hintergrundarbeit: Sie braucht eine erkennbare, widerrufbare Freigabe. Das
Abschalten muss laufende/geplante Arbeit stoppen bzw. weitere Requests sperren;
ein Schalter, der nur die Anzeige ändert, genügt nicht. Bereits gesendete Bytes
können durch Widerruf nicht rückwirkend zurückgeholt werden.

Eine Serveradresse ist noch keine Freigabe für regelmäßige Hintergrundübertragung.
Eine lokale Gerätesuche ist ebenfalls Kommunikation und darf nicht still beim
App-Start anlaufen. Auswahl eines externen Android-Kalenders kann dessen eigenen
Sync-Dienst betreffen und muss als externer Datenweg erkennbar sein.

## Ergänzte Gestaltungsreferenzen vom 4. Oktober 2026

- https://developer.apple.com/design/human-interface-guidelines/design-principles
- https://developer.apple.com/design/human-interface-guidelines/designing-for-ios
- https://developer.apple.com/design/resources/#ios-apps

Alle drei Seiten abgerufen; die beiden HIG-Texte über Apples öffentliche
Dokumentations-JSON vollständig gelesen. Die Ressourcenseite verlinkt offizielle
UI- und Icon-Vorlagen. Die Vorlagendateien selbst wurden noch nicht ausgewertet.

Konkrete Prüfkriterien für dieses Projekt: Kalenderinhalte priorisieren, wenige
sichtbare Hauptaktionen, konsistente und bekannte Interaktionen, transparente
Datenwege, verständliche Rückmeldungen und umkehrbare Aktionen. Große Schrift,
Hell/Dunkel und unterschiedliche Fenstergrößen müssen bedienbar bleiben.
Die Nutzerpräzisierung zu eindeutigen Icons ist in AGENTS.md verbindlich festgehalten.

### Desktop-Ergänzung

- https://developer.apple.com/design/human-interface-guidelines/designing-for-macos
- https://developer.apple.com/design/resources/#macos-apps

macOS-Richtlinie vollständig über Apples Dokumentations-JSON gelesen,
Ressourcenübersicht geöffnet. Für Ubuntu prüfen: größere Fenster sinnvoll nutzen,
weniger verschachtelte Dialoge, anpassbare Fenster, zugängliche Befehle,
präzise Mausbedienung und Tastaturkürzel. iOS- und macOS-Verhalten bewusst
unterscheiden; Desktop nicht als vergrößerte Telefonoberfläche behandeln.
Offizielle Desktop-Vorlagendateien sind noch nicht ausgewertet.

### Mac-Kalender: Erste Schritte

https://support.apple.com/de-de/guide/calendar/iclc0d84c7fa/mac

Vom Nutzer ergänzte Seite gelesen. Als konkrete Bedienreferenz: schnelle
Terminerstellung über Plus, nachträgliche Ergänzung von Notizen/URLs/Anhängen
und Hinweise im Ereignis. Account-Verwaltung bleibt hier auf eigene Server und
explizit eingerichtete Verbindungen ausgerichtet. Funktionsparität noch prüfen.
