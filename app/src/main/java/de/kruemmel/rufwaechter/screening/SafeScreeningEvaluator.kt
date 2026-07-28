package de.kruemmel.rufwaechter.screening

import de.kruemmel.rufwaechter.domain.CarrierVerification
import de.kruemmel.rufwaechter.domain.DecisionReason
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ScreeningDecision
import de.kruemmel.rufwaechter.domain.ScreeningSnapshot
import kotlin.time.TimeSource

class SafeScreeningEvaluator(
    private val softTimeoutMs: Long = 750,
) {
    fun evaluateIncoming(
        identity: PhoneIdentity,
        verification: CarrierVerification,
        snapshot: ScreeningSnapshot,
        evaluator: (PhoneIdentity, CarrierVerification, ScreeningSnapshot) -> ScreeningDecision,
    ): ScreeningDecision {
        val started = TimeSource.Monotonic.markNow()
        val evaluated = try {
            evaluator(identity, verification, snapshot)
        } catch (_: Exception) {
            return failOpen(started.elapsedNow().inWholeMilliseconds)
        }
        val elapsed = started.elapsedNow().inWholeMilliseconds
        return if (elapsed > softTimeoutMs) failOpen(elapsed) else evaluated.copy(evaluationDurationMs = elapsed)
    }

    private fun failOpen(elapsedMs: Long) = ScreeningDecision(
        action = ScreeningAction.ALLOW,
        score = 0,
        confidence = 0,
        reasons = listOf(DecisionReason.FAIL_OPEN),
        matchedRuleId = null,
        evaluationDurationMs = elapsedMs,
    )
}
