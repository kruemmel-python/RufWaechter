package de.kruemmel.rufwaechter.screening

import de.kruemmel.rufwaechter.domain.CarrierVerification
import de.kruemmel.rufwaechter.domain.DecisionReason
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ScreeningDecision
import de.kruemmel.rufwaechter.domain.ScreeningSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeScreeningEvaluatorTest {
    private val identity = PhoneIdentity.UnknownNumber
    private val snapshot = ScreeningSnapshot.empty()

    @Test fun `Exception fuehrt zu Fail Open`() {
        val decision = SafeScreeningEvaluator().evaluateIncoming(
            identity,
            CarrierVerification.NOT_VERIFIED,
            snapshot,
        ) { _, _, _ -> error("defekt") }
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertTrue(DecisionReason.FAIL_OPEN in decision.reasons)
    }

    @Test fun `langsame Auswertung fuehrt zu Fail Open`() {
        val decision = SafeScreeningEvaluator(softTimeoutMs = 1).evaluateIncoming(
            identity,
            CarrierVerification.NOT_VERIFIED,
            snapshot,
        ) { _, _, _ ->
            Thread.sleep(5)
            allowed()
        }
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertTrue(DecisionReason.FAIL_OPEN in decision.reasons)
    }

    @Test fun `normale Entscheidung wird genau einmal ausgewertet`() {
        var calls = 0
        val decision = SafeScreeningEvaluator().evaluateIncoming(
            identity,
            CarrierVerification.NOT_VERIFIED,
            snapshot,
        ) { _, _, _ ->
            calls++
            allowed()
        }
        assertEquals(1, calls)
        assertEquals(ScreeningAction.ALLOW, decision.action)
    }

    private fun allowed() = ScreeningDecision(
        ScreeningAction.ALLOW,
        0,
        50,
        listOf(DecisionReason.DEFAULT_ACTION),
        null,
        0,
    )
}
