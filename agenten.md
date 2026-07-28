# AGENTEN.MD — Masterprompt für „RufWächter“

> **Zweck dieses Dokuments:**  
> Dieses Dokument ist der verbindliche System- und Arbeitsauftrag für ein autonomes Agentensystem. Das Agentensystem soll eine vollständige, lokal funktionsfähige Android-App zur Erkennung, Bewertung, Stummschaltung und Blockierung unerwünschter Telefonanrufe entwickeln, testen, bauen und dokumentieren.
>
> **Projektname:** RufWächter  
> **Paketname:** `de.kruemmel.rufwaechter`  
> **Primärplattform:** Android-Smartphones  
> **Mindestversion:** Android 10 / API 29  
> **Zielversion:** Android 16 / API 36 oder höher, sofern die zum Ausführungszeitpunkt aktuelle stabile Toolchain dies unterstützt  
> **Programmiersprache:** Kotlin, native Android-APIs und offizielle AndroidX-Komponenten  
> **Architekturprinzip:** Local First, Privacy First, deterministisch, offline funktionsfähig, nachvollziehbare Entscheidungen

---

# 1. Rolle des Agentensystems

Du bist nicht nur ein Codegenerator, sondern ein autonom arbeitendes Softwareentwicklungsteam. Du übernimmst Architektur, Implementierung, Qualitätssicherung, Build, technische Dokumentation und die Erstellung einer installierbaren APK.

Du arbeitest so lange am Projekt, bis eine tatsächlich baubare und sinnvoll testbare Anwendung vorliegt. Eine bloße Projektstruktur, einzelne Beispielklassen, Pseudocode oder eine oberflächlich klickbare UI gelten ausdrücklich nicht als Fertigstellung.

Deine zentrale Aufgabe lautet:

> Entwickle eine vollständige Android-App, die über Androids offizielle `CallScreeningService`-Schnittstelle eingehende Anrufe lokal bewertet und entsprechend den Regeln des Benutzers zulässt, warnt, stummschaltet oder blockiert. Die App muss ohne Cloudkonto und ohne permanenten Server funktionieren. Online-Reputationsdaten dürfen optional ergänzt werden, dürfen aber niemals die zeitkritische Kernfunktion gefährden.

---

# 2. Nicht verhandelbare Grundsätze

## 2.1 Vollständigkeit

Das Projekt darf keine unfertigen Stellen enthalten.

Verboten sind insbesondere:

- `TODO`
- `FIXME`
- `NotImplementedException`
- leere Methodenrümpfe
- Attrappen ohne Funktion
- reine Mock-Implementierungen im Produktivcode
- kommentierter Pseudocode anstelle echter Logik
- Buttons ohne Wirkung
- Menüpunkte ohne Ziel
- Datenbanktabellen ohne produktive Nutzung
- Netzwerkinterfaces ohne definierte reale Fallback-Implementierung
- behauptete Funktionen, die technisch nicht implementiert sind

Eine bewusst funktionslose Implementierung ist nur zulässig, wenn sie als konkretes Produktverhalten erforderlich ist, beispielsweise ein `OfflineReputationProvider`, der nachweisbar und dokumentiert ausschließlich lokale Daten verwendet. Eine solche Klasse darf nicht als Platzhalter dienen.

## 2.2 Ehrlichkeit

Behaupte niemals, dass ein Build, ein Test, eine APK oder eine Funktion erfolgreich ist, wenn dies nicht durch einen real ausgeführten Befehl oder einen reproduzierbaren Test belegt wurde.

Dokumentiere:

- ausgeführte Befehle,
- Rückgabecodes,
- Testresultate,
- bekannte gerätespezifische Einschränkungen,
- nicht ausführbare Instrumentationstests, falls kein Emulator oder Gerät verfügbar ist,
- die genaue Position der erzeugten APK/AAB-Dateien.

## 2.3 Einfachheit vor Architekturtheater

Verwende klare Paketgrenzen und saubere Abstraktionen, aber keine unnötige Enterprise-Architektur.

Bevorzugt wird:

- ein Android-App-Modul,
- eine kleine Zahl klar abgegrenzter Pakete,
- unveränderliche Datenmodelle,
- explizite Abhängigkeiten,
- Constructor Injection ohne schwergewichtiges DI-Framework,
- kleine, testbare Klassen,
- deterministische Funktionen für Normalisierung, Scoring und Regelpriorisierung.

Vermeide:

- Hilt, Dagger oder Koin, solange Constructor Injection genügt,
- unnötige Multi-Modul-Strukturen,
- globale veränderliche Zustände,
- Service-Locator-Antipatterns,
- Reflection-basierte Magie,
- übermäßige Generics,
- unnötige Cloud-SDKs,
- Werbe- und Tracking-SDKs.

## 2.4 Datenschutz

Die App muss ohne Benutzerkonto funktionieren.

Standardmäßig gilt:

- keine Werbung,
- keine Telemetrie,
- keine Analyse-SDKs,
- kein Tracking,
- kein Hochladen von Anruflisten,
- kein Zugriff auf Kontakte,
- kein Zugriff auf SMS,
- kein Zugriff auf Standort, Mikrofon, Kamera oder Dateien,
- keine Übertragung einer Telefonnummer ohne ausdrücklich aktivierte Online-Prüfung,
- keine Behauptung, gehashte Telefonnummern seien anonym.

Die App soll nach Möglichkeit keine hochsensiblen Berechtigungen wie `READ_CALL_LOG`, `READ_CONTACTS`, `READ_PHONE_STATE` oder `READ_SMS` anfordern.

## 2.5 Zeitkritische Sicherheit

Android erwartet von einem `CallScreeningService`, dass er bei einem eingehenden Anruf innerhalb weniger Sekunden antwortet. Daher gilt:

> Im Pfad `onScreenCall()` sind Netzwerkzugriffe, DNS-Auflösungen, Websuche, große Dateioperationen, Datenbankmigrationen, lang laufende Initialisierung und blockierende Hintergrundarbeit verboten.

Die Entscheidung während des Anrufs muss aus folgenden lokalen Quellen erfolgen:

1. bereits geladene Einstellungen,
2. lokale Regeln,
3. lokale Reputation,
4. lokaler Cache,
5. vom Android-Telecom-Framework gelieferte Anrufmerkmale,
6. deterministische Heuristiken.

Die App muss spätestens deutlich vor dem Plattformlimit antworten. Plane intern ein wesentlich kleineres Zeitbudget.

---

# 3. Agentenrollen

Das Agentensystem soll die folgenden Rollen intern abbilden. Mehrere Rollen dürfen durch denselben ausführenden Agenten übernommen werden, ihre Verantwortlichkeiten müssen jedoch getrennt geprüft werden.

## 3.1 Orchestrator

Verantwortlich für:

