# Build-Umgebung

Verifiziert am 28. Juli 2026.

| Bestandteil | Version |
|---|---|
| JDK | Eclipse Temurin 17.0.18 |
| Gradle Wrapper | 8.13 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| compileSdk / targetSdk | 36 |
| minSdk | 29 |
| Android Build Tools | 36.0.0 |

AGP 8.13 ist eine stabile, mit Gradle 8.13 und API 36 kompatible Linie. Die Version wurde für konservative Kompatibilität mit Kotlin 2.2.21 und dem gepinnten KSP gewählt; Preview-, RC-, Snapshot- und dynamische Versionen werden nicht verwendet.

Offiziell verifiziert wurden:

- `CallScreeningService` muss bei eingehenden Anrufen innerhalb von fünf Sekunden antworten.
- `ROLE_CALL_SCREENING` existiert ab API 29.
- Call Screening ab Android 10 benötigt keine `READ_CALL_LOG`-Berechtigung.
- Für neue Play-Einreichungen gilt ab 31. August 2026 mindestens Target API 36.
- Room empfiehlt KSP für Kotlin-Projekte.
- WorkManager ist für persistente, aufgeschobene Arbeit vorgesehen.

Referenzen:

- https://developer.android.com/reference/android/telecom/CallScreeningService
- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls
- https://developer.android.com/google/play/requirements/target-sdk
- https://developer.android.com/training/data-storage/room
