# Bedrohungsmodell

| Bedrohung | Auswirkung | Maßnahme |
|---|---|---|
| gefälschte Rufnummer / Spoofing | Fehlklassifikation | Carrier-Signal nur gewichten; persönliche Regeln und Konfidenzgrenze |
| manipulierter Feed | falsche Blockierungen | ausschließlich HTTPS, Größen-/Schema-/Werteprüfung, SHA-256, atomare Transaktion |
| bösartige Importdatei | Speicher-/CPU-Verbrauch | 5-MiB- und 50.000-Datensatzlimit, UTF-8-Prüfung, begrenzte Texte, Streaming-Parser |
| große Regelmenge | Antwortverzögerung | Map für exakte Treffer, präfixbasierter Index, Leistungstest |
| Race beim Snapshottausch | inkonsistente Entscheidung | unveränderliche Daten und atomarer Gesamttausch |
| automatische Fehlblockierung | verpasster Anruf | Mindestkonfidenz, schwache Quelle maximal Warnung, Benutzerfreigabe höchste Priorität |
| Online-Datenschutzverlust | Offenlegung einer Nummer | kein Netz im Anrufpfad; Websuche erst nach Bestätigung |
| kompromittierte Quelle | veraltete/falsche Daten | Ablauf, Herkunft, letzte gültige Basis bleibt aktiv |
| exportierte Komponente | unbefugter Aufruf | Service mit Systemberechtigung; nur Launcher-Activity sonst exportiert |
| Log-Leak | Offenlegung | keine rohen Nummern in Logcat; Benachrichtigungen maskiert |
| Backup-Leak | Cloudkopie lokaler Daten | Backup und Gerätetransfer für Datenbank/Preferences deaktiviert |
| Diebstahl von PhoneBlock-Zugangsdaten | Kontozugriff und fremde Meldungen | AES-GCM mit Android-Keystore-Schlüssel, `noBackupFilesDir`, keine Logs/Exporte |
| Missbrauch der Meldefunktion | unerwünschte Offenlegung oder Falschmeldung | separate Opt-in-Zustimmung, manuelle Kategorie, lokale Outbox, keine automatische Anrufmeldung |
| übergroße PhoneBlock-Antwort | Speicher-/Plattenverbrauch | 25-MiB-Streamgrenze, maximal 250.000 Einträge, Room-Transaktion |
| kompromittierter PhoneBlock-Endpunkt | falsche Community-Sperren | feste HTTPS-Domain, keine Redirects, lokale Freigabe hat Vorrang, Quelle getrennt löschbar |
| Zugriff auf Android-System-Sperrliste | unnötige hochprivilegierte Berechtigung | kein `READ_BLOCKED_NUMBERS`; nur nutzervermittelter Dateiimport |

Restrisiko: Android und Hersteller-Telefon-Apps bestimmen letztlich, ob und wie der Service aufgerufen und eine Systemaktion dargestellt wird.