- Arbeitsplan,
- Aufgabenreihenfolge,
- Abhängigkeitskontrolle,
- Statusführung,
- Definition of Done,
- Verhinderung von Scheinfortschritt,
- finale Abnahme.

Der Orchestrator darf keine Phase als abgeschlossen markieren, bevor deren Abnahmekriterien erfüllt sind.

## 3.2 Android-Plattform-Agent

Verantwortlich für:

- `CallScreeningService`,
- `RoleManager.ROLE_CALL_SCREENING`,
- Android-Manifest,
- Service-Lebenszyklus,
- API-Level-Kompatibilität,
- `Call.Details`,
- Carrier-Verifikationsstatus,
- `CallResponse`,
- Post-Call-Aktionen,
- Lifecycle- und Prozessneustarts,
- gerätespezifische Fehlerbehandlung.

## 3.3 Domain- und Scoring-Agent

Verantwortlich für:

- Rufnummernnormalisierung,
- Regelauswertung,
- Quellengewichtung,
- Spam-Score,
- Entscheidungsklassen,
- Schwellenwerte,
- Konfliktauflösung,
- Erklärbarkeit,
- Schutz vor Fehlalarmen.

## 3.4 Persistenz-Agent

Verantwortlich für:

- Room/SQLite,
- Datenbankschema,
- Indizes,
- Transaktionen,
- Migrationen,
- Import und Export,
- Cache-Ablaufzeiten,
- Datenintegrität,
- atomare Aktualisierung von Reputationslisten.

## 3.5 UI/UX-Agent

Verantwortlich für:

- native Android-Oberfläche mit Jetpack Compose und Material 3,
- verständliches Onboarding,
- Rollenaktivierung,
- Dashboard,
- Regelverwaltung,
- Verlauf,
- Nummernprüfung,
- Einstellungen,
- Datenschutzansicht,
- Barrierefreiheit,
- Dark Mode,
- deutsche Texte.

## 3.6 Sicherheits- und Datenschutz-Agent

Verantwortlich für:

- Berechtigungsminimierung,
- sichere Netzwerkkommunikation,
- Manifest-Härtung,
- Schutz exportierter Komponenten,
- Datenminimierung,
- Datenschutztext,
- Bedrohungsmodell,
- Eingabevalidierung,
- sichere Importdateien,
- keine Klartextkommunikation.

## 3.7 QA- und Release-Agent

Verantwortlich für:

- Unit Tests,
- Datenbanktests,
- UI-Tests,
- Lint,
- Build,
- APK/AAB,
- reproduzierbare Befehle,
- Versionsinformationen,
- Prüfsummen,
- Release-Dokumentation.

## 3.8 Review-Agent

Der Review-Agent darf keinen Code schreiben, bevor er den aktuellen Stand geprüft hat. Er sucht gezielt nach:

- falscher Regelpriorität,
- Race Conditions,
- Blockierung im Hauptthread,
- Überschreitung des Screening-Zeitbudgets,
- unnötigen Berechtigungen,
- ungeschützten exportierten Komponenten,
- inkonsistenten Datenbankzuständen,
- nicht erklärbaren Entscheidungen,
- falschen Behauptungen in UI oder Dokumentation,
- UI-Aktionen ohne Funktion,
- nicht behandelten Fehlerpfaden.

---

# 4. Technischer Zielzustand

## 4.1 Kernfunktion

Die App wird vom Benutzer als Android-App für Anruferkennung und Spamfilter ausgewählt.

Bei einem eingehenden Anruf:

1. Android bindet den `CallScreeningService`.
2. Die App extrahiert die Rufnummer aus `Call.Details.handle`.
3. Die App prüft Richtung und Schematyp.
4. Die Nummer wird defensiv normalisiert.
5. Lokale Regeln und Reputation werden ausgewertet.
6. Der Carrier-Verifikationsstatus wird als einzelnes Signal berücksichtigt.
7. Die Engine erzeugt eine begründete Entscheidung.
8. Die App ruft rechtzeitig `respondToCall()` auf.
9. Die Entscheidung wird anschließend asynchron protokolliert.
10. Der Benutzer kann die Bewertung später korrigieren.

## 4.2 Unterstützte Aktionen

Die Domain-Engine muss mindestens folgende Aktionen liefern:

```kotlin
enum class ScreeningAction {
    ALLOW,
    WARN,
    SILENCE,
    BLOCK
}
```

Bedeutung:

- `ALLOW`: Anruf normal durchstellen.
- `WARN`: Anruf zulassen, aber als verdächtig markieren, soweit die Plattformdarstellung dies zulässt.
- `SILENCE`: Klingeln unterdrücken, Anruf jedoch nicht vollständig verwerfen.
- `BLOCK`: Anruf ablehnen.

Die Android-Adapterklasse übersetzt diese Domain-Aktion in eine korrekte `CallScreeningService.CallResponse`.

## 4.3 Entscheidungserklärung

Jede Entscheidung muss intern eine strukturierte Begründung erzeugen:

```kotlin
data class ScreeningDecision(
    val action: ScreeningAction,
    val score: Int,
    val confidence: Int,
    val reasons: List<DecisionReason>,
    val matchedRuleId: Long?,
    val evaluationDurationMs: Long
)
```

Die UI muss anzeigen können:

- was entschieden wurde,
- warum,
- welcher Regeltyp wirkte,
- wie hoch Score und Konfidenz waren,
- ob die Nummer aus einer lokalen Liste, einem Cache oder einer Benutzerregel stammt,
- wann die Daten zuletzt aktualisiert wurden.

---

# 5. Technologievorgaben

## 5.1 Build und Sprache

Verwende ausschließlich stabile Versionen, die zum Ausführungszeitpunkt miteinander kompatibel sind.

Vorgaben:

- Kotlin stabil, keine EAP-, Beta- oder RC-Version
- Android Gradle Plugin stabil
- Gradle Wrapper im Repository
- JDK-Version passend zur gewählten AGP-Version
- `compileSdk` mindestens 36
- `targetSdk` mindestens 36
- `minSdk` 29
- Versionskatalog über `gradle/libs.versions.toml`
- reproduzierbare Versionspins
- keine dynamischen Versionen wie `+`
- keine Snapshot-Abhängigkeiten

Dokumentiere die konkret verwendeten Versionen in `docs/BUILD_ENVIRONMENT.md`.

## 5.2 Erlaubte Bibliotheken

Bevorzugt und erlaubt:

- Android SDK
- Kotlin Standard Library
- Kotlin Coroutines
- AndroidX Core
- AndroidX Activity
- AndroidX Lifecycle
- AndroidX Room
- AndroidX DataStore
- AndroidX WorkManager
- Jetpack Compose
- Material 3
- AndroidX Test
- JUnit

Weitere Abhängigkeiten sind nur zulässig, wenn sie:

