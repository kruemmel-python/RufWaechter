# Testplan

## Automatisiert

- Normalisierung: nationale/internationale Form, `tel:`, Formatzeichen, leer, privat, fremdes Schema, Überlänge, Buchstaben, Idempotenz
- Prioritäten: Freigabe, Blockierung, längstes Präfix, Ablauf, Deaktivierung, deterministischer Konflikt, Privatregel
- Scoring: Wertebereich, schwache Meldung, Carrier allein, bestätigter Betrug, veraltete Daten, Carrier bestanden, Schutz aus
- PhoneBlock-Priorität: lokale Freigabe/Sperre vor persönlichen Listen; persönliche Whitelist vor Community
- PhoneBlock-Parser: Version, Delta-Löschung, Duplikat, persönliche Liste, ungültige Stimmen
- Zugangsdaten: Keystore-Rundlauf und vollständiges Löschen
- Sperrlistenimport: Text, CSV, JSON-Feld, Deduplizierung und Größenlimit
- Room: PhoneBlock-Einträge, Sync-Zustand und Melde-Outbox
- Oberfläche: Hilfe-Navigation, sichtbarer Hilfestart, Bildbeschreibung und Scrollen bei großer Schrift
- Screening-Grenze: Exception und Soft-Timeout führen zu fail-open; Evaluator wird genau einmal aufgerufen
- Leistung: 10.000 exakte Regeln und paralleler Snapshottausch

Die ausgeführten Ergebnisse werden nach dem finalen Lauf in `docs/RELEASE_REPORT.md` festgehalten.

## Gerätetestmatrix

| Android | Status |
|---|---|
| 10 / API 29 | nur kompiliert, realer Test offen |
| 11 / API 30 | nur kompiliert, realer Test offen |
| 12 / API 31 | nur kompiliert, realer Test offen |
| 13 / API 33 | nur kompiliert, realer Test offen |
| 14 / API 34 | nur kompiliert, realer Test offen |
| 15 / API 35 | nur kompiliert, realer Test offen |
| 16 / API 36 | 19 Instrumentationstests auf physischem RMX3853 erfolgreich |

## Manuell auf echtem Gerät

Rolle annehmen/ablehnen, eingehende Testanrufe für jede Aktion, Dual-SIM, Hersteller-Telefon-App, Prozess-Kaltstart, Benachrichtigung, SAF-Import/Export, Dark Mode, TalkBack, große Schrift, WLAN-only, Feedfehler, PhoneBlock mit Testkonto, API-Key-/Basic-Wechsel und vollständige Löschung.
