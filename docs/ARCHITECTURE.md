# Architektur

## Zielbild

RufWächter ist eine Local-First-App mit einem App-Modul. Constructor Injection erfolgt über `AppContainer`; ein DI-Framework ist nicht nötig.

```text
Android Telecom
  -> RufWaechterScreeningService
  -> SafeScreeningEvaluator
  -> atomarer ScreeningSnapshot
  -> ScreeningEngine
  -> CallResponse

Compose UI -> MainViewModel -> AppRepository -> Room
                       \-> SettingsRepository -> DataStore
Import/Worker -> Provider -> JSON-Prüfung -> Room-Transaktion -> neuer Snapshot
PhoneBlockWorker -> feste HTTPS-API -> inkrementelle Quelltabellen -> neuer Snapshot
Manuelle Sperre -> zustimmungspflichtige Outbox -> PhoneBlock /rate
```

## Zeitkritischer Pfad

`onScreenCall()` liest ausschließlich `Call.Details`, normalisiert defensiv, lädt eine atomare Referenz und wertet Maps beziehungsweise einen nach Präfix gruppierten Index aus. Netzwerk, WorkManager-Warten, Compose, Migrationen und Dateiimport sind ausgeschlossen. Ein Soft-Limit von 750 ms erzeugt fail-open. Die offizielle Plattformgrenze beträgt fünf Sekunden.

Der Snapshot ist unveränderlich und wird als Ganzes mit `AtomicReference` ersetzt. Beim Prozessstart ist sofort ein sicherer leerer Snapshot vorhanden; bis Room und DataStore geladen sind, werden Anrufe zugelassen.

## Entscheidungen

Domain und Android-Adapter sind getrennt. `WARN` wird als Zulassen umgesetzt und im lokalen Verlauf erklärt, weil `CallResponse` keine frei gestaltbare Warnbeschriftung anbietet. `SILENCE` setzt nur `setSilenceCall(true)`. `BLOCK` setzt `setDisallowCall(true)` und `setRejectCall(true)`.

## Risiken und Entscheidungen

- Hersteller können die Telefon-App oder Rollenoberfläche abweichend implementieren: real testen.
- Ein leerer Startsnapshot kann einen sehr frühen Anruf zulassen: bewusstes Fail-open.
- Carrier-Verifikation ist nur ein Signal und blockiert niemals allein.
- Feed-Daten sind nicht vertrauenswürdig: vollständig validieren und transaktional installieren.
- PhoneBlock-Community, persönliche Listen und lokale Regeln bleiben in getrennten Tabellen beziehungsweise Quellen. Lokale Regeln haben Vorrang; eine persönliche PhoneBlock-Whitelist hat Vorrang vor der Community-Liste.
- PhoneBlock-Zugangsdaten liegen außerhalb von Room und DataStore verschlüsselt im `noBackupFilesDir`; der Schlüssel verbleibt im Android Keystore.
- Ein initialer PhoneBlock-Vollabgleich wird durch tägliche versionierte Deltas fortgeführt. Ein neuer Vollabgleich findet frühestens nach 30 Tagen statt.
- Die geschützte Android-System-Sperrliste ist für die Call-Screening-Rolle nicht lesbar. Eine Datenübernahme ist nur nutzervermittelt aus einer exportierten Datei möglich.
- Der Windows-Workspace enthält ein Nicht-ASCII-Zeichen; `android.overridePathCheck` ist dafür explizit gesetzt.