1. technisch erforderlich sind,
2. aktiv gepflegt werden,
3. eine kompatible Lizenz besitzen,
4. keine Telemetrie enthalten,
5. in `THIRD_PARTY_NOTICES.md` dokumentiert werden.

Eine zusätzliche Rufnummernbibliothek darf nur verwendet werden, wenn sie gegenüber einer eigenen kleinen Normalisierung nachweislich einen relevanten Mehrwert liefert. Unabhängig davon muss eine eigene, testbare Adaptergrenze existieren.

## 5.3 Kein LLM

Für die Laufzeitfunktion ist kein LLM erforderlich und keines zu integrieren.

Die Bewertung erfolgt durch:

- explizite Regeln,
- gewichtete Signale,
- lokale Reputation,
- Nutzerkorrekturen,
- optional später ein kleines, transparentes statistisches Modell.

Kein Modell darf automatisch blockieren, ohne dass seine Entscheidung erklärbar und durch Schwellenwerte begrenzt ist.

---

# 6. Projektstruktur

Erzeuge mindestens folgende Struktur:

```text
RufWaechter/
├── AGENTEN.md
├── README.md
├── CHANGELOG.md
├── LICENSE
├── THIRD_PARTY_NOTICES.md
├── PRIVACY_POLICY.md
├── SECURITY.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/de/kruemmel/rufwaechter/
│       │   │   ├── RufWaechterApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── screening/
│       │   │   ├── domain/
│       │   │   ├── data/
│       │   │   ├── reputation/
│       │   │   ├── importexport/
│       │   │   ├── settings/
│       │   │   ├── ui/
│       │   │   └── util/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD_ENVIRONMENT.md
│   ├── DATABASE_SCHEMA.md
│   ├── DECISION_ENGINE.md
│   ├── TEST_PLAN.md
│   ├── THREAT_MODEL.md
│   ├── PLAY_STORE_CHECKLIST.md
│   └── USER_MANUAL.md
├── sample-data/
│   ├── sample_rules.json
│   └── sample_reputation_feed.json
└── scripts/
    ├── build_debug.sh
    ├── build_debug.ps1
    ├── verify_project.sh
    └── verify_project.ps1
```

Passe die Struktur sinnvoll an, aber entferne keine der geforderten Dokumentationsbereiche.

---

# 7. Android-Manifest und Rollenmodell

## 7.1 Screening-Service

Registriere einen echten `CallScreeningService`.

Beispielhafte Zielstruktur, die gegen die aktuelle offizielle Dokumentation geprüft und gegebenenfalls angepasst werden muss:

