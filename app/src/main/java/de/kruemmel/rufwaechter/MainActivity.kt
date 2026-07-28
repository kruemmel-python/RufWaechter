package de.kruemmel.rufwaechter

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.kruemmel.rufwaechter.data.CallDecisionEntity
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.RuleType
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ThemeMode
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockAuthMode
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockRating
import de.kruemmel.rufwaechter.ui.MainUiState
import de.kruemmel.rufwaechter.ui.MainViewModel
import de.kruemmel.rufwaechter.ui.theme.RufWaechterTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as RufWaechterApplication
        MainViewModel.Factory(app, app.container)
    }
    private var roleAvailable by mutableStateOf(false)
    private var roleHeld by mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshRoleStatus()
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.updateSettings(viewModel.state.value.settings.copy(notificationsEnabled = granted))
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val input = contentResolver.openInputStream(uri)
            if (input == null) viewModel.reportMessage("Die gewählte Datei konnte nicht geöffnet werden.")
            else viewModel.importReputation(input)
        }
    private val blockedNumbersImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val input = contentResolver.openInputStream(uri)
            if (input == null) viewModel.reportMessage("Die gewählte Sperrliste konnte nicht geöffnet werden.")
            else viewModel.importBlockedNumbers(input)
        }
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            val output = contentResolver.openOutputStream(uri)
            if (output == null) viewModel.reportMessage("Das Exportziel konnte nicht geöffnet werden.")
            else viewModel.exportData(output)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshRoleStatus()
        setContent {
            val state by viewModel.state.collectAsState()
            RufWaechterTheme(state.settings.themeMode) {
                RufWaechterApp(
                    viewModel = viewModel,
                    roleAvailable = roleAvailable,
                    roleHeld = roleHeld,
                    onRequestRole = ::requestScreeningRole,
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/json")) },
                    onImportBlockedNumbers = {
                        blockedNumbersImportLauncher.launch(arrayOf("text/plain", "text/csv", "application/json"))
                    },
                    onOpenSystemBlockedNumbers = ::openSystemBlockedNumbers,
                    onExport = { exportLauncher.launch("rufwaechter-export.json") },
                    onWebSearch = ::openWebSearch,
                    onNotificationSetting = ::setNotifications,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRoleStatus()
    }

    private fun refreshRoleStatus() {
        val manager = getSystemService(RoleManager::class.java)
        roleAvailable = manager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        roleHeld = manager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    private fun requestScreeningRole() {
        val manager = getSystemService(RoleManager::class.java) ?: return
        if (manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun openWebSearch(number: String) {
        val query = URLEncoder.encode("\"$number\" Spam Betrug Werbung Anruf", StandardCharsets.UTF_8.name())
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri()))
        }.onFailure {
            viewModel.reportMessage("Es ist kein geeigneter Browser verfügbar.")
        }
    }

    private fun openSystemBlockedNumbers() {
        val manager = getSystemService(TelecomManager::class.java)
        runCatching {
            startActivity(manager.createManageBlockedNumbersIntent())
        }.onFailure {
            viewModel.reportMessage("Die Telefon-App stellt keine Systemansicht für gesperrte Nummern bereit.")
        }
    }

    private fun setNotifications(enabled: Boolean) {
        if (!enabled) {
            viewModel.updateSettings(viewModel.state.value.settings.copy(notificationsEnabled = false))
        } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.updateSettings(viewModel.state.value.settings.copy(notificationsEnabled = true))
        }
    }
}

private enum class AppScreen(val title: String, val short: String) {
    DASHBOARD("Übersicht", "Start"),
    CHECK("Nummer prüfen", "Prüfen"),
    RULES("Regeln", "Regeln"),
    HISTORY("Verlauf", "Verlauf"),
    SETTINGS("Einstellungen", "Setup"),
    PRIVACY("Datenschutz", "Daten"),
    HELP("Hilfe", "Hilfe"),
}

