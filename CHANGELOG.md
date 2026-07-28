# Changelog

## 1.2.0 – 2026-07-28

- jederzeit über die Kopfleiste und die Übersicht erreichbare In-App-Hilfe
- sechs skalierbare, Dark-Mode-taugliche Vektorillustrationen
- statusabhängige Einrichtungsprüfung für Systemrolle, Schutz und PhoneBlock
- klare Kennzeichnung erforderlicher, empfohlener und freiwilliger Schritte
- direkte Aktionen zu Systemrolle, PhoneBlock, Sperrlistenimport, Regeln und Datenschutz
- Empfehlungen für sichere Schwellenwerte und den Umgang mit privaten Nummern

## 1.1.0 – 2026-07-28

- PhoneBlock-Community als getrennte, versionierte Quelle
- monatlicher Vollabgleich und höchstens tägliche inkrementelle Aktualisierung
- verschlüsselte Basic- oder API-Key-Zugangsdaten im Android Keystore
- persönliche PhoneBlock-Black-/Whitelist bei API-Key-Anmeldung
- freiwillige, abschaltbare Meldung manueller Sperren mit wählbarer Kategorie
- nutzervermittelter Import exportierter System-/Telefon-Sperrlisten
- Room-Schema 2 mit expliziter Migration von Schema 1

## 1.0.0 – 2026-07-28

- erste vollständige lokale Android-Implementierung
- Call-Screening-Rolle und geschützter `CallScreeningService`
- deterministische Regeln, Scoring, Erklärungen und In-Memory-Snapshot
- Room, DataStore, eigener Verlauf und Benutzerkorrekturen
- JSON-Import, HTTPS-Provider, WorkManager und Datenexport
- Compose-Oberfläche mit Onboarding, Übersicht, Prüfung, Regeln, Verlauf, Einstellungen und Datenschutz
- Unit-, Leistungs- und Parallelitätstests sowie Release-Dokumentation
