# Play-Store-Checkliste

- [x] targetSdk und compileSdk 36
- [x] Kernfunktion als Anruferkennung und Spamfilter dokumentiert
- [x] keine `READ_CALL_LOG`, `READ_CONTACTS`, `READ_PHONE_STATE` oder SMS-Berechtigung
- [x] keine nicht erteilbare `READ_BLOCKED_NUMBERS`-Berechtigung; Systemliste nur per Nutzerexport
- [x] `INTERNET` nur für optionale HTTPS-Feeds/Websuche
- [x] `POST_NOTIFICATIONS` kontextbezogen und optional
- [x] von WorkManager zusammengeführte normale Berechtigungen für Netzwerkstatus, Wake-Lock, Neustart und Foreground-Service dokumentiert
- [x] Kernschutz benötigt kein Konto; PhoneBlock-Konto ist optional
- [x] SDK-Liste in `THIRD_PARTY_NOTICES.md`
- [x] Data-Safety-Entwurf: lokale Telefonnummern/Regeln/Entscheidungen; optionale PhoneBlock-Authentifizierung und nutzeraktivierte Übertragung gemeldeter Nummern/Kategorien/Notizen; nutzerinitiierte externe Websuche
- [ ] PhoneBlock-Testzugang für Play-Review bereitstellen, falls die optionale Kontofunktion geprüft werden soll
- [ ] öffentliche Datenschutz-URL ergänzen
- [ ] Play-Richtlinien am Veröffentlichungstag erneut prüfen
- [ ] Produktionsschlüssel sicher erzeugen und AAB signieren
- [ ] Store-Kategorie „Tools“ prüfen
- [ ] Inhaltsbewertung durchführen
- [ ] App-Icon auf finalen Markenstand bringen
- [ ] Feature Graphic und echte Gerätescreenshots erstellen
- [ ] kurze und lange Storebeschreibung finalisieren
- [ ] Closed-Test auf unterstützten Android-Versionen durchführen

Die aktuelle Play-Anforderung ab 31. August 2026 verlangt für neue Apps und Updates Target API 36 oder höher. Diese Checkliste ist keine Konformitätszusage.