private val bottomScreens = AppScreen.entries.filterNot { it == AppScreen.HELP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RufWaechterApp(
    viewModel: MainViewModel,
    roleAvailable: Boolean,
    roleHeld: Boolean,
    onRequestRole: () -> Unit,
    onImport: () -> Unit,
    onImportBlockedNumbers: () -> Unit,
    onOpenSystemBlockedNumbers: () -> Unit,
    onExport: () -> Unit,
    onWebSearch: (String) -> Unit,
    onNotificationSetting: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }
    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen == AppScreen.HELP) {
                        TextButton(onClick = { screen = AppScreen.DASHBOARD }) { Text("Zurück") }
                    }
                },
                actions = {
                    if (screen != AppScreen.HELP) {
                        TextButton(onClick = { screen = AppScreen.HELP }) { Text("Hilfe") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                bottomScreens.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = { Text(item.short.take(1)) },
                        label = { Text(item.short) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.DASHBOARD -> DashboardScreen(
                    state,
                    viewModel,
                    roleAvailable,
                    roleHeld,
                    onRequestRole,
                    onNavigate = { screen = it },
                )
                AppScreen.CHECK -> NumberCheckScreen(state, viewModel, onWebSearch)
                AppScreen.RULES -> RulesScreen(state, viewModel)
                AppScreen.HISTORY -> HistoryScreen(state, viewModel)
                AppScreen.SETTINGS -> SettingsScreen(state, viewModel, onNotificationSetting)
                AppScreen.PRIVACY -> PrivacyScreen(
                    state,
                    viewModel,
                    onImport,
                    onImportBlockedNumbers,
                    onOpenSystemBlockedNumbers,
                    onExport,
                )
                AppScreen.HELP -> HelpScreen(
                    state = state,
                    roleAvailable = roleAvailable,
                    roleHeld = roleHeld,
                    onRequestRole = onRequestRole,
                    onNavigate = { screen = it },
                    onImportBlockedNumbers = onImportBlockedNumbers,
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    roleAvailable: Boolean,
    roleHeld: Boolean,
    onRequestRole: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
) {
    val startToday = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val today = state.decisions.filter { it.timestamp >= startToday }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(
                title = if (roleHeld && state.settings.protectionEnabled) "Schutz aktiv" else "Schutz nicht aktiv",
                detail = when {
                    !roleAvailable -> "Dieses Gerät bietet die Anruferkennungsrolle nicht an."
                    !roleHeld -> "RufWächter muss im System als App für Anruferkennung ausgewählt werden."
                    !state.settings.protectionEnabled -> "Die Rolle ist aktiv, der Schutz wurde aber in den Einstellungen ausgeschaltet."
                    else -> "Anrufe werden lokal und ohne Cloudkonto bewertet."
                },
            )
        }
        if (!roleHeld && roleAvailable) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Einrichtung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("1. Die Prüfung läuft lokal. 2. Android zeigt den Systemdialog. 3. Online-Daten bleiben standardmäßig aus. 4. Sehr unsichere Fälle werden zugelassen.")
                        EnumChooser(
                            "Standardverhalten",
                            state.settings.defaultAction,
                            ScreeningAction.entries,
                        ) { viewModel.updateSettings(state.settings.copy(defaultAction = it)) }
                        Button(onClick = onRequestRole) { Text("Schutz im System aktivieren") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Heute", today.size, Modifier.weight(1f))
                Metric("Blockiert", today.count { it.action == ScreeningAction.BLOCK.name }, Modifier.weight(1f))
                Metric("Stumm", today.count { it.action == ScreeningAction.SILENCE.name }, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Warnungen", today.count { it.action == ScreeningAction.WARN.name }, Modifier.weight(1f))
                Metric("Regeln", state.rules.size, Modifier.weight(1f))
                Metric("Reputation", state.reputationCount, Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Letztes Feed-Update: " + (state.feedMetadata.firstOrNull()?.downloadedAt?.let(::formatDate) ?: "nie"),
            )
            Text(
                "PhoneBlock: ${state.phoneBlockCommunityCount} Nummern, " +
                    "${state.phoneBlockPendingCount} ausstehende eigene Meldungen",
            )
        }
        item {
            Text("Schnellaktionen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate(AppScreen.CHECK) }, modifier = Modifier.weight(1f)) { Text("Nummer prüfen") }
                OutlinedButton(onClick = { onNavigate(AppScreen.RULES) }, modifier = Modifier.weight(1f)) { Text("Regel anlegen") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNavigate(AppScreen.HISTORY) }, modifier = Modifier.weight(1f)) { Text("Verlauf") }
                OutlinedButton(onClick = { onNavigate(AppScreen.PRIVACY) }, modifier = Modifier.weight(1f)) { Text("Feed importieren") }
            }
        }
        item {
            Button(
                onClick = { onNavigate(AppScreen.HELP) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bebilderte Hilfe öffnen") }
        }
    }
}

@Composable
private fun HelpScreen(
    state: MainUiState,
    roleAvailable: Boolean,
    roleHeld: Boolean,
    onRequestRole: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onImportBlockedNumbers: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "In wenigen Minuten startklar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Diese Hilfe zeigt, was zwingend nötig ist, welche Einstellungen empfohlen werden " +
                    "und welche Zusatzfunktionen freiwillig sind.",
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Einrichtungsstatus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HelpStatusRow("Android-Systemrolle", roleHeld, if (roleAvailable) "erforderlich" else "nicht verfügbar")
                    HelpStatusRow("Schutz in RufWächter", state.settings.protectionEnabled, "erforderlich")
                    HelpStatusRow(
                        "PhoneBlock-Zugang",
                        state.phoneBlockCredentialsConfigured && state.settings.phoneBlockEnabled,
                        "freiwillig",
                    )
                    Text(
                        if (roleHeld && state.settings.protectionEnabled) {
                            "Der lokale Anrufschutz ist einsatzbereit."
                        } else {
                            "Mindestens ein erforderlicher Einrichtungsschritt fehlt noch."
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item {
            HelpIllustratedCard(
                image = R.drawable.help_setup,
                imageDescription = "Smartphone mit Schutzschild und Häkchen",
                label = "MUSS",
                title = "1. Anrufschutz im System aktivieren",
                body = when {
                    !roleAvailable -> "Dieses Gerät bietet Androids Rolle für Anruferkennung nicht an. " +
                        "RufWächter kann dann keine Anrufe filtern."
                    roleHeld -> "RufWächter ist bereits als App für Anruferkennung ausgewählt. " +
                        "Zusätzlich muss „Schutz aktiv“ eingeschaltet bleiben."
                    else -> "Android muss RufWächter als App für Anruferkennung kennen. Tippe auf den Knopf " +
                        "und bestätige RufWächter im Systemdialog."
                },
                recommendations = listOf(
                    "RufWächter benötigt keine Kontakte und kein allgemeines Anrufprotokoll.",
                    "Ohne Systemrolle kann die App Nummern verwalten, aber keine Anrufe filtern.",
                ),
                actionLabel = if (!roleHeld && roleAvailable) "Systemrolle aktivieren" else "Schutz-Einstellungen öffnen",
                onAction = if (!roleHeld && roleAvailable) onRequestRole else ({ onNavigate(AppScreen.SETTINGS) }),
            )
        }
        item {
            HelpIllustratedCard(
                image = R.drawable.help_phoneblock,
                imageDescription = "Lokale Datenbank mit Aktualisierungspfeilen",
                label = "SOLLTE",
                title = "2. PhoneBlock-Daten aktuell halten",
                body = "Hinterlege unter Einstellungen einen PhoneBlock-API-Schlüssel, aktiviere " +
                    "„PhoneBlock täglich aktualisieren“ und starte einmal „Jetzt abgleichen“. " +
                    "Danach arbeitet RufWächter nur mit der lokal gespeicherten Kopie.",
                recommendations = listOf(
                    "API-Schlüssel statt der veralteten Basic-Anmeldung verwenden.",
                    "„Nur ungetaktetes Netz“ eingeschaltet lassen, wenn mobile Daten geschont werden sollen.",
                    "Eigene Sperren nur dann melden, wenn du bewusst an PhoneBlock teilnehmen möchtest.",
                ),
                actionLabel = "PhoneBlock einrichten",
                onAction = { onNavigate(AppScreen.SETTINGS) },
            )
        }
        item {
            HelpIllustratedCard(
                image = R.drawable.help_import,
                imageDescription = "Liste mit Pfeil zum Import",
                label = "KANN",
                title = "3. Frühere Android-Sperren übernehmen",
                body = "Android erlaubt RufWächter nicht, die geschützte System-Sperrliste automatisch zu lesen. " +
                    "Öffne unter Datenschutz zuerst die Systemliste, exportiere sie mit den Möglichkeiten deiner " +
                    "Telefon-App und wähle anschließend die TXT-, CSV- oder JSON-Datei aus.",
                recommendations = listOf(
                    "Eine Nummer pro Zeile wird unterstützt; CSV-Spalten dürfen „phone“ oder „number“ heißen.",
                    "Der Import erzeugt persönliche Blockierregeln, die Community-Daten überstimmen.",
                    "Die bisherigen Android-Sperren bleiben unabhängig davon im System aktiv.",
                ),
                actionLabel = "Exportdatei auswählen",
                onAction = onImportBlockedNumbers,
                secondaryActionLabel = "Systemliste und Datenschutz",
                onSecondaryAction = { onNavigate(AppScreen.PRIVACY) },
            )
        }
        item {
            HelpIllustratedCard(
                image = R.drawable.help_rules,
                imageDescription = "Regelliste mit Häkchen und Schutzschild",
                label = "SOLLTE",
                title = "4. Nummern prüfen und Entscheidungen korrigieren",
                body = "Unter „Prüfen“ kannst du eine Nummer bewerten, bevor sie anruft. „Zulassen / legitim“ " +
                    "erstellt eine persönliche Freigabe, „Blockieren / Spam“ eine persönliche Sperre. " +
                    "Eigene exakte Regeln haben immer Vorrang vor PhoneBlock.",
                recommendations = listOf(
                    "Wichtige Nummern ausdrücklich freigeben, wenn sie niemals gefiltert werden sollen.",
                    "Falsch erkannte Anrufe im Verlauf als legitim korrigieren.",
                    "Präfix- und Länderregeln nur verwenden, wenn du ihre große Reichweite geprüft hast.",
                ),
                actionLabel = "Nummer prüfen",
                onAction = { onNavigate(AppScreen.CHECK) },
                secondaryActionLabel = "Regeln verwalten",
                onSecondaryAction = { onNavigate(AppScreen.RULES) },
            )
        }
        item {
            HelpIllustratedCard(
                image = R.drawable.help_settings,
                imageDescription = "Drei Schieberegler",
                label = "SOLLTE",
                title = "5. Sichere Grundeinstellungen verwenden",
                body = "Die ausgelieferten Werte sind vorsichtig gewählt: Standardaktion, private und unbekannte " +
                    "Nummern auf „Zulassen“, Warnen ab 35, Stummschalten ab 60, Blockieren ab 80 und " +
                    "Mindestkonfidenz 75.",
                recommendations = listOf(
                    "Private oder unbekannte Nummern nicht pauschal blockieren; Ärzte und Behörden rufen oft so an.",
                    "Blockierschwelle oder Mindestkonfidenz erst nach Prüfung des Verlaufs absenken.",
                    "Benachrichtigungen aktivieren, wenn du blockierte Entscheidungen sofort sehen möchtest.",
                ),
                actionLabel = "Einstellungen prüfen",
                onAction = { onNavigate(AppScreen.SETTINGS) },
            )
        }
        item {
            HelpIllustratedCard(
                image = R.drawable.help_privacy,
                imageDescription = "Smartphone neben einem Vorhängeschloss",
                label = "KANN",
                title = "6. Daten kontrollieren und sichern",
                body = "Unter Datenschutz siehst du lokale Daten, Berechtigungen und aktive Netzwerkfunktionen. " +
                    "Dort kannst du Regeln und Einstellungen exportieren, Reputationslisten importieren oder alle " +
                    "App-Daten löschen. PhoneBlock-Zugangsdaten werden niemals exportiert.",
                recommendations = listOf(
                    "Vor einem Gerätewechsel einen App-Export erstellen.",
                    "Die Verlaufsaufbewahrung passend zum eigenen Datenschutzbedarf wählen.",
                    "Ein vollständiges Löschen entfernt auch lokale Listen und verschlüsselte Zugangsdaten.",
                ),
                actionLabel = "Datenschutz und Export",
                onAction = { onNavigate(AppScreen.PRIVACY) },
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Was bedeuten die vier Aktionen?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HelpDefinition("Zulassen", "Der Anruf klingelt normal.")
                    HelpDefinition("Warnen", "Der Anruf wird zugelassen und im Verlauf als verdächtig erklärt.")
                    HelpDefinition("Stummschalten", "Das Telefon klingelt nicht; der Anruf wird nicht aktiv abgewiesen.")
                    HelpDefinition("Blockieren", "Der Anruf wird abgewiesen und von RufWächter protokolliert.")
                }
            }
        }
        item {
            Text(
                "Grundregel: Erst den Verlauf beobachten, dann strenger einstellen. " +
                    "Persönliche Freigaben sind der sicherste Weg gegen Fehlalarme.",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HelpStatusRow(label: String, ready: Boolean, qualifier: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ready) "✓" else "○", color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(if (ready) "bereit" else qualifier, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HelpIllustratedCard(
    @DrawableRes image: Int,
    imageDescription: String,
    label: String,
    title: String,
    body: String,
    recommendations: List<String>,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(image),
                contentDescription = imageDescription,
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body)
            recommendations.forEach { Text("• $it") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, modifier = Modifier.weight(1f)) { Text(actionLabel) }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpDefinition(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.width(104.dp), fontWeight = FontWeight.Bold)
        Text(detail, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "$title. $detail" }) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(detail)
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NumberCheckScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onWebSearch: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var webConfirm by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(64) },
                label = { Text("Telefonnummer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Button(onClick = { viewModel.checkNumber(input) }, enabled = input.isNotBlank()) { Text("Lokal prüfen") } }
        state.numberCheck?.let { result ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(result.identity.display(), fontWeight = FontWeight.Bold)
                        Text("Aktion: ${result.decision.action.german()}")
                        Text("Score: ${result.decision.score}/100 · Konfidenz: ${result.decision.confidence}/100")
                        Text("Gründe: ${result.decision.reasons.joinToString { it.name }}")
                        Text("Auswertung: ${result.decision.evaluationDurationMs} ms")
                    }
                }
            }
            val number = (result.identity as? PhoneIdentity.Number)?.normalized?.value
            if (number != null) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.setNumberRule(number, ScreeningAction.ALLOW) }, modifier = Modifier.weight(1f)) { Text("Zulassen / legitim") }
                        Button(onClick = { viewModel.setNumberRule(number, ScreeningAction.BLOCK) }, modifier = Modifier.weight(1f)) { Text("Blockieren / Spam") }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.setNumberRule(number, ScreeningAction.SILENCE, 24) }, modifier = Modifier.weight(1f)) { Text("24 h stumm") }
                        OutlinedButton(onClick = { webConfirm = true }, modifier = Modifier.weight(1f)) { Text("Im Web suchen") }
                    }
                }
                item {
                    OutlinedButton(onClick = { viewModel.deleteRulesForNumber(number) }) { Text("Regeln für diese Nummer löschen") }
                }
            }
        }
    }
    if (webConfirm) {
        AlertDialog(
            onDismissRequest = { webConfirm = false },
            title = { Text("Nummer übertragen?") },
            text = { Text("Die Telefonnummer wird erst durch diese Aktion an die gewählte Suchmaschine übertragen.") },
            confirmButton = {
                TextButton(onClick = {
                    webConfirm = false
                    onWebSearch(input)
                }) { Text("Websuche öffnen") }
            },
            dismissButton = { TextButton(onClick = { webConfirm = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun RulesScreen(state: MainUiState, viewModel: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALLE") }
    var sort by rememberSaveable { mutableStateOf(RuleSort.UPDATED) }
    var editing by remember { mutableStateOf<NumberRule?>(null) }
    var create by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<NumberRule?>(null) }
    val filtered = state.rules
        .filter {
            (filter == "ALLE" || it.type.name == filter) &&
                (query.isBlank() || it.normalizedValue.orEmpty().contains(query, true) || it.note.contains(query, true))
        }
        .let { rules ->
            when (sort) {
                RuleSort.UPDATED -> rules.sortedByDescending(NumberRule::updatedAtEpochMs)
                RuleSort.NUMBER -> rules.sortedBy { it.normalizedValue.orEmpty() }
                RuleSort.PRIORITY -> rules.sortedBy { it.priorityLabel().toInt() }
            }
        }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Regeln durchsuchen") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { create = true }) { Text("Neu") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleFilterChooser(filter) { filter = it }
            EnumChooser("Sortierung", sort, RuleSort.entries) { sort = it }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Text("Keine passenden Regeln vorhanden.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rule.normalizedValue ?: rule.type.name, fontWeight = FontWeight.Bold)
                            Text("${rule.type.name} · ${rule.action.german()} · Priorität ${rule.priorityLabel()}")
                            if (rule.note.isNotBlank()) Text(rule.note)
                            rule.expiresAtEpochMs?.let { Text("Läuft ab: ${formatDate(it)}") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggleRule(rule) })
                                Text("Aktiv")
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { editing = rule }) { Text("Bearbeiten") }
                                TextButton(onClick = { deleteCandidate = rule }) { Text("Löschen") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (create || editing != null) {
        RuleDialog(
            existing = editing,
            onDismiss = {
                create = false
                editing = null
            },
            onSave = { raw, type, action, note, expiry ->
                viewModel.saveRule(editing, raw, type, action, note, expiry)
                create = false
                editing = null
            },
        )
    }
    deleteCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Regel löschen?") },
            text = { Text("Die Regel wird dauerhaft aus der lokalen Datenbank entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule)
                    deleteCandidate = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") } },
        )
    }
}

private enum class RuleSort { UPDATED, NUMBER, PRIORITY }

@Composable
private fun RuleFilterChooser(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Filter: $selected") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            (listOf("ALLE") + RuleType.entries.map { it.name }).forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleDialog(
    existing: NumberRule?,
    onDismiss: () -> Unit,
    onSave: (String, RuleType, ScreeningAction, String, Long?) -> Unit,
) {
    var raw by remember(existing) { mutableStateOf(existing?.normalizedValue.orEmpty()) }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }
    var type by remember(existing) { mutableStateOf(existing?.type ?: RuleType.EXACT_BLOCK) }
    var action by remember(existing) { mutableStateOf(existing?.action ?: ScreeningAction.BLOCK) }
    var temporary by remember(existing) { mutableStateOf(existing?.expiresAtEpochMs != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Regel anlegen" else "Regel bearbeiten") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { EnumChooser("Regeltyp", type, RuleType.entries) { type = it } }
                if (type != RuleType.PRIVATE_NUMBER && type != RuleType.UNKNOWN_NUMBER) {
                    item { OutlinedTextField(raw, { raw = it.take(64) }, label = { Text("Nummer oder Präfix") }) }
                }
                item { EnumChooser("Aktion", action, ScreeningAction.entries) { action = it } }
                item { OutlinedTextField(note, { note = it.take(500) }, label = { Text("Notiz") }) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(temporary, { temporary = it })
                        Text("Nach 24 Stunden ablaufen")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(raw, type, action, note, if (temporary) System.currentTimeMillis() + 24 * 60 * 60 * 1000L else null)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun <T> EnumChooser(label: String, selected: T, options: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.toString()) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: MainUiState, viewModel: MainViewModel) {
    var clearConfirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nur Entscheidungen von RufWächter", modifier = Modifier.weight(1f))
            TextButton(onClick = { clearConfirm = true }, enabled = state.decisions.isNotEmpty()) { Text("Alle löschen") }
        }
        if (state.decisions.isEmpty()) {
            Text("Noch keine Anrufe bewertet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.decisions, key = CallDecisionEntity::id) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.displayNumber, fontWeight = FontWeight.Bold)
                            Text("${formatDate(entry.timestamp)} · ${ScreeningAction.valueOf(entry.action).german()}")
                            Text("Score ${entry.score}, Konfidenz ${entry.confidence}, ${entry.evaluationDurationMs} ms")
                            Text(entry.reasonCodes.replace(",", ", "))
                            entry.userCorrection?.let { Text("Korrektur: $it") }
                            Row {
                                TextButton(
                                    onClick = { viewModel.correctDecision(entry, false) },
                                    enabled = entry.normalizedNumber != null,
                                ) { Text("Legitim") }
                                TextButton(
                                    onClick = { viewModel.correctDecision(entry, true) },
                                    enabled = entry.normalizedNumber != null,
                                ) { Text("Spam") }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { viewModel.deleteDecision(entry.id) }) { Text("Löschen") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Verlauf vollständig löschen?") },
            text = { Text("Alle lokal gespeicherten Screening-Entscheidungen werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    clearConfirm = false
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onNotificationSetting: (Boolean) -> Unit,
) {
    val settings = state.settings
    var warn by remember(settings) { mutableStateOf(settings.thresholds.warnAt.toString()) }
    var silence by remember(settings) { mutableStateOf(settings.thresholds.silenceAt.toString()) }
    var block by remember(settings) { mutableStateOf(settings.thresholds.blockAt.toString()) }
    var confidence by remember(settings) { mutableStateOf(settings.minimumBlockConfidence.toString()) }
    var retention by remember(settings.historyRetentionDays) { mutableStateOf(settings.historyRetentionDays.toString()) }
    var interval by remember(settings.updateIntervalHours) { mutableStateOf(settings.updateIntervalHours.toString()) }
    var feedUrl by remember(settings.feedUrl) { mutableStateOf(settings.feedUrl) }
    var phoneBlockAuthMode by remember { mutableStateOf(PhoneBlockAuthMode.API_KEY) }
    var phoneBlockUsername by remember { mutableStateOf("") }
    var phoneBlockSecret by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingSwitch("Schutz aktiv", settings.protectionEnabled) { viewModel.updateSettings(settings.copy(protectionEnabled = it)) } }
        item {
            Text(
                "Warnung: BLOCK für private oder unbekannte Nummern kann legitime Anrufe ohne Rückfragemöglichkeit abweisen.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        item { EnumChooser("Standardaktion", settings.defaultAction, ScreeningAction.entries) { viewModel.updateSettings(settings.copy(defaultAction = it)) } }
        item { EnumChooser("Private Nummern", settings.privateNumberAction, ScreeningAction.entries) { viewModel.updateSettings(settings.copy(privateNumberAction = it)) } }
        item { EnumChooser("Unbekannte Nummern", settings.unknownNumberAction, ScreeningAction.entries) { viewModel.updateSettings(settings.copy(unknownNumberAction = it)) } }
        item {
            Text("Schwellenwerte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("Warnen", warn, { warn = it }, Modifier.weight(1f))
                NumberField("Stumm", silence, { silence = it }, Modifier.weight(1f))
                NumberField("Block", block, { block = it }, Modifier.weight(1f))
                NumberField("Konf.", confidence, { confidence = it }, Modifier.weight(1f))
            }
            Button(onClick = {
                viewModel.updateThresholds(
                    warn.toIntOrNull() ?: -1,
                    silence.toIntOrNull() ?: -1,
                    block.toIntOrNull() ?: -1,
                    confidence.toIntOrNull() ?: -1,
                )
            }) { Text("Schwellen speichern") }
        }
        item { SettingSwitch("Benachrichtigungen", settings.notificationsEnabled, onNotificationSetting) }
        item { EnumChooser("Darstellung", settings.themeMode, ThemeMode.entries) { viewModel.updateSettings(settings.copy(themeMode = it)) } }
        item { SettingSwitch("Online-Feed aktivieren", settings.onlineUpdatesEnabled) { viewModel.updateSettings(settings.copy(onlineUpdatesEnabled = it)) } }
        item { SettingSwitch("Nur ungetaktetes Netz (WLAN)", settings.wifiOnly) { viewModel.updateSettings(settings.copy(wifiOnly = it)) } }
        item {
            OutlinedTextField(
                value = feedUrl,
                onValueChange = { feedUrl = it.take(2048) },
                label = { Text("HTTPS-Feed-Adresse") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.updateSettings(settings.copy(feedUrl = feedUrl.trim())) },
                enabled = feedUrl.isBlank() || feedUrl.startsWith("https://"),
            ) { Text("Feed-Adresse speichern") }
        }
        item {
            Text("Aufbewahrung und Feed-Intervall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Tage", retention, { retention = it }, Modifier.weight(1f))
                NumberField("Stunden", interval, { interval = it }, Modifier.weight(1f))
            }
            Button(onClick = {
                viewModel.updateRetentionAndInterval(
                    retention.toIntOrNull() ?: -1,
                    interval.toIntOrNull() ?: -1,
                )
            }) { Text("Zeiten speichern") }
            Text("Gefährliche Aktionen wie vollständiges Löschen befinden sich unter Datenschutz.")
        }
        item {
            HorizontalDivider()
            Text("PhoneBlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Quelle: https://phoneblock.net/phoneblock/api/blocklist")
            Text(
                "${state.phoneBlockCommunityCount} Community-Nummern lokal; " +
                    "${state.phoneBlockPendingCount} eigene Meldung(en) ausstehend.",
            )
            Text(
                if (state.phoneBlockCredentialsConfigured) {
                    "Zugangsdaten sind verschlüsselt im Android Keystore hinterlegt."
                } else {
                    "Keine PhoneBlock-Zugangsdaten hinterlegt."
                },
            )
        }
        item {
            EnumChooser(
                "Anmeldung",
                phoneBlockAuthMode,
                PhoneBlockAuthMode.entries,
            ) { phoneBlockAuthMode = it }
            if (phoneBlockAuthMode == PhoneBlockAuthMode.BASIC) {
                OutlinedTextField(
                    value = phoneBlockUsername,
                    onValueChange = { phoneBlockUsername = it.take(256) },
                    label = { Text("PhoneBlock-Benutzername") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Basic-Anmeldung wird von PhoneBlock als veraltet bezeichnet; ein API-Schlüssel wird bevorzugt.")
            }
            OutlinedTextField(
                value = phoneBlockSecret,
                onValueChange = { phoneBlockSecret = it.take(4096) },
                label = {
                    Text(if (phoneBlockAuthMode == PhoneBlockAuthMode.API_KEY) "API-Schlüssel" else "Passwort")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.savePhoneBlockCredentials(
                            phoneBlockAuthMode,
                            phoneBlockUsername,
                            phoneBlockSecret,
                        )
                        phoneBlockSecret = ""
                    },
                    enabled = phoneBlockSecret.isNotBlank() &&
                        (phoneBlockAuthMode == PhoneBlockAuthMode.API_KEY || phoneBlockUsername.isNotBlank()),
                ) { Text("Zugang speichern") }
                OutlinedButton(
                    onClick = viewModel::clearPhoneBlockCredentials,
                    enabled = state.phoneBlockCredentialsConfigured,
                ) { Text("Zugang löschen") }
            }
        }
        item {
            SettingSwitch("PhoneBlock täglich aktualisieren", settings.phoneBlockEnabled) { enabled ->
                if (enabled && !state.phoneBlockCredentialsConfigured) {
                    viewModel.reportMessage("Hinterlege zuerst PhoneBlock-Zugangsdaten.")
                } else {
                    viewModel.updateSettings(settings.copy(phoneBlockEnabled = enabled))
                }
            }
            Button(
                onClick = viewModel::syncPhoneBlockNow,
                enabled = settings.phoneBlockEnabled && state.phoneBlockCredentialsConfigured,
            ) { Text("Jetzt abgleichen") }
            Text("Vollabgleich höchstens monatlich; inkrementeller Abgleich gemäß PhoneBlock-Vorgabe höchstens täglich.")
        }
        item {
            SettingSwitch("Eigene Sperren an PhoneBlock melden", settings.phoneBlockContribute) { enabled ->
                if (enabled && (!state.phoneBlockCredentialsConfigured || !settings.phoneBlockEnabled)) {
                    viewModel.reportMessage(
                        "Für die freiwillige Teilnahme müssen PhoneBlock-Zugang und Aktualisierung aktiv sein.",
                    )
                } else {
                    viewModel.updateSettings(settings.copy(phoneBlockContribute = enabled))
                }
            }
            Text(
                "Nur bei aktiver Zustimmung werden neu manuell gesperrte Nummer, gewählte Kategorie und Notiz übertragen. " +
                    "Verlauf und eingehende Anrufe werden nicht hochgeladen.",
            )
            PhoneBlockRatingChooser(settings.phoneBlockDefaultRating) { rating ->
                viewModel.updateSettings(settings.copy(phoneBlockDefaultRating = rating.apiValue))
            }
            OutlinedButton(
                onClick = viewModel::queueExistingManualBlocks,
                enabled = settings.phoneBlockContribute && state.phoneBlockCredentialsConfigured,
            ) { Text("Bestehende lokale Sperren melden") }
        }
    }
}

@Composable
private fun PhoneBlockRatingChooser(selectedApiValue: String, onSelect: (PhoneBlockRating) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = PhoneBlockRating.entries.firstOrNull { it.apiValue == selectedApiValue }
        ?: PhoneBlockRating.ADVERTISING
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Meldekategorie: ${selected.germanLabel}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PhoneBlockRating.entries.forEach { rating ->
                DropdownMenuItem(
                    text = { Text(rating.germanLabel) },
                    onClick = {
                        onSelect(rating)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun PrivacyScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onImport: () -> Unit,
    onImportBlockedNumbers: () -> Unit,
    onOpenSystemBlockedNumbers: () -> Unit,
    onExport: () -> Unit,
) {
    var deleteConfirm by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Lokal gespeicherte Daten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Eigene Regeln, lokale Reputationsdaten, App-Einstellungen und die von RufWächter erzeugten Entscheidungen.")
        }
        item {
            Text("Berechtigungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Internet: für bewusst aktivierte HTTPS-Feeds, PhoneBlock und Websuche. Benachrichtigungen: nur nach " +
                    "Zustimmung. WorkManager ergänzt Netzwerkstatus, Wake-Lock, Neustart-Empfang und seine normale " +
                    "Foreground-Service-Berechtigung für verlässlich geplante Pflege. Kein Zugriff auf Kontakte, SMS, " +
                    "Standort, das allgemeine Anrufprotokoll oder die Android-System-Sperrliste.",
            )
        }
        item {
            Text("Netzwerk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (state.settings.onlineUpdatesEnabled) "Automatische HTTPS-Feeds sind aktiviert." else "Automatische Netzwerk-Feeds sind deaktiviert.")
            Text("Feed: ${state.settings.feedUrl.ifBlank { "nicht konfiguriert" }}")
            Text(
                if (state.settings.phoneBlockEnabled) {
                    "PhoneBlock ist aktiviert; Community-Daten werden höchstens täglich inkrementell abgerufen."
                } else {
                    "PhoneBlock ist deaktiviert."
                },
            )
        }
        item {
            Text("Bisherige System-Sperren", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Android erlaubt einer reinen Anruferkennungs-App keinen Lesezugriff auf die systemweite Sperrliste. " +
                    "Bestehende Sperren bleiben im Telefonsystem aktiv. Eine vom Telefonhersteller exportierte Text-, " +
                    "CSV- oder JSON-Liste kann hier nutzervermittelt übernommen werden.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenSystemBlockedNumbers, modifier = Modifier.weight(1f)) {
                    Text("Systemliste öffnen")
                }
                OutlinedButton(onClick = onImportBlockedNumbers, modifier = Modifier.weight(1f)) {
                    Text("Export importieren")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Feed importieren") }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Daten exportieren") }
            }
        }
        item {
            Text("Datenschutzgrundsatz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Es gibt keine Werbung, Telemetrie oder Tracking. Telefonnummern verlassen das Gerät nur nach einer " +
                    "bewussten Websuche, bei aktivierter Online-Prüfung oder als ausdrücklich aktivierte PhoneBlock-Meldung. " +
                    "Lokaler Verlauf und Regeln können jederzeit exportiert oder vollständig gelöscht werden. Die ausführliche " +
                    "Erklärung steht in PRIVACY_POLICY.md im Quellprojekt.",
            )
        }
        item {
            HorizontalDivider()
            Text("Gefahrenbereich", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Button(onClick = { deleteConfirm = true }) { Text("Alle lokalen Daten löschen") }
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Alle Daten unwiderruflich löschen?") },
            text = { Text("Regeln, Verlauf, Reputation, Feed-Metadaten und Einstellungen werden lokal gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    deleteConfirm = false
                }) { Text("Alles löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Abbrechen") } },
        )
    }
}

private fun PhoneIdentity.display(): String = when (this) {
    is PhoneIdentity.Number -> "Normalisiert: ${normalized.value}"
    PhoneIdentity.PrivateNumber -> "Private/unterdrückte Nummer"
    PhoneIdentity.UnknownNumber -> "Unbekannte oder ungültige Nummer"
    is PhoneIdentity.UnsupportedHandle -> "Nicht unterstütztes Schema: ${scheme ?: "unbekannt"}"
}

private fun ScreeningAction.german(): String = when (this) {
    ScreeningAction.ALLOW -> "Zulassen"
    ScreeningAction.WARN -> "Warnen"
    ScreeningAction.SILENCE -> "Stummschalten"
    ScreeningAction.BLOCK -> "Blockieren"
}

private fun NumberRule.priorityLabel(): String = when (type) {
    RuleType.EXACT_ALLOW -> "1"
    RuleType.EXACT_BLOCK -> "2"
    RuleType.TEMPORARY_EXACT -> "3"
    RuleType.PREFIX_ALLOW, RuleType.PREFIX_BLOCK -> "4"
    RuleType.COUNTRY -> "5"
    RuleType.PRIVATE_NUMBER, RuleType.UNKNOWN_NUMBER -> "6"
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