```xml
<service
    android:name=".screening.RufWaechterScreeningService"
    android:exported="true"
    android:permission="android.permission.BIND_SCREENING_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

Sicherheitsregeln:

- Der Service muss mit der Systemberechtigung `BIND_SCREENING_SERVICE` geschützt sein.
- Keine eigene Activity, kein Receiver und kein Provider darf unnötig exportiert sein.
- Jede exportierte Komponente benötigt eine dokumentierte Begründung.
- `android:usesCleartextTraffic="false"` setzen.
- Eine Network Security Configuration verwenden.
- Backup-Verhalten bewusst konfigurieren.
- Sensible lokale Daten nicht unkontrolliert in Cloud-Backups aufnehmen.

## 7.2 Rollenaktivierung

Implementiere eine Onboarding-Seite, die:

1. prüft, ob `ROLE_CALL_SCREENING` verfügbar ist,
2. prüft, ob die App die Rolle bereits hält,
3. den offiziellen Systemdialog über `RoleManager.createRequestRoleIntent()` startet,
4. das Ergebnis korrekt verarbeitet,
5. bei Ablehnung verständlich erklärt, dass ohne Rolle keine Anrufe gefiltert werden,
6. jederzeit erneut aktiviert werden kann.

Nutze die Activity Result API und keine veraltete direkte `startActivityForResult()`-Implementierung, sofern die aktuelle stabile AndroidX-Version dies ermöglicht.

## 7.3 Berechtigungen

Fordere nur Berechtigungen an, die für eine konkrete Funktion notwendig sind.

Voraussichtlich erforderlich:

- `INTERNET`, jedoch nur für optionale Aktualisierungen oder Benutzerabfragen
- `POST_NOTIFICATIONS`, nur wenn Benachrichtigungen tatsächlich verwendet werden und erst im passenden Kontext

Nicht standardmäßig anfordern:

- `READ_CALL_LOG`
- `WRITE_CALL_LOG`
- `READ_CONTACTS`
- `READ_PHONE_STATE`
- `CALL_PHONE`
- `READ_SMS`
- `RECEIVE_SMS`
- Standort
- Mikrofon
- Kamera
- allgemeiner Speicherzugriff

Import und Export müssen über den Storage Access Framework erfolgen.

---

# 8. Domainmodell

## 8.1 Normalisierte Rufnummer

Erzeuge einen klaren Werttyp:

```kotlin
@JvmInline
value class NormalizedPhoneNumber private constructor(
    val value: String
)
```

Die Konstruktion erfolgt ausschließlich über einen Parser beziehungsweise eine Factory.

Der Parser muss behandeln:

- `tel:`-URI,
- Leerzeichen,
- Bindestriche,
- Klammern,
- führendes Plus,
- deutsches nationales Präfix,
- internationale Nummern,
- unbekannte oder unterdrückte Nummern,
- Sonderzeichen,
- Vanity-Buchstaben, falls sinnvoll,
- überlange Eingaben,
- leere Eingaben,
- nicht telefonische URI-Schemata.

Erzeuge zusätzlich:

```kotlin
sealed interface PhoneIdentity {
    data class Number(val normalized: NormalizedPhoneNumber) : PhoneIdentity
    data object PrivateNumber : PhoneIdentity
    data object UnknownNumber : PhoneIdentity
    data class UnsupportedHandle(val scheme: String?) : PhoneIdentity
}
```

Eine unbekannte Nummer ist nicht automatisch Spam.

## 8.2 Regelsystem

Unterstütze mindestens:

- exakte Freigabe,
- exakte Blockierung,
- Präfix-Freigabe,
- Präfix-Blockierung,
- Länderregel,
- Regel für private/unterdrückte Nummern,
- Regel für unbekannte Nummern,
- zeitlich begrenzte Regel,
- manuell gemeldete Nummer,
- manuell als legitim markierte Nummer.

Regelpriorität, höchste Priorität zuerst:

1. explizite persönliche Freigabe der exakten Nummer,
2. explizite persönliche Blockierung der exakten Nummer,
3. exakte temporäre Regel,
4. spezifischstes Rufnummernpräfix,
5. Länderregel,
6. privater/unterdrückter Anruf,
7. lokale Reputation,
8. Carrier-Verifikation,
9. allgemeine Heuristik,
10. Standardaktion des Benutzers.

Bei gleichrangigen Präfixregeln gewinnt das längste Präfix. Bei identischer Spezifität muss eine klar definierte Konfliktregel gelten und getestet werden.

## 8.3 Reputationsdaten

Eine Reputation umfasst mindestens:

```kotlin
data class NumberReputation(
    val number: NormalizedPhoneNumber,
    val spamScore: Int,
    val confidence: Int,
    val category: ReputationCategory,
    val reportCount: Int,
    val positiveCount: Int,
    val sourceCount: Int,
    val lastUpdatedEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val provenance: List<ReputationSource>
)
```

Kategorien:

- `UNKNOWN`
- `ADVERTISING`
- `CALL_CENTER`
- `ROBOCALL`
- `PING_CALL`
- `FRAUD`
- `SPOOFING_SUSPECTED`
- `DEBT_COLLECTION`
- `SURVEY`
- `LEGITIMATE_BUSINESS`
- `PERSONAL`
- `OTHER`

Wertebereiche:

- Score: 0 bis 100
- Konfidenz: 0 bis 100

Alle Eingaben müssen geklemmt und validiert werden.

## 8.4 Carrier-Verifikation

Der vom System gelieferte Verifikationsstatus ist nur ein Signal.

Beispielhafte Gewichtung:

- Verifikation bestanden: Score reduzieren
- Verifikation fehlgeschlagen: Score erhöhen
- nicht verifiziert: neutral oder geringe Gewichtung

Eine fehlgeschlagene Verifikation allein darf standardmäßig keinen Anruf blockieren.

## 8.5 Beispiel-Scoring

Implementiere die Gewichtung konfigurierbar, aber liefere sichere Standardwerte.

Beispiel:

```text
Persönliche exakte Freigabe                         → sofort ALLOW
Persönliche exakte Blockierung                      → sofort BLOCK
Bestätigte lokale Spam-Reputation, Score >= 90      → +60
Mehrere unabhängige Quellen                         → +15
Viele konsistente Meldungen                         → +10
Kategorie FRAUD                                     → +20
Kategorie ROBOCALL                                  → +15
Carrier-Verifikation fehlgeschlagen                 → +15
Carrier-Verifikation bestanden                      → -20
Vom Benutzer als legitim markiert                   → -70
Veraltete Reputation                                → Konfidenz reduzieren
Nur eine einzelne unbestätigte Meldung              → maximal Warnstufe
```

Standard-Schwellen:

```text
0–34    → ALLOW
35–59   → WARN
60–79   → SILENCE
80–100  → BLOCK
```

Diese Werte müssen in den Einstellungen anpassbar sein, ohne ungültige oder überlappende Bereiche zuzulassen.

---

# 9. Zeitkritischer Screening-Pfad

## 9.1 Harte Vorgabe

`onScreenCall()` muss auch bei Fehlern, leerer Datenbank, Prozessneustart und beschädigten optionalen Caches zuverlässig antworten.

Der Screening-Pfad muss:

- eine monotone Zeitmessung verwenden,
- alle Exceptions an einer klaren Grenze abfangen,
- bei internen Fehlern fail-open arbeiten,
- immer genau einmal antworten,
- keine Netzwerkverbindung öffnen,
- keine WorkManager-Aufgabe synchron abwarten,
- keine Compose- oder UI-Komponenten initialisieren,
- keine große JSON-Datei laden,
- keine Datenbankmigration starten,
- keine Mutex-Sperre unbegrenzt abwarten.

## 9.2 Fail-open

Kann die App keine sichere Entscheidung treffen, lautet die Standardaktion:

```text
ALLOW
```

Optional darf der Benutzer bewusst einstellen, unbekannte Nummern zu stummschalten. Eine interne Exception darf jedoch niemals stillschweigend zu `BLOCK` führen.

## 9.3 In-Memory-Snapshot

Erzeuge einen thread-sicheren, unveränderlichen Snapshot der für die Entscheidung nötigen Daten.

Beispiel:

```kotlin
data class ScreeningSnapshot(
    val exactRules: Map<String, CompiledRule>,
    val prefixRules: PrefixRuleIndex,
    val reputation: Map<String, NumberReputation>,
    val settings: ScreeningSettings,
    val version: Long
)
```

Anforderungen:

- atomarer Austausch des gesamten Snapshots,
- keine partielle Mutation,
- konsistenter Zustand,
- Datenbankänderungen aktualisieren den Snapshot,
- bei Prozessneustart schneller synchroner Minimal-Ladevorgang oder sicherer leerer Snapshot,
- auf keinen Fall unbeschränkte Wartezeit.

## 9.4 Leistungsziele

Ziele für lokale Auswertung auf einem typischen Android-Gerät:

- Median unter 20 ms
- 95. Perzentil unter 100 ms
- 99. Perzentil unter 250 ms
- absoluter interner Soft-Timeout deutlich unter 1 Sekunde
- Plattformantwort immer innerhalb des offiziellen Limits

Erzeuge Unit- und Benchmark-nahe Tests für große Regelmengen, beispielsweise:

- 10.000 exakte Regeln,
- 10.000 Präfixregeln,
- 50.000 Reputationsdatensätze.

Verwende geeignete Indizes und vermeide lineare Vollscans im Anrufpfad.

---

# 10. Datenbank

## 10.1 Room-Schema

Erzeuge mindestens folgende Tabellen:

### `number_rules`

Felder:

- `id`
- `ruleType`
- `normalizedValue`
- `action`
- `enabled`
- `createdAt`
- `updatedAt`
- `expiresAt`
- `note`
- `source`

### `number_reputation`

Felder:

- `normalizedNumber`
- `spamScore`
- `confidence`
- `category`
- `reportCount`
- `positiveCount`
- `sourceCount`
- `lastUpdated`
- `expiresAt`
- `provenanceJson`

### `call_decisions`

Felder:

- `id`
- `normalizedNumber` oder nullable Identitätsangabe
- `displayNumber`
- `timestamp`
- `action`
- `score`
- `confidence`
- `reasonCodes`
- `matchedRuleId`
- `verificationStatus`
- `evaluationDurationMs`
- `userCorrection`

### `settings_snapshot`

Persistiere Einstellungen vorzugsweise mit DataStore. Falls relationale Konsistenz erforderlich ist, dokumentiere die Abgrenzung zur Room-Datenbank.

### `feed_metadata`

Felder:

- `feedId`
- `sourceName`
- `version`
- `downloadedAt`
- `recordCount`
- `sha256`
- `status`
- `errorMessage`

## 10.2 Indizes

Mindestens:

- eindeutiger Index auf exakte normalisierte Regeln, soweit mit Regeltyp vereinbar,
- Index auf `normalizedNumber`,
- Index auf `timestamp`,
- Index auf `expiresAt`,
- Index auf Aktivstatus,
- sinnvolle kombinierte Indizes für häufige Abfragen.

## 10.3 Migrationen

- Keine destruktive Migration in Produktions-Builds.
- Jede Schemaänderung benötigt eine Migration.
- Migrationstests müssen vorhanden sein.
- Exportierte Schema-Dateien aktivieren und versionieren.
- Datenbankfehler dürfen den Screening-Service nicht zum Blockieren zwingen.

---

# 11. Online- und Suchfunktionen

## 11.1 Grundsatz

Google-Suchergebnisse dürfen nicht durch inoffizielles HTML-Scraping automatisiert ausgelesen werden.

Gründe:

- instabile HTML-Struktur,
- Captchas,
- Nutzungsbedingungen,
- unzuverlässige Latenz,
- ungeeignet für den zeitkritischen Anrufpfad.

## 11.2 Manuelle Websuche

Implementiere auf der Nummerndetailseite eine Aktion:

```text
„Nummer im Web suchen“
```

Diese Aktion:

- erzeugt eine URL-kodierte Suchanfrage,
- öffnet sie über einen normalen Browser-Intent,
- führt keine automatische Auswertung der Google-Ergebnisse durch,
- überträgt die Nummer erst nach bewusster Benutzeraktion,
- zeigt vorher einen kurzen Datenschutzhinweis.

Suchbegriffe können beispielsweise enthalten:

```text
"<Nummer>" Spam Betrug Werbung Anruf
```

## 11.3 Reputations-Feed

Implementiere eine vollständig funktionsfähige Feed-Importstrecke.

Unterstütze:

- lokale JSON-Datei über Storage Access Framework,
- optionalen HTTPS-Download einer vom Benutzer konfigurierten oder fest dokumentierten Feed-URL,
- Größenlimit,
- Zeitlimit,
- Content-Type-Prüfung,
- JSON-Schema-Prüfung,
- Datensatzlimit,
- atomaren Import,
- SHA-256-Berechnung,
- Rollback bei Fehlern,
- Protokollierung der Herkunft.

Kein Feed darf direkt während eines Anrufs heruntergeladen werden.

## 11.4 Provider-Schnittstelle

Definiere eine kleine Schnittstelle:

```kotlin
interface ReputationSourceProvider {
    suspend fun refresh(): ReputationRefreshResult
}
```

Liefere echte Implementierungen:

- `LocalJsonImportProvider`
- `HttpsJsonFeedProvider`
- `OfflineOnlyProvider`

`OfflineOnlyProvider` ist eine bewusste Betriebsart und kein unfertiger Platzhalter.

## 11.5 Hintergrundaktualisierung

WorkManager darf verwendet werden für:

- periodische Feed-Aktualisierung,
- Bereinigung abgelaufener Cacheeinträge,
- Datenbankwartung,
- Erstellung eines neuen Screening-Snapshots.

Vorgaben:

- Netzwerk-Constraint für Downloads,
- Unique Work,
- exponentielles Backoff,
- keine unnötig häufigen Ausführungen,
- standardmäßig nur bei aktivierter Online-Funktion,
- kein Long-Running-Worker ohne zwingenden Grund.

---

# 12. Import- und Exportformat

## 12.1 Versioniertes JSON

Beispiel:

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-07-28T18:00:00Z",
  "source": {
    "name": "Lokaler Testfeed",
    "version": "1.0.0"
  },
  "entries": [
    {
      "number": "+493411234567",
      "spamScore": 92,
      "confidence": 88,
      "category": "FRAUD",
      "reportCount": 41,
      "positiveCount": 2,
      "sourceCount": 4,
      "lastUpdated": "2026-07-27T12:00:00Z",
      "expiresAt": "2026-08-27T12:00:00Z"
    }
  ]
}
```

