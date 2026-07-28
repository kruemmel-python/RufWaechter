package de.kruemmel.rufwaechter.domain

import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class ScreeningEngine {
    fun evaluate(
        identity: PhoneIdentity,
        verification: CarrierVerification,
        snapshot: ScreeningSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ScreeningDecision {
        val started = TimeSource.Monotonic.markNow()
        if (!snapshot.settings.protectionEnabled) {
            return decision(ScreeningAction.ALLOW, 0, 100, listOf(DecisionReason.PROTECTION_DISABLED), null, started)
        }
        return when (identity) {
            is PhoneIdentity.Number -> evaluateNumber(identity.normalized, verification, snapshot, nowEpochMs, started)
            PhoneIdentity.PrivateNumber -> evaluateSpecial(
                RuleType.PRIVATE_NUMBER,
                snapshot.settings.privateNumberAction,
                DecisionReason.PRIVATE_NUMBER_RULE,
                snapshot,
                nowEpochMs,
                started,
            )
            PhoneIdentity.UnknownNumber -> evaluateSpecial(
                RuleType.UNKNOWN_NUMBER,
                snapshot.settings.unknownNumberAction,
                DecisionReason.UNKNOWN_NUMBER_RULE,
                snapshot,
                nowEpochMs,
                started,
            )
            is PhoneIdentity.UnsupportedHandle ->
                decision(ScreeningAction.ALLOW, 0, 0, listOf(DecisionReason.FAIL_OPEN), null, started)
        }
    }

    private fun evaluateNumber(
        number: NormalizedPhoneNumber,
        verification: CarrierVerification,
        snapshot: ScreeningSnapshot,
        now: Long,
        started: TimeMark,
    ): ScreeningDecision {
        val exact = snapshot.exactRules[number.value].orEmpty().filter { it.isActive(now) }
        val local = exact.filter { it.source != RuleSource.PHONEBLOCK_COMMUNITY && it.source != RuleSource.PHONEBLOCK_PERSONAL }
        local.filter { it.type == RuleType.EXACT_ALLOW }.bestOrNull()?.let {
            return exactDecision(it, started)
        }
        local.filter { it.type == RuleType.EXACT_BLOCK }.bestOrNull()?.let {
            return exactDecision(it, started)
        }
        local.filter { it.type == RuleType.TEMPORARY_EXACT }.bestOrNull()?.let {
            return decision(it.action, it.action.baseScore, 100, listOf(DecisionReason.TEMPORARY_RULE), it.id, started)
        }
        exact.filter { it.source == RuleSource.PHONEBLOCK_PERSONAL && it.type == RuleType.EXACT_ALLOW }
            .bestOrNull()?.let { return exactDecision(it, started) }
        exact.filter { it.source == RuleSource.PHONEBLOCK_PERSONAL && it.type == RuleType.EXACT_BLOCK }
            .bestOrNull()?.let { return exactDecision(it, started) }
        exact.filter { it.source == RuleSource.PHONEBLOCK_COMMUNITY && it.type == RuleType.EXACT_BLOCK }
            .bestOrNull()?.let { return exactDecision(it, started) }
        snapshot.prefixRules.bestMatch(number.value, now)?.let {
            return decision(it.action, it.action.baseScore, 100, listOf(DecisionReason.PREFIX_RULE), it.id, started)
        }
        findCountryRule(number.value, snapshot, now)?.let {
            return decision(it.action, it.action.baseScore, 90, listOf(DecisionReason.COUNTRY_RULE), it.id, started)
        }

        val reasons = mutableListOf<DecisionReason>()
        var score = 0
        var confidence = 0
        var weakEvidenceOnly = false
        snapshot.reputation[number.value]?.takeUnless { it.expiresAtEpochMs?.let { expiry -> expiry <= now } == true }?.let { rep ->
            score = (rep.safeSpamScore * 0.65).roundToInt()
            confidence = rep.safeConfidence
            reasons += DecisionReason.LOCAL_REPUTATION
            if (rep.sourceCount >= 3) {
                score += 15
                reasons += DecisionReason.MULTIPLE_SOURCES
            }
            if (rep.reportCount >= 10 && rep.reportCount > rep.positiveCount * 3) {
                score += 10
                reasons += DecisionReason.CONSISTENT_REPORTS
            }
            when (rep.category) {
                ReputationCategory.FRAUD -> {
                    score += 20
                    reasons += DecisionReason.FRAUD_CATEGORY
                }
                ReputationCategory.ROBOCALL, ReputationCategory.PING_CALL -> {
                    score += 15
                    reasons += DecisionReason.ROBOCALL_CATEGORY
                }
                ReputationCategory.LEGITIMATE_BUSINESS, ReputationCategory.PERSONAL -> {
                    score -= 70
                    reasons += DecisionReason.LEGITIMATE_CATEGORY
                }
                else -> Unit
            }
            if (now - rep.lastUpdatedEpochMs > STALE_AFTER_MS) {
                confidence = (confidence * 0.6).roundToInt()
                reasons += DecisionReason.STALE_REPUTATION
            }
            weakEvidenceOnly = rep.sourceCount <= 1 && rep.reportCount <= 1
        }
        when (verification) {
            CarrierVerification.PASSED -> {
                score -= 20
                confidence = maxOf(confidence, 60)
                reasons += DecisionReason.CARRIER_PASSED
            }
            CarrierVerification.FAILED -> {
                score += 15
                confidence = maxOf(confidence, 35)
                reasons += DecisionReason.CARRIER_FAILED
            }
            CarrierVerification.NOT_VERIFIED -> Unit
        }
        score = score.coerceIn(0, 100)
        confidence = confidence.coerceIn(0, 100)
        var action = actionFor(score, confidence, snapshot.settings)
        if (weakEvidenceOnly && action > ScreeningAction.WARN) {
            action = ScreeningAction.WARN
            reasons += DecisionReason.LOW_EVIDENCE_LIMIT
        }
        if (reasons.isEmpty()) {
            action = snapshot.settings.defaultAction
            reasons += DecisionReason.DEFAULT_ACTION
        }
        return decision(action, score, confidence, reasons, null, started)
    }

    private fun evaluateSpecial(
        type: RuleType,
        fallback: ScreeningAction,
        reason: DecisionReason,
        snapshot: ScreeningSnapshot,
        now: Long,
        started: TimeMark,
    ): ScreeningDecision {
        val rule = snapshot.specialRules[type].orEmpty().filter { it.isActive(now) }.bestOrNull()
        val action = rule?.action ?: fallback
        return decision(action, action.baseScore, if (rule == null) 70 else 100, listOf(reason), rule?.id, started)
    }

    private fun findCountryRule(number: String, snapshot: ScreeningSnapshot, now: Long): NumberRule? =
        snapshot.countryRules.entries
            .filter { (prefix, _) -> number.startsWith(prefix) }
            .maxByOrNull { it.key.length }
            ?.value
            ?.filter { it.isActive(now) }
            ?.bestOrNull()

    private fun actionFor(score: Int, confidence: Int, settings: ScreeningSettings): ScreeningAction =
        when {
            score >= settings.thresholds.blockAt && confidence >= settings.minimumBlockConfidence -> ScreeningAction.BLOCK
            score >= settings.thresholds.silenceAt -> ScreeningAction.SILENCE
            score >= settings.thresholds.warnAt -> ScreeningAction.WARN
            else -> ScreeningAction.ALLOW
        }

    private fun decision(
        action: ScreeningAction,
        score: Int,
        confidence: Int,
        reasons: List<DecisionReason>,
        ruleId: Long?,
        started: TimeMark,
    ) = ScreeningDecision(
        action = action,
        score = score.coerceIn(0, 100),
        confidence = confidence.coerceIn(0, 100),
        reasons = reasons.distinct(),
        matchedRuleId = ruleId,
        evaluationDurationMs = started.elapsedNow().inWholeMilliseconds,
    )

    private fun exactDecision(rule: NumberRule, started: TimeMark): ScreeningDecision {
        val reason = when (rule.source) {
            RuleSource.PHONEBLOCK_COMMUNITY -> DecisionReason.PHONEBLOCK_COMMUNITY
            RuleSource.PHONEBLOCK_PERSONAL -> DecisionReason.PHONEBLOCK_PERSONAL
            else -> if (rule.action == ScreeningAction.ALLOW) {
                DecisionReason.PERSONAL_ALLOW
            } else {
                DecisionReason.PERSONAL_BLOCK
            }
        }
        return decision(rule.action, rule.action.baseScore, 100, listOf(reason), rule.id, started)
    }

    private fun List<NumberRule>.bestOrNull(): NumberRule? =
        takeIf { it.isNotEmpty() }?.bestByDeterministicPriority()

    private val ScreeningAction.baseScore: Int
        get() = when (this) {
            ScreeningAction.ALLOW -> 0
            ScreeningAction.WARN -> 45
            ScreeningAction.SILENCE -> 70
            ScreeningAction.BLOCK -> 100
        }

    companion object {
        private const val STALE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
