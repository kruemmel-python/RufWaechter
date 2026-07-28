# Drittanbieterhinweise

Die Anwendung bindet keine Werbe-, Analyse-, Tracking- oder Crash-Reporting-SDKs ein.

| Komponente | Zweck | Lizenz |
|---|---|---|
| Kotlin und Kotlin Coroutines | Sprache und Nebenläufigkeit | Apache-2.0 |
| AndroidX Core, Activity, Lifecycle, Navigation | Android-Grundfunktionen | Apache-2.0 |
| Jetpack Compose und Material 3 | Benutzeroberfläche | Apache-2.0 |
| AndroidX Room | lokale SQLite-Persistenz | Apache-2.0 |
| AndroidX DataStore | Einstellungen | Apache-2.0 |
| AndroidX WorkManager | optionale Hintergrundpflege | Apache-2.0 |
| JUnit und AndroidX Test | Tests, nicht im Release-Laufzeitcode | EPL-1.0 / Apache-2.0 |

Die konkret gepinnten Versionen stehen in `gradle/libs.versions.toml`. Lizenztexte der AndroidX-Artefakte werden durch deren AAR-/POM-Metadaten bereitgestellt.

PhoneBlock ist ein optionaler, von RufWächter unabhängiger Datendienst. RufWächter verwendet dessen dokumentierte HTTPS-API, übernimmt aber keinen PhoneBlock-Quellcode. PhoneBlock weist seinen Quellcode als GPL-3.0 und Webseiteninhalte als CC BY-NC-SA 4.0 aus. Für die Dienstnutzung gelten die jeweils aktuellen Bedingungen:

- https://phoneblock.net/phoneblock/usage
- https://phoneblock.net/phoneblock/datenschutz?lang=de
- https://phoneblock.net/phoneblock/api
