# Datenschutzerklärung für RufWächter

Stand: 28. Juli 2026

RufWächter funktioniert ohne RufWächter-Benutzerkonto, Werbung, Telemetrie, Analyse oder Tracking. Die App verarbeitet Telefonnummern, persönliche Regeln, lokale Reputationsdaten, Einstellungen und eigene Screening-Entscheidungen auf dem Gerät. Ein bestehendes PhoneBlock-Konto kann optional verbunden werden.

## Lokale Daten

Gespeichert werden:

- vom Benutzer angelegte Regeln und Notizen,
- importierte Reputationsdatensätze und deren Herkunft,
- die von RufWächter selbst getroffenen Entscheidungen einschließlich Nummer, Zeitpunkt, Aktion, Score und Begründung,
- Einstellungen und technische Feed-Metadaten.
- optional synchronisierte PhoneBlock-Community- und persönliche Listen,
- optional wartende, vom Benutzer freigegebene PhoneBlock-Meldungen.

Die App liest weder Kontakte noch SMS noch Standortdaten und fordert keinen allgemeinen Zugriff auf das System-Anrufprotokoll an. Lokale Daten sind von Cloud-Backup und Gerätetransfer ausgeschlossen. Der Benutzer kann den Verlauf separat oder sämtliche App-Daten vollständig löschen.

## Netzwerk

Automatische Online-Feeds und PhoneBlock sind standardmäßig deaktiviert. Nach Aktivierung eines allgemeinen Feeds wird ausschließlich die konfigurierte HTTPS-Adresse durch WorkManager geladen. Die App überträgt bei Feed-Downloads keine einzelne aktuell anrufende Telefonnummer. Bei der ausdrücklich bestätigten Websuche wird die Nummer an den gewählten Browser beziehungsweise Suchanbieter übertragen.

Bei aktiviertem PhoneBlock-Abgleich werden die gespeicherten Zugangsdaten zur HTTP-Authentifizierung an `phoneblock.net` gesendet und die Community-Liste abgerufen. Bei API-Key-Anmeldung werden zusätzlich die persönliche Black- und Whitelist abgerufen. Es findet kein PhoneBlock-Netzwerkzugriff während eines eingehenden Anrufs statt.

„Eigene Sperren an PhoneBlock melden“ ist eine separate, standardmäßig ausgeschaltete Einwilligung. Ist sie aktiv, übermittelt RufWächter bei einer manuellen Sperre die vollständige normalisierte Telefonnummer, die gewählte Bewertungskategorie und die vom Benutzer gespeicherte Notiz an PhoneBlock. Bestehende lokale Sperren werden nur nach Betätigung der gesonderten Schaltfläche vorgemerkt. Der lokale Anrufverlauf wird nicht übertragen. Für Verarbeitung und Löschung beim Dienst gelten die [PhoneBlock-Datenschutzerklärung](https://phoneblock.net/phoneblock/datenschutz?lang=de) und dessen Kontoeinstellungen.

PhoneBlock-Zugangsdaten werden mit AES-GCM und einem nicht exportierbaren Schlüssel des Android Keystore verschlüsselt im nicht sicherbaren App-Verzeichnis gespeichert. Passwort oder API-Schlüssel erscheinen weder im Datenexport noch in Feed-Metadaten.

WorkManager bringt technisch die normalen Berechtigungen für Netzwerkstatus, Wake-Lock, Foreground-Service und den Empfang eines Geräteneustarts mit, damit aktivierte Hintergrundpflege zuverlässig neu geplant werden kann.

Keine Netzwerkoperation ist Bestandteil der zeitkritischen Anrufentscheidung.

## Benachrichtigungen

Benachrichtigungen werden nur nach Zustimmung verwendet. Sie enthalten ausschließlich eine maskierte Nummer und die lokale Aktion.

## Weitergabe und Aufbewahrung

RufWächter betreibt keinen eigenen Server und übermittelt keine Daten an die Projektverantwortlichen. Aufbewahrung und Löschung finden lokal statt. Ein vom Benutzer ausgelöster Export wird an ein über den Android-Dateidialog gewähltes Ziel geschrieben und enthält keine PhoneBlock-Zugangsdaten. „Alle lokalen Daten löschen“ entfernt auch Zugangsdaten, PhoneBlock-Listen und ausstehende Meldungen. Bereits an PhoneBlock übertragene Daten und das PhoneBlock-Konto müssen direkt dort verwaltet oder gelöscht werden.

Vor einer Store-Veröffentlichung müssen Verantwortlicher, Kontaktweg und öffentliche Datenschutz-URL ergänzt werden.
