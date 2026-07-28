# Sicherheit

## Unterstützte Version

Sicherheitskorrekturen werden für die aktuelle Hauptversion 1.x vorgesehen. Dieses lokale Projekt besitzt noch keinen öffentlichen Meldekanal.

## Sicherheitsmerkmale

- ausschließlich HTTPS; Klartextverkehr ist im Manifest und in der Network Security Configuration deaktiviert
- Screening-Service ist exportiert, aber durch `android.permission.BIND_SCREENING_SERVICE` geschützt
- keine weiteren App-Komponenten sind exportiert, abgesehen von der Launcher-Activity
- keine Zugangsdaten oder API-Schlüssel im Repository
- optionale PhoneBlock-Zugangsdaten mit Android-Keystore-AES-GCM im `noBackupFilesDir`
- feste PhoneBlock-HTTPS-Basisadresse, keine Redirects und keine frei konfigurierbare Authentifizierungs-Domain
- 25-MiB- und 250.000-Eintragsgrenze für PhoneBlock-Antworten
- keine vollständigen Telefonnummern in technischen Logs oder Benachrichtigungen
- Größen-, Datensatz-, UTF-8-, Schema-, Zahlen-, Zeitstempel- und Duplikatprüfung beim Import
- atomarer Datenbankimport; bei Fehler bleibt der letzte gültige Stand aktiv
- unveränderlicher, atomar getauschter In-Memory-Snapshot
- interne Screening-Fehler führen zu `ALLOW`
- PhoneBlock-Synchronisation und Meldungen sind vollständig vom Screening-Pfad getrennt

## Meldung

Bei einer Veröffentlichung ist ein privater Security-Kontakt einzurichten. Meldungen sollten Version, Android-Version, reproduzierbare Schritte und Auswirkungen enthalten, jedoch keine realen Telefonnummern oder fremden personenbezogenen Daten.