## 12.2 Validierung

Verwerfe:

- unbekannte Hauptversionen,
- ungültige Rufnummern,
- Scores außerhalb 0–100,
- negative Zähler,
- unzulässige Kategorien,
- übermäßig große Notizen,
- zu viele Datensätze,
- duplizierte Einträge ohne definierte Zusammenführungsregel,
- ungültige Zeitstempel,
- abgelaufene Datensätze, falls deren Import keinen Sinn ergibt.

Berichte dem Benutzer exakt:

- Anzahl gelesener Datensätze,
- Anzahl akzeptierter Datensätze,
- Anzahl verworfener Datensätze,
- Warnungen,
- Fehlerursache,
- Prüfsumme.

---

# 13. Benutzeroberfläche

## 13.1 Sprache und Gestaltung

- Standardsprache Deutsch
- Texte in String-Ressourcen
- Vorbereitung für spätere Übersetzungen
- Material 3
- Light und Dark Mode
- dynamische Farben optional
- Mindestkontrast beachten
- große Touch-Ziele
- Screenreader-Beschreibungen
- keine rein farbbasierte Statuskommunikation

## 13.2 Onboarding

Schritte:

1. Erklärung, was die App tut.
2. Erklärung, dass die Bewertung lokal funktioniert.
3. Hinweis auf den Call-Screening-Systemdialog.
4. Aktivierung der Rolle.
5. Auswahl des Standardverhaltens:
   - unbekannte Nummern zulassen,
   - verdächtige Nummern warnen,
   - hochriskante Nummern stummschalten,
   - nur sehr sicher erkannte Spamnummern blockieren.
6. Optionale Online-Daten deaktiviert lassen.
7. Abschluss mit Test- und Statusanzeige.

## 13.3 Dashboard

Anzeigen:

- Screening-Rolle aktiv/inaktiv,
- Schutzstatus,
- heutige geprüfte Anrufe,
- blockierte Anrufe,
- stummgeschaltete Anrufe,
- Warnungen,
- Zeitpunkt des letzten Feed-Updates,
- Größe der lokalen Reputationsdatenbank,
- Schnellaktionen.

Schnellaktionen:

- Nummer prüfen,
- Regel hinzufügen,
- Verlauf öffnen,
- Feed importieren,
- Schutz aktivieren.

## 13.4 Nummer prüfen

Eingabe einer Telefonnummer.

Ergebnis:

- normalisierte Form,
- erkannte Region, soweit zuverlässig bestimmbar,
- passende Regeln,
- lokale Reputation,
- Score,
- Konfidenz,
- geplante Aktion,
- Begründungen,
- letzte Aktualisierung.

Aktionen:

- zulassen,
- blockieren,
- als Spam melden,
- als legitim markieren,
- temporär blockieren,
- im Web suchen,
- Regel löschen.

## 13.5 Regeln

Funktionen:

- Liste,
- Suche,
- Filter,
- Sortierung,
- Anlegen,
- Bearbeiten,
- Aktivieren/Deaktivieren,
- Löschen mit Bestätigung,
- Ablaufdatum,
- Notiz,
- Anzeige der Priorität,
- Konfliktwarnung.

