# Release-Bericht

Stand: 28. Juli 2026

## Projektstatus

- Gesamtstatus: Build- und automatisierte Qualitätsprüfungen erfolgreich; reale Telefonanrufe und ältere Android-Versionen bleiben manuell zu prüfen.
- App-Version: 1.2.0 (`versionCode 3`), Debugkennzeichnung `1.2.0-dev`
- Paket: `de.kruemmel.rufwaechter`, Debug: `de.kruemmel.rufwaechter.debug`
- minSdk 29, targetSdk 36, compileSdk 36
- Kotlin 2.2.21, AGP 8.13.0, Gradle 8.13, JDK 17.0.18

## Ausgeführte Builds

| Prüfung | Ergebnis |
|---|---|
| `gradlew --version` | erfolgreich |
| `gradlew clean` | erfolgreich |
| `gradlew lintDebug` | erfolgreich, 0 Fehler; 17 geprüfte Versionshinweise |
| `gradlew testDebugUnitTest` | 39 Tests, 0 Fehler |
| `gradlew assembleDebug` | erfolgreich |
| `gradlew assembleDebugAndroidTest` | erfolgreich |
| `gradlew connectedDebugAndroidTest` | 19 Tests auf RMX3853 / Android 16, 0 Fehler |
| `gradlew bundleRelease` | erfolgreich |

Der generierte Buildordner wurde vor dem maßgeblichen Lauf vollständig entfernt. `clean`, Lint, JVM-Tests, APK, Test-APK und Release-Bundle liefen erfolgreich. Der Instrumentationslauf wurde wegen einer herstellerspezifischen Installations-/Activity-Start-Race getrennt und nach Vorinstallation der APK erfolgreich ausgeführt.

```text
gradlew clean lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest bundleRelease
BUILD SUCCESSFUL
```

Anschließend lief `connectedDebugAndroidTest` nach einer präzisierten UI-Assertion erfolgreich.

## Artefakte

- Debug-APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release-AAB: `app/build/outputs/bundle/release/app-release.aab`
- Lint: `app/build/reports/lint-results-debug.html`
- JVM-Tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Gerätetests: `app/build/reports/androidTests/connected/debug/index.html`

SHA-256:

```text
7979f7965a873ad6375c94f2d3717f608cc21dde1889746795130197f8634686  app-debug.apk
f8f29a27460b97c72b23879f3334b6201799fc32a0bb378b76bd8ae5adea44f7  app-release.aab
```

`aapt` bestätigte für die APK `compileSdkVersion=36`, `minSdkVersion=29` und `targetSdkVersion=36`. `apksigner` bestätigte die Debugsignatur (APK Signature Scheme v2, Android-Debugzertifikat). `jarsigner` bestätigte, dass das Release-AAB absichtlich unsigniert ist; eine Produktionssignatur wurde nicht erzeugt.

## Bewusste Einschränkungen

- `WARN` wird vom Android-Adapter zugelassen und lokal erklärt; die Plattform-`CallResponse` unterstützt keine frei gestaltbare Warnbeschriftung.
- Die bebilderte Hilfe wurde mit großer Systemschrift und Dark Mode auf dem Android-16-Testgerät visuell geprüft; TalkBack wurde nicht vollständig manuell durchlaufen.
- Reale eingehende Anrufe, Carrier-Verifikationswerte, Dual-SIM und herstellerspezifische Telefon-Apps wurden nicht automatisiert geprüft.
- Android 10 bis 15 wurden nur gegen das SDK kompiliert, nicht auf Geräten ausgeführt.
- Die PhoneBlock-Produktion wurde nicht mit den im Screenshot offengelegten Zugangsdaten aufgerufen. API-Parser, Keystore, Datenbank, Migration und Oberfläche wurden lokal beziehungsweise auf dem Gerät getestet; ein separater PhoneBlock-Testaccount bleibt für einen Ende-zu-Ende-Netztest erforderlich.
- Die Android-System-Sperrliste kann von einer reinen Call-Screening-App nicht gelesen werden. Vorhandene Systemsperren bleiben wirksam; Übernahme erfolgt nur aus einer nutzerseitig exportierten Datei.
- Store-Medien, öffentliche Datenschutz-URL und Produktionssignatur fehlen absichtlich.

## Sicherheits- und Datenschutzprüfung

- kein Netzwerkzugriff im Screening-Pfad
- fail-open bei Exception oder internem Soft-Timeout
- Service mit `BIND_SCREENING_SERVICE` geschützt
- keine Call-Log-, Kontakt-, SMS-, Standort-, Mikrofon-, Kamera- oder allgemeine Speicherberechtigung
- Klartextverkehr und App-Datenbackup deaktiviert
- Importgrößen und Datensätze begrenzt, Werte validiert, SHA-256 berechnet und Daten transaktional installiert
- PhoneBlock-Zugangsdaten AES-GCM-verschlüsselt mit Android-Keystore-Schlüssel, nicht im Backup oder Export
- PhoneBlock-Meldungen nur nach separatem Opt-in; kein Netzwerk im Screening-Pfad
- keine Berechtigung für die geschützte Android-System-Sperrliste
- keine Telemetrie, Werbung, Analyse oder Crash-Reporting
