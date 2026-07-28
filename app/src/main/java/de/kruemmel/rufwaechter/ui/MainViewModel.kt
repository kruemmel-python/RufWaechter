package de.kruemmel.rufwaechter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.kruemmel.rufwaechter.AppContainer
import de.kruemmel.rufwaechter.data.CallDecisionEntity
import de.kruemmel.rufwaechter.data.FeedMetadataEntity
import de.kruemmel.rufwaechter.domain.CarrierVerification
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.RuleSource
import de.kruemmel.rufwaechter.domain.RuleType
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ScreeningDecision
import de.kruemmel.rufwaechter.domain.ScreeningSettings
import de.kruemmel.rufwaechter.domain.ScreeningThresholds
import de.kruemmel.rufwaechter.importexport.ReputationParseOutcome
import de.kruemmel.rufwaechter.importexport.BlockedNumbersImportCodec
import de.kruemmel.rufwaechter.importexport.BlockedNumbersImportResult
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockAuthMode
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockCredentials
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockSyncResult
import de.kruemmel.rufwaechter.reputation.WorkScheduler
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NumberCheckResult(
    val identity: PhoneIdentity,
    val decision: ScreeningDecision,
)

data class MainUiState(
    val rules: List<NumberRule> = emptyList(),
    val decisions: List<CallDecisionEntity> = emptyList(),
    val feedMetadata: List<FeedMetadataEntity> = emptyList(),
    val reputationCount: Int = 0,
    val phoneBlockCommunityCount: Int = 0,
    val phoneBlockPendingCount: Int = 0,
    val phoneBlockCredentialsConfigured: Boolean = false,
    val settings: ScreeningSettings = ScreeningSettings(),
    val numberCheck: NumberCheckResult? = null,
    val message: String? = null,
)

class MainViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val numberCheck = MutableStateFlow<NumberCheckResult?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val phoneBlockCredentialsConfigured =
        MutableStateFlow(container.phoneBlockCredentialStore.isConfigured())

    val state: StateFlow<MainUiState> = combine(
        container.repository.rules,
        container.repository.decisions,
        container.repository.feedMetadata,
        container.repository.reputationCount,
        container.settingsRepository.settings,
        container.repository.phoneBlockCommunityCount,
        container.repository.phoneBlockPendingCount,
        phoneBlockCredentialsConfigured,
        numberCheck,
        message,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MainUiState(
            rules = values[0] as List<NumberRule>,
            decisions = values[1] as List<CallDecisionEntity>,
            feedMetadata = values[2] as List<FeedMetadataEntity>,
            reputationCount = values[3] as Int,
            settings = values[4] as ScreeningSettings,
            phoneBlockCommunityCount = values[5] as Int,
            phoneBlockPendingCount = values[6] as Int,
            phoneBlockCredentialsConfigured = values[7] as Boolean,
            numberCheck = values[8] as NumberCheckResult?,
            message = values[9] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun checkNumber(raw: String) {
        val identity = container.phoneNumberParser.parse(raw)
        val decision = container.screeningEngine.evaluate(
            identity,
            CarrierVerification.NOT_VERIFIED,
            container.snapshotStore.current(),
        )
        numberCheck.value = NumberCheckResult(identity, decision)
    }

    fun saveRule(
        existing: NumberRule? = null,
        rawValue: String,
        type: RuleType,
        action: ScreeningAction,
        note: String,
        expiresAt: Long? = null,
    ) {
        viewModelScope.launch {
            val normalized = when (type) {
                RuleType.PRIVATE_NUMBER, RuleType.UNKNOWN_NUMBER -> null
                RuleType.COUNTRY -> normalizePrefix(rawValue)
                RuleType.PREFIX_ALLOW, RuleType.PREFIX_BLOCK -> normalizePrefix(rawValue)
                else -> (container.phoneNumberParser.parse(rawValue) as? PhoneIdentity.Number)?.normalized?.value
            }
            if (type !in setOf(RuleType.PRIVATE_NUMBER, RuleType.UNKNOWN_NUMBER) && normalized == null) {
                message.value = "Die Rufnummer oder das Präfix ist ungültig."
                return@launch
            }
            val now = System.currentTimeMillis()
            container.repository.saveRule(
                NumberRule(
                    id = existing?.id ?: 0,
                    type = type,
                    normalizedValue = normalized,
                    action = action,
                    enabled = existing?.enabled ?: true,
                    createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                    expiresAtEpochMs = expiresAt,
                    note = note.take(500),
                    source = existing?.source ?: RuleSource.USER,
                ),
            )
            if (type == RuleType.EXACT_BLOCK && action == ScreeningAction.BLOCK) {
                queuePhoneBlockReportIfEnabled(normalized, note)
            }
            message.value = "Regel gespeichert."
        }
    }

    fun setNumberRule(raw: String, action: ScreeningAction, temporaryHours: Int? = null) {
        val type = when {
            temporaryHours != null -> RuleType.TEMPORARY_EXACT
            action == ScreeningAction.ALLOW -> RuleType.EXACT_ALLOW
            else -> RuleType.EXACT_BLOCK
        }
        val expires = temporaryHours?.let { System.currentTimeMillis() + it * 60L * 60 * 1000 }
        saveRule(rawValue = raw, type = type, action = action, note = "Über Nummernprüfung erstellt", expiresAt = expires)
    }

    fun toggleRule(rule: NumberRule) {
        viewModelScope.launch {
            container.repository.saveRule(rule.copy(enabled = !rule.enabled, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun deleteRule(rule: NumberRule) {
        viewModelScope.launch {
            container.repository.deleteRule(rule)
            message.value = "Regel gelöscht."
        }
    }

    fun deleteRulesForNumber(number: String) {
        viewModelScope.launch {
            val matches = container.repository.loadRules().filter { it.normalizedValue == number }
            matches.forEach { container.repository.deleteRule(it) }
            message.value = if (matches.isEmpty()) "Für diese Nummer existiert keine Regel." else "${matches.size} Regel(n) gelöscht."
        }
    }

    fun correctDecision(entry: CallDecisionEntity, spam: Boolean) {
        viewModelScope.launch {
            container.repository.correctDecision(entry, spam)
            if (spam) queuePhoneBlockReportIfEnabled(entry.normalizedNumber, "Vom Benutzer als Spam markiert")
            message.value = if (spam) "Als Spam markiert und blockiert." else "Als legitim markiert und freigegeben."
        }
    }

    fun deleteDecision(id: Long) {
        viewModelScope.launch { container.repository.deleteDecision(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.repository.clearHistory()
            message.value = "Lokaler Verlauf gelöscht."
        }
    }

    fun updateSettings(settings: ScreeningSettings) {
        viewModelScope.launch {
            val normalized = if (settings.phoneBlockEnabled) {
                settings
            } else {
                settings.copy(phoneBlockContribute = false)
            }
            container.settingsRepository.update(normalized)
            WorkScheduler(getApplication()).apply(normalized)
            message.value = "Einstellungen gespeichert."
        }
    }

    fun updateThresholds(warn: Int, silence: Int, block: Int, confidence: Int) {
        val thresholds = ScreeningThresholds(warn, silence, block)
        if (!thresholds.isValid || confidence !in 0..100) {
            message.value = "Schwellen müssen aufsteigend und zwischen 0 und 100 liegen."
            return
        }
        updateSettings(state.value.settings.copy(thresholds = thresholds, minimumBlockConfidence = confidence))
    }

    fun updateRetentionAndInterval(retentionDays: Int, intervalHours: Int) {
        if (retentionDays !in 1..365 || intervalHours !in 6..168) {
            message.value = "Aufbewahrung: 1–365 Tage; Aktualisierung: 6–168 Stunden."
            return
        }
        updateSettings(
            state.value.settings.copy(
                historyRetentionDays = retentionDays,
                updateIntervalHours = intervalHours,
            ),
        )
    }

    fun importReputation(input: InputStream) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { container.importExportManager.importReputation(input) }
            message.value = when (outcome) {
                is ReputationParseOutcome.Success ->
                    buildString {
                        append("${outcome.result.accepted.size} akzeptiert, ${outcome.result.rejected} verworfen. ")
                        outcome.result.warnings.firstOrNull()?.let { append("Hinweis: $it ") }
                        append("SHA-256: ${outcome.result.sha256.take(12)}…")
                    }
                is ReputationParseOutcome.Failure -> outcome.message
            }
            container.rebuildSnapshot()
        }
    }

    fun importBlockedNumbers(input: InputStream) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                BlockedNumbersImportCodec(container.phoneNumberParser).parse(input)
            }
            message.value = when (parsed) {
                is BlockedNumbersImportResult.Failure -> parsed.message
                is BlockedNumbersImportResult.Success -> {
                    val imported = container.repository.importBlockedNumbers(parsed.numbers)
                    container.rebuildSnapshot()
                    "$imported Sperrnummer(n) importiert; ${parsed.numbers.size - imported} bereits vorhanden, " +
                        "${parsed.rejectedLines} Zeile(n) verworfen."
                }
            }
        }
    }

    fun savePhoneBlockCredentials(mode: PhoneBlockAuthMode, username: String, secret: String) {
        viewModelScope.launch {
            val credentials = PhoneBlockCredentials(mode, username.trim(), secret)
            if (!credentials.isValid() || username.length > 256 || secret.length > 4096) {
                message.value = "Die PhoneBlock-Zugangsdaten sind unvollständig oder zu lang."
                return@launch
            }
            val saved = withContext(Dispatchers.IO) {
                runCatching { container.phoneBlockSynchronizer.saveCredentials(credentials) }.isSuccess
            }
            if (!saved) {
                message.value = "Die Zugangsdaten konnten nicht verschlüsselt gespeichert werden."
                return@launch
            }
            phoneBlockCredentialsConfigured.value = true
            val updated = state.value.settings.copy(phoneBlockEnabled = true)
            container.settingsRepository.update(updated)
            WorkScheduler(getApplication()).apply(updated)
            WorkScheduler(getApplication()).requestPhoneBlockNow(updated.wifiOnly)
            message.value = "PhoneBlock-Zugangsdaten verschlüsselt gespeichert."
        }
    }

    fun clearPhoneBlockCredentials() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.phoneBlockSynchronizer.clearCredentials() }
            phoneBlockCredentialsConfigured.value = false
            val updated = state.value.settings.copy(
                phoneBlockEnabled = false,
                phoneBlockContribute = false,
            )
            container.settingsRepository.update(updated)
            WorkScheduler(getApplication()).apply(updated)
            message.value = "PhoneBlock-Zugangsdaten gelöscht."
        }
    }

    fun syncPhoneBlockNow() {
        viewModelScope.launch {
            message.value = "PhoneBlock-Abgleich gestartet."
            val result = withContext(Dispatchers.IO) { container.phoneBlockSynchronizer.synchronize() }
            message.value = when (result) {
                PhoneBlockSyncResult.NotConfigured -> "PhoneBlock-Zugangsdaten fehlen."
                is PhoneBlockSyncResult.Failed -> result.message
                is PhoneBlockSyncResult.UpToDate ->
                    "PhoneBlock ist bereits aktuell (Version ${result.version ?: "unbekannt"}); " +
                        "${result.reportsSent} Meldung(en) übertragen."
                is PhoneBlockSyncResult.Updated ->
                    "PhoneBlock Version ${result.version}: ${result.communityChanges} Änderung(en), " +
                        "${result.removed} entfernt, ${result.personalEntries} persönliche Einträge, " +
                        "${result.reportsSent} Meldung(en) übertragen." +
                        result.note?.let { " $it" }.orEmpty()
            }
        }
    }

    fun queueExistingManualBlocks() {
        viewModelScope.launch {
            val settings = state.value.settings
            if (!settings.phoneBlockEnabled ||
                !settings.phoneBlockContribute ||
                !phoneBlockCredentialsConfigured.value
            ) {
                message.value = "Aktiviere zuerst die freiwillige PhoneBlock-Teilnahme und hinterlege Zugangsdaten."
                return@launch
            }
            val rules = container.repository.loadRules().filter {
                it.type == RuleType.EXACT_BLOCK &&
                    it.action == ScreeningAction.BLOCK &&
                    it.source in setOf(RuleSource.USER, RuleSource.LOCAL_CORRECTION, RuleSource.IMPORT)
            }
            rules.forEach { rule ->
                rule.normalizedValue?.let { number ->
                    container.repository.queuePhoneBlockReport(
                        number,
                        settings.phoneBlockDefaultRating,
                        rule.note.ifBlank { "Manuelle Sperre aus RufWächter" },
                    )
                }
            }
            WorkScheduler(getApplication()).requestPhoneBlockNow(settings.wifiOnly)
            message.value = "${rules.size} lokale Sperre(n) zur bewussten Übertragung vorgemerkt."
        }
    }

    fun exportData(output: OutputStream) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.importExportManager.exportData(
                    output,
                    container.repository.loadRules(),
                    container.repository.loadAllReputation(),
                    container.repository.loadAllDecisions(),
                    container.repository.loadAllFeedMetadata(),
                    container.repository.loadPhoneBlockEntries(),
                    container.settingsRepository.settings.first(),
                )
            }
            message.value = "Export gespeichert."
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.phoneBlockSynchronizer.clearCredentials() }
            container.repository.clearAll()
            container.settingsRepository.clear()
            phoneBlockCredentialsConfigured.value = false
            WorkScheduler(getApplication()).apply(ScreeningSettings())
            numberCheck.value = null
            message.value = "Alle lokalen App-Daten wurden gelöscht."
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun reportMessage(value: String) {
        message.value = value
    }

    private fun normalizePrefix(raw: String): String? {
        val compact = raw.trim().filter { it == '+' || it.isDigit() }
        if (compact.startsWith("+") && compact.drop(1).all(Char::isDigit) && compact.length in 2..16) {
            return compact
        }
        return (container.phoneNumberParser.parse(raw) as? PhoneIdentity.Number)?.normalized?.value
    }

    private suspend fun queuePhoneBlockReportIfEnabled(number: String?, note: String) {
        val settings = state.value.settings
        if (number == null ||
            !settings.phoneBlockEnabled ||
            !settings.phoneBlockContribute ||
            !phoneBlockCredentialsConfigured.value
        ) {
            return
        }
        container.repository.queuePhoneBlockReport(
            number = number,
            rating = settings.phoneBlockDefaultRating,
            comment = note.ifBlank { "Manuelle Sperre aus RufWächter" },
        )
        WorkScheduler(getApplication()).requestPhoneBlockNow(settings.wifiOnly)
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(application, container) as T
    }
}