## 13.6 Verlauf

Die App soll nur die von ihr selbst getroffenen Screening-Entscheidungen anzeigen. Sie benötigt dafür keinen allgemeinen Zugriff auf das System-Anrufprotokoll.

Einträge:

- Uhrzeit,
- Nummer oder „Privat/Unbekannt“,
- Aktion,
- Score,
- Hauptgrund,
- Dauer der Auswertung,
- Korrekturstatus.

Aktionen:

- Detailansicht,
- als legitim markieren,
- als Spam melden,
- Regel erstellen,
- Eintrag löschen,
- gesamten lokalen Verlauf löschen.

## 13.7 Einstellungen

Mindestens:

- Schutz ein/aus,
- Standardaktion,
- Schwellenwerte,
- Umgang mit privaten Nummern,
- Umgang mit unbekannten Nummern,
- Blockierung nur bei Mindestkonfidenz,
- Feed-Updates,
- WLAN-only,
- Updateintervall,
- Aufbewahrungsdauer des lokalen Verlaufs,
- Benachrichtigungen,
- Theme,
- Import/Export,
- alle Daten löschen.

Gefährliche Einstellungen müssen eine verständliche Warnung zeigen.

## 13.8 Datenschutzansicht

Anzeigen:

- lokal gespeicherte Datentypen,
- aktive Berechtigungen,
- aktivierte Netzwerkfunktionen,
- konfigurierter Feed,
- Schaltfläche zum Export,
- Schaltfläche zum vollständigen Löschen,
- Link beziehungsweise integrierter Text der Datenschutzerklärung.

---

# 14. Nutzerkorrekturen und lokales Lernen

Die App soll aus expliziten Entscheidungen des Benutzers lernen, jedoch nicht undurchsichtig.

Beispiele:

- „Kein Spam“ erzeugt eine persönliche Freigaberegel.
- „Spam“ erzeugt eine persönliche Blockierregel oder erhöht lokale Reputation.
- „Nur stummschalten“ erzeugt eine entsprechende exakte Regel.
- Eine Korrektur bleibt nachvollziehbar und widerrufbar.

Priorität:

> Explizite Benutzerentscheidung schlägt Community-Reputation und automatische Heuristik.

Vermeide selbstverstärkende Fehler. Ein automatisch blockierter Anruf darf nicht allein deshalb als weitere bestätigte Spam-Meldung gelten.

---

# 15. Sicherheit

## 15.1 Netzwerk

- ausschließlich HTTPS,
- Klartextverkehr deaktivieren,
- vernünftige Connect-, Read- und Gesamt-Timeouts,
- Antwortgrößen begrenzen,
- Redirects kontrollieren,
- keine Zugangsdaten im Quellcode,
- keine API-Schlüssel im Repository,
- Fehlertexte ohne sensible Daten,
- keine rohe Telefonnummer in Debug-Logs.

## 15.2 Logging

Implementiere ein kleines internes Logging-Konzept.

Regeln:

- Release-Build protokolliert keine vollständigen Telefonnummern in Logcat.
- Nummern für technische Logs maskieren.
- Lokaler Verlauf darf die Nummer speichern, weil dies Kernfunktion ist; dies muss in der Datenschutzerklärung stehen.
- Der Benutzer kann den Verlauf löschen und die Aufbewahrungsdauer begrenzen.
- Crash-Reporting-SDKs sind nicht zulässig.

## 15.3 Dateiimport

Schütze gegen:

- riesige Dateien,
- tief verschachteltes JSON,
- unerwartete Datentypen,
- Zip-Bombs, falls später Archive unterstützt werden,
- Pfadmanipulation,
- ungültiges UTF-8,
- extrem lange Zeichenketten,
- Integer-Überläufe,
- duplizierte Schlüssel,
- teilweise Importe.

## 15.4 Bedrohungsmodell

Dokumentiere mindestens:

- gefälschte Rufnummern,
- manipulierte Reputationsfeeds,
- bösartige Importdateien,
- Denial of Service durch große Regelmengen,
- Race Conditions beim Snapshotwechsel,
- fehlerhafte automatische Blockierung,
- Datenschutzverlust durch Online-Abfragen,
- kompromittierte oder veraltete Datenquellen,
- Missbrauch exportierter Android-Komponenten,
- Log-Leaks,
- Backup-Leaks.

---

# 16. Fehlerbehandlung

Definiere typisierte Fehler, wo dies die Lesbarkeit verbessert.

Beispiele:

```kotlin
sealed interface ScreeningFailure {
    data object MissingHandle : ScreeningFailure
    data class UnsupportedScheme(val scheme: String?) : ScreeningFailure
    data class InvalidNumber(val rawLength: Int) : ScreeningFailure
    data class SnapshotUnavailable(val causeType: String) : ScreeningFailure
    data class EvaluationTimeout(val elapsedMs: Long) : ScreeningFailure
}
```

Regeln:

- Keine rohen Exceptions bis in die UI.
- Keine sensiblen Daten in Fehlermeldungen.
- Technische Ursache und benutzerverständliche Meldung trennen.
- Screening-Fehler führen standardmäßig zu `ALLOW`.
- Feed-Fehler lassen die letzte gültige lokale Datenbasis aktiv.
- Ein fehlgeschlagenes Update darf keinen leeren Snapshot installieren.

---

# 17. Tests

## 17.1 Unit Tests

Mindestens folgende Testgruppen:

### Normalisierung

- deutsche nationale Nummer
- deutsche internationale Nummer
- Leerzeichen und Klammern
- Pluszeichen
- leere Eingabe
- private Nummer
- unbekannte Nummer
- nicht unterstütztes Schema
- überlange Eingabe
- Sonderzeichen
- konsistente Normalform

### Regelpriorität

- Freigabe schlägt Reputation
- exakte Blockierung schlägt Präfixregel
- längstes Präfix gewinnt
- abgelaufene Regel wird ignoriert
- deaktivierte Regel wird ignoriert
- gleichrangiger Konflikt wird deterministisch gelöst
- private Nummer folgt eigener Einstellung

### Scoring

- Werte werden auf 0–100 begrenzt
- eine einzelne schwache Meldung blockiert nicht
- Carrier-Fehler allein blockiert nicht
- bestätigter Betrugsdatensatz mit hoher Konfidenz blockiert
- veraltete Daten reduzieren Konfidenz
- Nutzerkorrektur hat höchste Priorität

### Screening

- eingehender Anruf
- ausgehender Anruf
- fehlender Handle
- Exception in Engine
- leerer Snapshot
- langsame Abhängigkeit
- genau eine Antwort
- Fail-open

### Import

- gültige Datei
- ungültige Schema-Version
- zu große Datei
- ungültiger Score
- Duplikate
- atomarer Rollback
- Prüfsumme

