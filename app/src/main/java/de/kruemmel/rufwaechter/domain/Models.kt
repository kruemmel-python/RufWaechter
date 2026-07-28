package de.kruemmel.rufwaechter.domain

@JvmInline
value class NormalizedPhoneNumber private constructor(val value: String) {
    companion object {
        fun fromCanonical(value: String): NormalizedPhoneNumber? =
            value.takeIf { CANONICAL.matches(it) }?.let(::NormalizedPhoneNumber)

        private val CANONICAL = Regex("""^\+[1-9]\d{5,14}$""")
    }
}

sealed interface PhoneIdentity {
    data class Number(val normalized: NormalizedPhoneNumber) : PhoneIdentity
    data object PrivateNumber : PhoneIdentity
    data object UnknownNumber : PhoneIdentity
    data class UnsupportedHandle(val scheme: String?) : PhoneIdentity
}

enum class ScreeningAction { ALLOW, WARN, SILENCE, BLOCK }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class RuleType {
    EXACT_ALLOW,
    EXACT_BLOCK,
    TEMPORARY_EXACT,
    PREFIX_ALLOW,
    PREFIX_BLOCK,
    COUNTRY,
    PRIVATE_NUMBER,
    UNKNOWN_NUMBER,
}

enum class RuleSource {
    USER,
    IMPORT,
    LOCAL_CORRECTION,
    PHONEBLOCK_COMMUNITY,
    PHONEBLOCK_PERSONAL,
}

data class NumberRule(
    val id: Long,
    val type: RuleType,
    val normalizedValue: String?,
    val action: ScreeningAction,
    val enabled: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val note: String = "",
    val source: RuleSource = RuleSource.USER,
) {
    fun isActive(nowEpochMs: Long): Boolean =
        enabled && (expiresAtEpochMs == null || expiresAtEpochMs > nowEpochMs)
}

enum class ReputationCategory {
    UNKNOWN,
    ADVERTISING,
    CALL_CENTER,
    ROBOCALL,
    PING_CALL,
    FRAUD,
    SPOOFING_SUSPECTED,
    DEBT_COLLECTION,
    SURVEY,
    LEGITIMATE_BUSINESS,
    PERSONAL,
    OTHER,
}

data class ReputationSource(
    val name: String,
    val version: String,
)

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
    val provenance: List<ReputationSource>,
) {
    val safeSpamScore: Int get() = spamScore.coerceIn(0, 100)
    val safeConfidence: Int get() = confidence.coerceIn(0, 100)
}

enum class CarrierVerification { PASSED, FAILED, NOT_VERIFIED }

enum class DecisionReason {
    PERSONAL_ALLOW,
    PERSONAL_BLOCK,
    TEMPORARY_RULE,
    PREFIX_RULE,
    COUNTRY_RULE,
    PRIVATE_NUMBER_RULE,
    UNKNOWN_NUMBER_RULE,
    LOCAL_REPUTATION,
    MULTIPLE_SOURCES,
    CONSISTENT_REPORTS,
    FRAUD_CATEGORY,
    ROBOCALL_CATEGORY,
    LEGITIMATE_CATEGORY,
    CARRIER_PASSED,
    CARRIER_FAILED,
    STALE_REPUTATION,
    LOW_EVIDENCE_LIMIT,
    PHONEBLOCK_COMMUNITY,
    PHONEBLOCK_PERSONAL,
    DEFAULT_ACTION,
    PROTECTION_DISABLED,
    FAIL_OPEN,
}

data class ScreeningDecision(
    val action: ScreeningAction,
    val score: Int,
    val confidence: Int,
    val reasons: List<DecisionReason>,
    val matchedRuleId: Long?,
    val evaluationDurationMs: Long,
)

data class ScreeningThresholds(
    val warnAt: Int = 35,
    val silenceAt: Int = 60,
    val blockAt: Int = 80,
) {
    val isValid: Boolean
        get() = warnAt in 1..98 && silenceAt in (warnAt + 1)..99 && blockAt in (silenceAt + 1)..100
}

data class ScreeningSettings(
    val protectionEnabled: Boolean = true,
    val defaultAction: ScreeningAction = ScreeningAction.ALLOW,
    val privateNumberAction: ScreeningAction = ScreeningAction.ALLOW,
    val unknownNumberAction: ScreeningAction = ScreeningAction.ALLOW,
    val thresholds: ScreeningThresholds = ScreeningThresholds(),
    val minimumBlockConfidence: Int = 75,
    val historyRetentionDays: Int = 30,
    val onlineUpdatesEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val updateIntervalHours: Int = 24,
    val feedUrl: String = "",
    val phoneBlockEnabled: Boolean = false,
    val phoneBlockContribute: Boolean = false,
    val phoneBlockDefaultRating: String = "E_ADVERTISING",
    val notificationsEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    init {
        require(thresholds.isValid) { "Ungültige Schwellenwerte" }
        require(minimumBlockConfidence in 0..100) { "Ungültige Mindestkonfidenz" }
        require(historyRetentionDays in 1..365) { "Ungültige Aufbewahrungsdauer" }
        require(updateIntervalHours in 6..168) { "Ungültiges Aktualisierungsintervall" }
        require(
            phoneBlockDefaultRating in setOf(
                "B_MISSED",
                "C_PING",
                "D_POLL",
                "E_ADVERTISING",
                "F_GAMBLE",
                "G_FRAUD",
            ),
        ) { "Ungültige PhoneBlock-Bewertung" }
    }
}

sealed interface ScreeningFailure {
    data object MissingHandle : ScreeningFailure
    data class UnsupportedScheme(val scheme: String?) : ScreeningFailure
    data class InvalidNumber(val rawLength: Int) : ScreeningFailure
    data class SnapshotUnavailable(val causeType: String) : ScreeningFailure
    data class EvaluationTimeout(val elapsedMs: Long) : ScreeningFailure
}
