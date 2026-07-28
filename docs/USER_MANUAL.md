# Benutzerhandbuch

## Bebilderte Hilfe verwenden

„Hilfe“ in der Kopfleiste öffnet von jeder Hauptseite die integrierte Anleitung. Auf der Übersicht
steht zusätzlich „Bebilderte Hilfe öffnen“. Die Statuskarte zeigt unmittelbar, ob Android-Systemrolle,
lokaler Schutz und der freiwillige PhoneBlock-Zugang bereit sind.

Die sechs Kapitel sind mit Vektorgrafiken bebildert und mit „MUSS“, „SOLLTE“ oder „KANN“ markiert.
Jedes Kapitel erklärt den Zweck, nennt sichere Empfehlungen und enthält eine Schaltfläche zur
betroffenen App-Funktion. „Zurück“ führt wieder zur Übersicht. Die Bildbeschreibungen sind für
TalkBack hinterlegt.

## Einrichtung

1. RufWächter installieren und öffnen.
2. Auf der Übersicht die lokale Verarbeitung lesen.
3. „Schutz im System aktivieren“ wählen und RufWächter im Android-Systemdialog bestätigen.
4. Unter Einstellungen Standardaktion, Privat-/Unbekannt-Verhalten und Schwellen prüfen.
5. Online-Feeds nur bei Bedarf aktivieren; sie sind anfangs aus.

## PhoneBlock einrichten

1. Unter „Einstellungen“ im Abschnitt „PhoneBlock“ API-Schlüssel oder Basic-Anmeldung auswählen.
2. API-Schlüssel beziehungsweise Benutzername und Passwort eingeben und „Zugang speichern“ wählen.
3. „PhoneBlock täglich aktualisieren“ aktivieren.
4. „Jetzt abgleichen“ zeigt Version, Änderungen, Löschungen und übertragene Meldungen an. Wiederholungen innerhalb von 24 Stunden laden die Community-Liste nicht erneut.

Ein API-Schlüssel ist vorzuziehen. Nur damit kann RufWächter laut API-Beschreibung auch die persönliche PhoneBlock-Black- und Whitelist abrufen. Zugangsdaten werden verschlüsselt gespeichert und nie exportiert.

„Eigene Sperren an PhoneBlock melden“ ist unabhängig vom Listenabruf und zunächst aus. Nach Aktivierung wird jede neu manuell angelegte exakte Sperre mit der ausgewählten Kategorie und Notiz in eine lokale Warteschlange gestellt. „Bestehende lokale Sperren melden“ umfasst auch zuvor importierte Sperren und muss gesondert ausgelöst werden.

## Nummer prüfen und Regeln

Unter „Prüfen“ eine Nummer eingeben. RufWächter zeigt Normalform, Aktion, Score, Konfidenz und Gründe. „Zulassen“, „Blockieren“ oder „24 h stumm“ erzeugt eine nachvollziehbare Regel. Eine Websuche öffnet erst nach Datenschutzhinweis den Browser.

Unter „Regeln“ lassen sich Regeln suchen, anlegen, bearbeiten, deaktivieren und nach Bestätigung löschen. Bei Präfixen gewinnt das längste passende Präfix; persönliche exakte Freigaben haben höchste Priorität.

## Verlauf und Korrekturen

Der Verlauf zeigt nur App-eigene Entscheidungen. „Legitim“ erstellt eine persönliche Freigabe, „Spam“ eine persönliche Blockierung. Einzelne Einträge oder der gesamte Verlauf können gelöscht werden.

## Feed, Export und Löschung

Unter „Daten“ importiert der Android-Dateidialog einen JSON-Feed. Das Ergebnis nennt akzeptierte und verworfene Einträge sowie einen gekürzten SHA-256. Der Export schreibt Regeln und Einstellungen an ein selbst gewähltes Ziel. „Alle lokalen Daten löschen“ entfernt nach Bestätigung Regeln, Verlauf, Reputation, Feed-Metadaten und Einstellungen.

Android gibt seine systemweite Sperrliste nicht an eine reine Anruferkennungs-App heraus. Vor RufWächter angelegte Systemsperren bleiben durch Android aktiv, erscheinen aber nicht automatisch in RufWächter. „Systemliste öffnen“ öffnet die vom Gerät bereitgestellte Verwaltungsansicht. Falls die Telefon-App eine Liste exportieren kann, nimmt „Export importieren“ Text-, CSV- oder JSON-Dateien mit einer Rufnummer pro Zeile beziehungsweise einem `phone`-/`number`-Feld an. Höchstens 1 MiB und 10.000 Nummern werden verarbeitet.

„Alle lokalen Daten löschen“ entfernt zusätzlich PhoneBlock-Listen, ausstehende Meldungen und die verschlüsselten Zugangsdaten. Bereits übertragene Bewertungen oder der PhoneBlock-Account werden ausschließlich in PhoneBlock verwaltet.

## Grenzen

Eine Warnaktion wird als zugelassener Anruf lokal protokolliert; die Android-Telefonoberfläche bietet über `CallResponse` keine frei gestaltbare Warnbeschriftung. Hersteller können Rollen- und Akkuverhalten verändern. Notrufe und Kontakte werden durch Androids Screening-Auswahl bestimmt.
