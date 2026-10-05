# Apple-Kalender als Referenz für Teamkalender

**Verbindliche Klarstellung vom 4. Oktober 2026:** Für Android und Ubuntu fordert
der Nutzer 100 % Orientierung an Apple HIG und Übernahme der Designphilosophie
Apples, einschließlich der bereitgestellten Originalbeispiele. Ältere Aussagen
unten, die das Ziel auf lose Gestaltungsprinzipien begrenzen oder Material-/GTK-
Standardgestaltung vor die Referenztreue stellen, sind insoweit überholt.
Die Überarbeitung ist unfertig. Siehe [WORK_STATUS.md](WORK_STATUS.md).

Stand: 1. Oktober 2026. Recherche und Gestaltungsgrundlage; die unten beschriebenen Erweiterungen sind noch nicht implementiert.

## Quellen und Prüfumfang

38 Seiten des [Apple-Kalenderhandbuchs](https://support.apple.com/de-de/guide/calendar/welcome/mac) abgerufen und die Funktionskapitel ausgewertet: Accounts, Ereignisse, Ganztägigkeit, Serien, Orte, Hinweise, Einladungen, Anhänge, Kalenderverwaltung, Freigaben, Abonnements, Einstellungen, Erinnerungen, Zeitzonen, Drucken und Tastaturbedienung. Referenztexte und Quellenadressen liegen in `reference/index.json`. Originalabbildungen wurden direkt angesehen: Wochenraster, Tagesansicht mit Erinnerungsinspektor, Einladung, Accountauswahl und kompakter/aufgeklappter Ereignisdialog.

Die [HIG](https://developer.apple.com/design/human-interface-guidelines) wurden über Apples öffentliche Dokumentationsdaten gelesen, da die HTML-Seiten JavaScript verlangen: Layout, Sidebars, Toolbars, Popovers, Color, Typography, Accessibility und Designing for macOS. Zusätzlich geprüft: [Dos and Don'ts](https://developer.apple.com/design/tips/) und [Design Resources](https://developer.apple.com/design/resources/). Design Resources enthält plattformspezifische UI-Kits und Vorlagen; ein vollständiges Figma-/Sketch-Kit wurde nicht geöffnet.

[Der SavvyCal-Artikel](https://savvycal.com/articles/apple-calendar/) stammt vom 21. Dezember 2021. Er ergänzt die Bedienungsbeispiele, ist für aktuelle Funktionen aber nachrangig. Die aktuelle Apple-Dokumentation beschreibt beispielsweise direkt im Kalender erstellbare und erledigbare Erinnerungen. [Skywork](https://skywork.ai/skypage/en/macos-calendar-apple-mcp-server/1980880127853658112) lieferte im verfügbaren Abruf Titel und Gliederung, nicht den vollständigen Artikel. Die Gliederung betrifft eine MCP-Anbindung an macOS; daraus wird keine bestätigte technische Implementierung für Ubuntu abgeleitet.

### Plattformressourcen: Apple als Referenz, Android als Implementierung

Apples [Design Resources](https://developer.apple.com/design/resources/) enthält
offizielle UI-Kits, App-Icon-Vorlagen, Schriften und SF Symbols. Sie wurden für
dieses Android-Projekt **nicht** direkt importiert. Apples
[Design-Resources-Lizenz](https://developer.apple.com/support/downloads/terms/apple-design-resources/Apple-Design-Resources-License-20230621-English.pdf)
beschränkt UI-Kit und Vorlagen auf Mockups für Software, die ausschließlich auf
Apple-Betriebssystemen läuft, und untersagt deren Verwendung für Mockups
nicht-Apple-Plattformen. SF Pro hat außerdem eine eigene Lizenz. Die Assets in
Android zu übernehmen oder ein Android-Mockup damit zu erstellen wäre daher
keine zulässige Abkürzung.

Für die Android-App verwenden wir Apples öffentlich zugängliche
[Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
und die vom Nutzer gelieferten Kalenderbilder als **Gestaltungsreferenz**.
Implementiert wird mit Googles offiziellen
[Jetpack Compose](https://developer.android.com/compose)- und
[Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)-Angeboten:
Systemtypografie, Material-Komponenten und -Symbole, Android-Status-/Navigationsleisten,
Barrierefreiheit und passende Systemverhalten. Die Kalenderfläche (Tag/Woche/Monat/Jahr)
bleibt eine eigene Compose-Ansicht, weil Material 3 keine vollständige Kalender-App
mit diesen Ansichten bereitstellt. Bei Bedarf nutzen wir die offiziellen
[adaptiven Compose-Layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive).

Die Apple-Bilder geben das gewünschte Kalenderverhalten und die visuelle Hierarchie
vor: zurückhaltende Werkzeugleiste, deutliches Datum, gut lesbares Wochenband,
feines Zeitraster, konsistente Kalenderfarben, erkennbare Heute-/Jetzt-Markierung,
semantische Hell-/Dunkelfarben und dynamische Textgrößen. Wir setzen diese Ziele
mit Android-Bausteinen um, statt Apple-Steuerelemente nachzuzeichnen. Apples
SF-Symbole und -Schriftdateien werden nicht in die Android-App eingebettet.
Apples offizielle [SF Symbols](https://developer.apple.com/sf-symbols/)-Bibliothek
bleibt eine wertvolle Referenz für Symbolgewicht, Skalierung, optische Ausrichtung,
mehrschichtige Farben und Animation. Apple beschreibt sie als auf San Francisco
und Apple-Plattformen abgestimmt. Für die Android-App beziehen wir Symbole deshalb
als Android-Vector-Drawable aus [Google Fonts Icons](https://fonts.google.com/icons),
wie die offizielle Compose-Doku
[empfiehlt](https://developer.android.com/develop/ui/compose/graphics/images/material).
So verwenden wir ein offizielles Angebot der Zielplattform, statt SF-Symbole
nachzuzeichnen oder das alte Material-Icon-Paket weiter auszubauen.

Der vom Nutzer genannte [GitHub-Katalog](https://github.com/mjmirza/apple-design-system/tree/master/catalog)
und die [uithings-Sammlung](https://uithings.com/apple-design-resources) sind hilfreiche
Wegweiser, aber keine Ersatzquelle für Plattformdokumentation oder Lizenzbedingungen.
Die konkrete Apple-Referenz wird jeweils anhand der offiziellen Apple-Seite geprüft;
für Android-Implementierungsdetails gilt die offizielle Android-Dokumentation.

Die [Apple-Farbhinweise](https://developer.apple.com/design/human-interface-guidelines/color)
werden als prüfbare Prinzipien angewendet: Farbe bleibt in Hell- und
Dunkelmodus unterscheidbar und ist nie der einzige Träger von Statusinformation.
Dieses Protokoll ist die Referenz für die Android-Kalenderoberfläche; die
Apple-UI-Kit-Dateien sind keine Android-Entwicklungsressource.

## Was die Bilder tatsächlich zeigen

`reference/apple-1.png`: Wochenansicht mit schmaler Seitenleiste, nach Account gruppierten Kalendern, farbigen Auswahlkästchen und kleinem Monatsnavigator. Oben stehen wenige Aktionen, mittig Tag/Woche/Monat/Jahr, rechts Suche. Darunter großer Datumsbezug und Vor/Heute/Zurück-Navigation. Das Hauptfeld enthält feine Rasterlinien, Stunden links, sieben Tagesspalten und einen separaten Ganztagsbereich. Termine haben einen blassen Hintergrund in Kalenderfarbe, einen kräftigen Farbstreifen links und dunklen Text. Überlappende Termine stehen nebeneinander. Rot markiert das heutige Datum und die aktuelle Zeit. **Die Alternativbeschreibung dieses Bildes behauptet Monatsansicht; sichtbar ausgewählt ist jedoch „Week“.** Für die Monatssemantik dient zusätzlich die [Symbolerklärung](https://support.apple.com/de-de/guide/calendar/symbls/mac).

`reference/apple-3.png`: Tagesansicht mit Zeitachse links und einem Detailbereich rechts. Der Ganztagsbereich liegt oberhalb der Zeitachse. Ein roter Zeitstrich ist mit einer Uhrzeit versehen. Erinnerungen besitzen einen Kreis zum Erledigen; ihre Darstellung unterscheidet sie von Terminen.

`reference/apple-2.png`: Ein kleines, am Termin orientiertes Informationsfenster gruppiert Titel/Ort, Datum/Wiederholung/Hinweis, Teilnehmer und Zusatzinformationen. Teilnehmerstatus wird durch Symbole zusätzlich zum Namen vermittelt.

`reference/apple-4.png`: Vergleich eines kompakten und eines erweiterten Terminfensters. Die Datumszusammenfassung wird bei Bedarf zu Feldern für Beginn, Ende, Wiederholung, Serienende, Wegzeit und Hinweise. Das Fenster bleibt im Kalenderkontext; selten benötigte Felder dominieren die erste Ansicht nicht.

`reference/apple-0.png`: Accountauswahl als eigener Dialog. Das ist ein separater Vorgang, nicht Bestandteil jedes Terminformulars.

## Gestaltungsentscheidungen für Ubuntu

Die folgenden Maße und Prioritäten sind unsere Umsetzungsvorschläge, keine Apple-Vorgaben.

| Bereich | Ziel für Teamkalender | Abweichung der Version 0.1.0 |
|---|---|---|
| Fensteraufbau | Eine kompakte Werkzeugleiste; Datumsüberschrift; großer Kalenderbereich | Drei optisch getrennte Kopf-/Bedienzeilen beanspruchen Platz |
| Seitenleiste | Etwa 210–240 px, ausblendbar; Kalendergruppen und Monatsnavigator | Immer sichtbar, Sicherungsaktionen und Entwicklungshinweis im Hauptbereich |
| Ansichtswahl | Zusammengehöriger Umschalter Tag/Woche/Monat/Jahr; Liste zusätzlich erreichbar | Vier einzelne Buttons; Jahr fehlt |
| Tag/Woche | Zeitraster, Ganztagszeile, aktuelle Zeit, Überschneidungen nebeneinander | Chronologische Agenda bzw. Listen in Spalten |
| Monat | Durchgängiges Raster, kleine Tageszahlen, kompakte Terminzeilen, Mehrtagesbalken | Jede Zelle wirkt wie eine eigene Karte; Termine verbrauchen zwei Zeilen |
| Termine | Kalenderfarbe als dezente Fläche und Akzent; Titel hat Vorrang | Überwiegend Punkt und Text ohne räumliche Dauer |
| Editor | Kompakte Details mit aufklappbaren Bereichen, Datum-/Zeitwahl | Langes modales Formular mit ISO-Datumsfeldern |
| Navigation | Heute, Datumsauswahl und Hauptkalender bleiben synchron | Kleiner Monatsnavigator wird beim Wechsel des Hauptdatums nicht nachgeführt |
| Status | Erkennbarer Auswahl-, Einladungs-, Schreibschutz- und Verbindungsstatus | Nur lokale Kalender, wenig differenzierte Zustände |

Die HIG betonen nachvollziehbare Hierarchie, Ausrichtung, schrittweises Einblenden von Details und ein an die Fenstergröße angepasstes Layout. Werkzeugleisten sollen häufige Aktionen enthalten; weitere Befehle gehören ins Menü. Seitenleisten sollen sich ausblenden lassen. Quelle: [Layout](https://developer.apple.com/design/human-interface-guidelines/layout), [Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars), [Sidebars](https://developer.apple.com/design/human-interface-guidelines/sidebars).

Für Ubuntu verwenden wir GTK-Komponenten, die Systemschrift, eigene bzw. systemeigene Symbole und passende Fensterbedienung. Die Informationshierarchie wird übernommen, nicht Apples Fensterknöpfe oder Schriftdateien. Farbe darf nicht der einzige Statusträger sein. Helle/dunkle Darstellung, Kontrast, sichtbarer Tastaturfokus und vergrößerte Schrift müssen funktionieren. Die 44-Punkt-Touchregel der Tips-Seite wird nicht als starres Desktopmaß verwendet. Quelle: [Color](https://developer.apple.com/design/human-interface-guidelines/color), [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility), [Designing for macOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-macos).

Ein nichtmodales Detailfenster darf bei Schließen durch einen Klick außerhalb keine eingegebenen Änderungen verlieren. Dafür müssen Entwürfe, Speichern und Abbrechen bewusst gestaltet sein. Quelle: [Popovers](https://developer.apple.com/design/human-interface-guidelines/popovers).

## Funktionen und technische Konsequenzen

| Funktion | Beobachtung aus dem Handbuch | Konsequenz für Teamkalender |
|---|---|---|
| Direkte Bearbeitung | Im Raster aufziehen, verschieben, Dauer an Rändern ändern | Gemeinsame Zeit-/Geometrieberechnung; Tastaturalternative; Undo |
| Kalenderverwaltung | Kalender anlegen, umbenennen, färben, gruppieren, ausblenden | Eigene Kalenderobjekte mit stabiler ID; nicht bloß Namen als Ereignisfeld |
| Wiederholungen | Intervalle, ausgewählte Wochentage, Monatsmuster, Serienende; einzelne oder zukünftige Vorkommen löschen | Strukturierte Regel, Ausnahmen und Serienaufteilung statt deutschem String |
| Zeitzonen | Anzeigezone und Terminzone; auch fließende Ortszeit | Zeittyp explizit speichern; Sommerzeit und Zeitzonenwechsel testen |
| Ganztag/mehrtägig | Eigener Bereich; Dauer reicht über Tage; auch Nachtschichten | Datumsintervalle getrennt von Zeitpunkten; über Mitternacht korrekt aufteilen |
| Hinweise | Mehrere Hinweise je nach Anbieter; Standardwerte; Schlummern | Zustellender Hintergrunddienst und persistenter Alarmzustand |
| Aufgaben | Geplante Erinnerungen mit Erledigt-Zustand | Aufgabenmodell von Termin und Terminbenachrichtigung trennen |
| Einladungen | Organisator, Teilnehmer, Ja/Nein/Vielleicht, Terminvorschläge | Teilnehmerobjekte, Antwortstatus, Änderungs-/Versandzustand |
| Verfügbarkeit | Nur wenn der Kalenderdienst diese Information liefert | „Unbekannt“ nie als „frei“ darstellen; Dienstfähigkeit berücksichtigen |
| Freigabe | Einzelner Kalender, Accountdelegierung und öffentliche Abos sind verschiedene Vorgänge | Rechte und Freigabeart explizit speichern; Terminteilnahme gewährt keinen Kalenderzugriff |
| Abonnements | Schreibgeschützt, periodisch aktualisiert | Schreibschutz im Modell und in jeder Änderungsaktion durchsetzen |
| Import/Export | Einzelkalender als .ics; Gesamtsicherung als Archiv | Interoperablen .ics-Austausch zusätzlich zur eigenen JSON-Sicherung entwickeln |
| Anhänge/URL/Ort | Eigenständige Daten und Aktionen, Dateien per Drag-and-drop | Verwaltete Anhänge mit portablen Referenzen statt nur lokalen Dateipfaden |
| Einstellungen | Wochenbeginn, Arbeitstage/-zeiten, Wochenzahlen, Standardkalender | Persistente benutzerbezogene Einstellungen; nicht im Code fest verdrahten |
| Drucken | Tag/Woche/Monat sowie Terminlisten mit Zeitraum und Kalenderauswahl | Eigenes Drucklayout/PDF statt Screenshot des Fensters |
| Integration | Geburtstag aus Kontakten, regionale Feiertagsabos, alternative Kalender | Optionale Datenquellen; Herkunft und Schreibschutz sichtbar halten |

Quellen: [Ereignisse](https://support.apple.com/de-de/guide/calendar/icalwr13-events/mac), [Serien](https://support.apple.com/de-de/guide/calendar/icl1018/mac), [Ganztägige Termine](https://support.apple.com/de-de/guide/calendar/icl1039/mac), [Zeitzonen](https://support.apple.com/de-de/guide/calendar/icl1035/mac), [Hinweise](https://support.apple.com/de-de/guide/calendar/icl1012/mac), [Erinnerungen](https://support.apple.com/de-de/guide/calendar/icl873b9a527/mac), [Einladungen](https://support.apple.com/de-de/guide/calendar/icl1016/mac), [Freigaben](https://support.apple.com/de-de/guide/calendar/icl1026/mac), [Abonnements](https://support.apple.com/de-de/guide/calendar/icl1022/mac), [Dateiaustausch](https://support.apple.com/de-de/guide/calendar/icl1023/mac), [Drucken](https://support.apple.com/de-de/guide/calendar/icalwr32-printing/mac).

Apples Siri, Handoff, FaceTime-Erstellung und die systemweite Erinnerungen-App sind plattformspezifisch. Für Ubuntu sind eigene Schnittstellen oder Alternativen erforderlich, etwa allgemeine Videokonferenz-URLs. Karten und Wegzeitberechnung benötigen eine bewusst ausgewählte Datenquelle. MCP wäre eine spätere Automatisierungsschnittstelle; es ersetzt weder Kalender-Synchronisation noch Kalenderprotokolle.

## Umsetzung in sinnvoller Reihenfolge

1. **Oberfläche und Interaktion:** Kopfbereich vereinfachen, Seitenleiste ausblendbar, Tag/Woche als echtes Raster, kompakte Monatszellen und Termin-Details, Jahresübersicht. Kalendernavigation synchronisieren. Deutsche Datumsanzeige und echte Auswahlfelder.
2. **Lokale Fachfunktionen:** Kalenderverwaltung, Einstellungen, persistente Filter, Aufgabenstatus, Seriendetails und Ausnahmen, Zeitzonen, Rückgängig, Anhänge, zuverlässige Benachrichtigungen und .ics-Austausch. Datenmigration vor Änderungen am Speicherschema.
3. **Zusammenarbeit:** Android-/Ubuntu-Synchronisation, Konfliktauflösung, Konten und Dienstfähigkeiten, Freigaben, Teilnehmerantworten und Verfügbarkeit. Die bisherige Offline-/Verschlüsselungsplanung bleibt eine eigene Anforderung; sie folgt nicht automatisch aus Apple Calendar.
4. **Erweiterungen:** Feiertage, Kontakte/Geburtstage, Druck, Wegzeiten, natürliche Sprache und bei Bedarf Automatisierung.

Abnahmebeispiele: Zwei überlappende Termine bleiben beide les- und bedienbar; Urlaub erscheint in jeder Ansicht identisch; ein geänderter Serientermin verändert seine Geschwister nicht; Zeitzonenwechsel verschiebt keinen ganztägigen Urlaub; ausgeblendete Kalender bleiben nach Neustart ausgeblendet; Abos lassen sich nicht versehentlich editieren; bei 200 % Textskalierung bleiben Hauptaktionen erreichbar. Alle Beispiele benötigen neben Normalfällen Tests für Monatswechsel, Schaltjahr, Sommerzeit, Nachttermine und Wiederherstellung.


## Ergänzung: sieben vom Benutzer bereitgestellte Bilder

Die Originale liegen unter `reference/user-reference-1.png` bis
`reference/user-reference-7.png`. Diese Bilder konkretisieren die gewünschte
Gestaltung; sie zeigen unterschiedliche Versionen und Fenstergrößen.

| Bild | Sichtbare Merkmale | Vorgabe für Teamkalender |
|---|---|---|
| 1 | Dunkle Jahresansicht, zwölf Monate in vier Spalten und drei Reihen, korallrote Monatsnamen, heller Text, dezente graue Nachbartage | Dunkelmodus mit abgestuften dunklen Flächen; Jahresübersicht ohne Kartenrahmen, adaptive Spaltenzahl |
| 2 | Helle Monatsansicht, durchgängige feine Rasterlinien, Tageszahlen rechts oben, leicht abgesetzte Wochenenden, kurze Ereigniszeilen und Uhrzeit rechts; verankertes Erinnerungsfenster | Flächiges Monatsraster mit hoher Informationsdichte; Ganztagsbalken und zeitgebundene Termine unterscheiden; Details am Eintrag öffnen |
| 3 | Helle Wochenansicht, Kalendergruppen links, Ganztagsbereich oben, Zeitachse und aktuelle Zeit, farbige Terminflächen | Wochenraster als Hauptansicht für zeitliche Planung |
| 4 | Ältere Monatsansicht mit gleicher Grundstruktur und zurückhaltender Gestaltung | Stabile Informationshierarchie übernehmen; ältere Fensterdekoration ist keine Zielvorgabe |
| 5 | Helle Jahresansicht mit zwölf Monaten; sehr dezente milchige Transparenz links, ruhige Hauptfläche; links ist der Mitteilungseingang ausgewählt | Seitenbereich soll optisch leicht wirken; Transparenz bleibt zurückhaltend. Kalenderliste und Mitteilungseingang sind unterschiedliche Inhalte derselben Seitenregion |
| 6 | Tagesansicht mit großem Datum/Wochentag, Ganztagsbereich, Stundenraster links und Erinnerungsdetails rechts samt kleinem Monatsnavigator | In breiten Fenstern kontextbezogener Detailbereich rechts; Felder nach Inhalt gruppieren. Nicht jedes Bild zeigt gleichzeitig eine linke Kalenderliste |
| 7 | Gleiche Wochenreferenz wie Bild 3 | Bestätigt Gewichtung von Raster, Ganztag, Farbe und schmaler Seitenleiste |

### Verbindliche visuelle Richtung

- Kalenderinhalt erhält den größten Flächenanteil. Kleine Werkzeugleiste mit
  gemeinsamem Ansichtsumschalter und gut erreichbarer Datumsnavigation.
- Keine einzeln eingerahmten Tageskarten. Monats- und Wochenansicht bilden eine
  zusammenhängende Fläche mit feinen Trennlinien.
- Terminflächen sind schwach in Kalenderfarbe getönt; ein stärkerer Farbstreifen
  und gut lesbare Schrift tragen die Information. Auswahl, Heute und aktuelle
  Zeit sind voneinander unterscheidbare Zustände.
- Jahresansicht: zwölf kleine Monatsraster, großzügige Abstände zwischen Monaten,
  farbige Monatsüberschriften, kompakte Zahlen und klar erkennbares Heute.
- Hellmodus: helle Hauptfläche, leicht graue Seitenregion, zurückhaltende Schatten.
  Dunkelmodus: dunkles Grau mit abgestuften Flächen statt reinem Schwarz und
  ausreichend hellem Text. Die Jahresansicht in Bild 1 ist die konkrete dunkle
  Referenz; dunkle Tages-/Wochenansichten sind daraus noch zu gestalten.
- Milchglas betrifft vor allem die Seitenregion, nicht das ganze Kalenderblatt.
  Der Hintergrund soll höchstens sanft durchscheinen und niemals die Lesbarkeit
  beeinträchtigen. Bild 5 zeigt den gewünschten visuellen Eindruck, liefert aber
  keine messbaren Werte für Deckkraft oder Unschärferadius.
- Echte Hintergrundunschärfe hängt unter Ubuntu von Fenstersystem und Compositor
  ab. Eine halbtransparente GTK-Fläche erzeugt allein keine Unschärfe hinter dem
  Fenster. Verlässliche Standarddarstellung: milchig getönte, lesbare Fläche;
  echte Transparenz nur mit unterstützter Technik und deckendem Fallback.
- Datumsformat, Wochenbeginn und Uhrzeiten werden deutsch/lokalisiert umgesetzt;
  die englischen Beispiele und sonntags beginnenden Wochen sind keine Vorgabe.

Diese Ergänzung dokumentiert die Referenzen. Sie bedeutet noch keine Änderung
an der installierten App.


## Ergänzung: Teamkalender-Appsymbol 0.3.0

Die neue Vektorquelle ist `../assets/icons/teamkalender.svg`; eine Vorschau liegt
in `reference/teamkalender-icon-0.3.0.png`. Die Darstellung verbindet eine
reduzierte Kalenderform mit drei verbundenen Teamfarben. Ubuntu erhält für die
transparent gerundete Desktopfläche passende Exporte; Android verwendet
adaptive Vorder- und Hintergrundvektoren, deren Außenform das System maskiert.
Damit bleibt das Motiv erkennbar, ohne plattformübergreifend dieselben
vorgerundeten Pixelkanten einzubrennen. Apples Icon-HIG empfiehlt wenige klare
Formen, zentrierten Inhalt und Systemmaskierung.