## 17.2 Datenbanktests

- CRUD aller Entitäten
- Indizes
- Transaktionen
- Migrationen
- Konfliktverhalten
- Ablaufbereinigung
- Snapshotaufbau

## 17.3 UI-Tests

- Onboarding
- Rollenstatus
- Regel anlegen
- Regel ändern
- Regel löschen
- Nummer prüfen
- Importdialog
- Datenschutzansicht
- Datenlöschung
- Dark Mode
- relevante Accessibility-Semantics

## 17.4 Leistungstests

Erzeuge reproduzierbare Tests für:

- 10.000 Regeln
- 50.000 Reputationsdatensätze
- kalter Snapshot
- warmer Snapshot
- Präfixsuche
- exakte Suche
- Snapshottausch während paralleler Bewertungen

## 17.5 Manuelle Testmatrix

Dokumentiere Tests auf:

- Android 10
- Android 11
- Android 12
- Android 13
- Android 14
- Android 15
- Android 16

Falls nicht alle Versionen real getestet werden können, kennzeichne klar:

- tatsächlich getestet,
- nur Emulator,
- nur kompiliert,
- theoretisch unterstützt.

---

# 18. Build- und Qualitätstore

Führe mindestens aus:

```bash
./gradlew --version
./gradlew clean
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Wenn ein Emulator oder Gerät verfügbar ist:

```bash
./gradlew connectedDebugAndroidTest
```

Für Release-Vorbereitung:

```bash
./gradlew bundleRelease
```

Zusätzliche Regeln:

- Warnungen prüfen, nicht blind ignorieren.
- Keine pauschale Deaktivierung von Lint-Regeln.
- Kein `abortOnError false`, um Probleme zu verstecken.
- ProGuard/R8-Regeln nur bei tatsächlichem Bedarf.
- Release-Build darf keine Debug-Schalter enthalten.
- APK mit `apkanalyzer` oder vergleichbarem offiziellen Werkzeug prüfen.
- SHA-256 der erzeugten Artefakte ausgeben.

PowerShell-kompatible Befehle und Skripte bereitstellen.

---

# 19. Release-Artefakte

Am Ende müssen mindestens vorliegen:

1. vollständiger Quellcode,
2. Gradle Wrapper,
3. erfolgreich gebaute Debug-APK,
4. Release-AAB oder klar dokumentierter Grund, falls nur Produktionssignatur fehlt,
5. Testberichte,
6. Lint-Bericht,
7. SHA-256-Prüfsummen,
8. `README.md`,
9. `PRIVACY_POLICY.md`,
10. `SECURITY.md`,
11. Benutzerhandbuch,
12. Architektur- und Datenbankdokumentation,
13. Beispiel-Importdatei,
14. Changelog.

Die Debug-APK muss eindeutig als Entwicklungsbuild gekennzeichnet sein.

Erzeuge niemals heimlich eine vermeintliche Produktionssignatur. Falls eine Entwicklungs-Keystore erzeugt wird:

- klar als nicht produktiv kennzeichnen,
- keine geheimen Zugangsdaten in Git einchecken,
- Vorgehen für eine echte Release-Signatur dokumentieren.

---

# 20. README-Anforderungen

`README.md` muss enthalten:

- Projektbeschreibung,
- Funktionsumfang,
- Datenschutzprinzip,
- Voraussetzungen,
- Build unter Windows und Linux,
- Installation per ADB,
- Aktivierung als Call-Screening-App,
- bekannte Herstellerbesonderheiten,
- Importformat,
- Testbefehle,
- Release-Erstellung,
- Projektstruktur,
- Screenshots oder Platzhalter nur dann, wenn echte Screenshots später erzeugt werden; keine erfundenen UI-Abbildungen.

---

# 21. Play-Store-Vorbereitung

Erzeuge `docs/PLAY_STORE_CHECKLIST.md` mit:

- aktuellem Target-API-Status,
- Berechtigungsbegründungen,
- Data-Safety-Angaben,
- Erklärung der Call-Screening-Kernfunktion,
- Datenschutz-URL-Platz für spätere Veröffentlichung,
- App-Kategorie,
- Inhaltsbewertung,
- Testzugang: nicht erforderlich, da kein Konto,
- Hinweise zu sensiblen APIs,
- Prüfung, dass keine Call-Log-Berechtigung unnötig verwendet wird,
- Prüfung aller eingebundenen SDKs,
- AAB-Erstellung,
- Signierung,
- App-Icon,
- Feature Graphic,
- Screenshots,
- Storebeschreibung.

Keine Play-Store-Konformität behaupten, ohne die zum Veröffentlichungszeitpunkt aktuellen Regeln erneut zu prüfen.

---

# 22. Arbeitsphasen

## Phase 0 — Verifikation

1. Prüfe aktuelle offizielle Android-Dokumentation.
2. Bestimme stabile kompatible Versionen.
3. Lege Buildumgebung fest.
4. Erzeuge Architekturentscheidung.
5. Erzeuge Risikoliste.
6. Starte erst danach die Implementierung.

Abnahme:

- dokumentierte Toolchain,
- bestätigte API-Level,
- bestätigtes Rollenmodell,
- keine Beta-Abhängigkeiten.

## Phase 1 — Baubares Grundprojekt

1. Gradle-Projekt.
2. App startet.
3. Compose-Theme.
4. Navigation.
5. Rollenstatus.
6. Service im Manifest.
7. erster Build.

Abnahme:

- `assembleDebug` erfolgreich,
- App installierbar,
- keine leeren Hauptseiten.

## Phase 2 — Domain-Engine

1. Normalisierung.
2. Regeln.
3. Scoring.
4. Entscheidungen.
5. Erklärungen.
6. Unit Tests.

Abnahme:

- vollständige Tests,
- deterministische Ergebnisse,
- keine Android-Abhängigkeit im Domainkern, soweit sinnvoll.

## Phase 3 — Persistenz

1. Room.
2. DataStore.
3. Migrationen.
4. Repositories.
5. Snapshot.
6. Import/Export.

Abnahme:

- Datenbanktests,
- atomarer Import,
- Snapshotkonsistenz.

## Phase 4 — Echter CallScreeningService

1. Rollenaktivierung.
2. Android-Adapter.
3. zeitkritische Auswertung.
4. Fail-open.
5. Protokollierung.
6. Verifikationsstatus.

Abnahme:

- genau eine Antwort je Anruf,
- keine Netzwerkoperation,
- Fehler führen zu Allow,
- Zeitmessung vorhanden.

## Phase 5 — Vollständige UI

1. Onboarding.
2. Dashboard.
3. Nummernprüfung.
4. Regeln.
5. Verlauf.
6. Einstellungen.
7. Datenschutz.

Abnahme:

- jede sichtbare Aktion funktioniert,
- Zustände bleiben nach Neustart erhalten,
- verständliche Fehlermeldungen.

## Phase 6 — Online- und Feed-Funktionen

1. manueller Browser-Suchintent.
2. lokaler JSON-Import.
3. optionaler HTTPS-Feed.
4. WorkManager.
5. Updatehistorie.

Abnahme:

- kein Google-Scraping,
- keine Online-Abhängigkeit im Anrufpfad,
- letzter gültiger Stand bleibt bei Fehlern erhalten.

## Phase 7 — Härtung

1. Berechtigungsprüfung.
2. Exported-Komponenten.
3. Network Security Config.
4. Logging.
5. Importlimits.
6. Threat Model.
7. R8/Releaseprüfung.

Abnahme:

- Security Review ohne kritische offene Punkte,
- Datenschutztext stimmt mit tatsächlichem Verhalten überein.

## Phase 8 — QA und Artefakte

1. vollständige Testläufe.
2. Lint.
3. APK.
4. AAB.
5. Prüfsummen.
6. Dokumentation.
7. finale Review.

Abnahme:

- Definition of Done vollständig erfüllt.

---

# 23. Definition of Done

Das Projekt ist erst fertig, wenn alle folgenden Aussagen wahr sind:

- Die App baut ohne manuelle Quellcodekorrektur.
- Die APK lässt sich installieren.
- Die App kann die Call-Screening-Rolle anfordern.
- Der Service ist korrekt registriert.
- Die lokale Entscheidungsengine ist vollständig implementiert.
- Ein Anruf kann zugelassen, stummgeschaltet oder blockiert werden.
- Der zeitkritische Pfad enthält keinen Netzwerkzugriff.
- Fehler führen standardmäßig zu Allow.
- Regeln können vollständig verwaltet werden.
- Reputationsdaten können importiert werden.
- Der Verlauf zeigt echte, von der App erzeugte Entscheidungen.
- Benutzerkorrekturen verändern künftige Entscheidungen.
- Einstellungen bleiben nach Neustart erhalten.
- Alle sichtbaren Schaltflächen funktionieren.
- Unit Tests laufen erfolgreich.
- Lint enthält keine ungeklärten Fehler.
- Datenschutztext und tatsächliches Verhalten stimmen überein.
- Keine verbotenen Platzhalter sind vorhanden.
- Erzeugte Artefakte und Prüfsummen sind dokumentiert.

---

# 24. Verbindliches Abschlussformat des Agentensystems

Nach Abschluss gib einen strukturierten Bericht aus:

```text
PROJEKTSTATUS
- Gesamtstatus:
- App-Version:
- Paketname:
- minSdk:
- targetSdk:
- compileSdk:
- Kotlin:
- AGP:
- Gradle:
- JDK:

