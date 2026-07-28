# Datenbankschema

Room-Datenbank: `rufwaechter.db`, Schema-Version 2. Exportiertes Schema: `app/schemas`.

## Tabellen

- `number_rules`: Typ, normalisierter Wert, Aktion, Aktivstatus, Zeitstempel, Ablauf, Notiz, Quelle. Eindeutiger Index auf Typ und normalisiertem Wert sowie Indizes für Nummer, Aktivstatus und Ablauf.
- `number_reputation`: normalisierte Nummer als Primärschlüssel, Score, Konfidenz, Kategorie, Zähler, Aktualisierung, Ablauf und Herkunfts-JSON.
- `call_decisions`: ausschließlich von RufWächter erzeugter Verlauf mit Identität, Zeitpunkt, Aktion, Gründen, Carrier-Status, Laufzeit und Nutzerkorrektur.
- `feed_metadata`: Quelle, Version, Zeitpunkt, Anzahl, SHA-256, Status und bereinigte Fehlermeldung.
- `phoneblock_entries`: getrennte Community-, persönliche Blacklist- und Whitelist-Einträge mit Bewertung, Stimmenzahl und Quellzeitstempeln.
- `phoneblock_sync_state`: serverseitige Versionsnummer sowie Zeitpunkte des letzten Voll-, Inkremental- und persönlichen Abgleichs.
- `phoneblock_pending_reports`: lokale Outbox für ausdrücklich freigegebene Meldungen mit Kategorie, Notiz und Versuchszähler.

Einstellungen liegen transaktional in Preferences DataStore und sind bewusst nicht mit Room gekoppelt. Ein leerer oder vorübergehend nicht verfügbarer Datenbankzustand führt im Screening zu `ALLOW`.

Schema 1 ist die Baseline. `MIGRATION_1_2` ergänzt ausschließlich die drei PhoneBlock-Tabellen und ihre Indizes; bestehende Regeln, Reputation und Verlauf bleiben unverändert. Destruktive Migration ist nicht konfiguriert.

PhoneBlock-Zugangsdaten sind bewusst kein Datenbankinhalt. Sie liegen AES-GCM-verschlüsselt im `noBackupFilesDir`; der Schlüssel wird im Android Keystore erzeugt.