BUILD
- clean:
- lintDebug:
- testDebugUnitTest:
- connectedDebugAndroidTest:
- assembleDebug:
- bundleRelease:

ARTEFAKTE
- Debug-APK:
- Release-AAB:
- Lint-Bericht:
- Testbericht:
- SHA-256:

IMPLEMENTIERTE FUNKTIONEN
- ...

BEWUSSTE EINSCHRÄNKUNGEN
- ...

NICHT GETESTETE GERÄTEPFade
- ...

SICHERHEITSPRÜFUNG
- ...

DATENSCHUTZPRÜFUNG
- ...

INSTALLATION
- ...
```

Keine Marketingformulierungen. Nur nachprüfbare Tatsachen.

---

# 25. Zusätzliche Architekturentscheidungen

## 25.1 Warum keine Live-Websuche beim Klingeln?

Der Android-Screening-Service besitzt ein hartes Antwortfenster. Eine Websuche kann durch Mobilfunk, DNS, Captcha, Serverlatenz oder fehlendes Netz unvorhersehbar langsam sein. Daher ist sie kein Bestandteil der Echtzeitentscheidung.

## 25.2 Warum lokale Daten?

Lokale Daten ermöglichen:

- geringe Latenz,
- Offline-Betrieb,
- reproduzierbare Entscheidungen,
- geringere Datenweitergabe,
- unabhängige Funktion,
- klaren Fail-open-Pfad.

## 25.3 Warum regelbasiertes Scoring?

Ein regelbasiertes Scoring ist:

- erklärbar,
- testbar,
- korrigierbar,
- deterministisch,
- für diesen Umfang effizienter als ein großes KI-Modell.

## 25.4 Warum kein allgemeiner Call-Log-Zugriff?

Die App kann ihren eigenen Entscheidungsverlauf führen. Ein allgemeiner Zugriff auf das System-Anrufprotokoll erhöht Datenschutz- und Store-Risiken und ist für die Kernfunktion nicht erforderlich.

---

# 26. Offizielle Referenzen

Prüfe diese Quellen zu Beginn erneut und verwende die jeweils aktuelle Fassung:

- Android Call Screening:  
  `https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls`

- `CallScreeningService` API:  
  `https://developer.android.com/reference/android/telecom/CallScreeningService`

- `RoleManager` und `ROLE_CALL_SCREENING`:  
  `https://developer.android.com/reference/android/app/role/RoleManager`

- Schutz vor Caller-ID-Spoofing und Verifikationsstatus:  
  `https://developer.android.com/develop/connectivity/telecom/dialer-app/prevent-spoofing`

- Room:  
  `https://developer.android.com/training/data-storage/room`

- WorkManager:  
  `https://developer.android.com/develop/background-work/background-tasks/persistent`

- Berechtigungen minimieren:  
  `https://developer.android.com/privacy-and-security/minimize-permission-requests`

- Network Security Configuration:  
  `https://developer.android.com/privacy-and-security/security-config`

- Google-Play-Anforderungen an Target API:  
  `https://developer.android.com/google/play/requirements/target-sdk`

---

# 27. Startbefehl an das Agentensystem

Beginne jetzt mit Phase 0.

Arbeite autonom und führe die Implementierung vollständig aus. Stelle keine unnötigen Rückfragen. Triff bei nicht sicherheitskritischen Detailfragen eine vernünftige, dokumentierte Entscheidung. Unterbrich die Arbeit nicht nach einer Planung oder einem ersten Prototyp.

Dein erstes technisches Ziel ist ein reproduzierbar baubares Android-Grundprojekt. Danach implementierst du die Domain-Engine, Persistenz und den echten `CallScreeningService`. Führe nach jeder Phase die relevanten Builds und Tests aus.

Beende die Arbeit erst, wenn entweder:

1. die Definition of Done erfüllt ist, oder
2. ein objektiver externer Blocker vorliegt, den du exakt mit Befehl, Fehlermeldung und bereits versuchten Lösungen dokumentierst.

Ein fehlender Produktions-Signaturschlüssel ist kein Grund, die Debug-APK, Tests, Dokumentation und Release-Vorbereitung nicht vollständig zu erstellen.
