package de.kruemmel.rufwaechter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningEngineTest {
    private val engine = ScreeningEngine()
    private val number = NormalizedPhoneNumber.fromCanonical("+493411234567")!!
    private val identity = PhoneIdentity.Number(number)
    private val now = 1_800_000_000_000

    @Test fun `Freigabe schlaegt Reputation und Blockregel`() {
        val rules = listOf(
            rule(1, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK),
            rule(2, RuleType.EXACT_ALLOW, ScreeningAction.ALLOW),
        )
        val decision = evaluate(rules, listOf(reputation(100, 100)))
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertEquals(2L, decision.matchedRuleId)
    }

    @Test fun `exakte Blockierung schlaegt Praefixfreigabe`() {
        val rules = listOf(
            rule(1, RuleType.PREFIX_ALLOW, ScreeningAction.ALLOW, "+49341"),
            rule(2, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK),
        )
        assertEquals(ScreeningAction.BLOCK, evaluate(rules).action)
    }

    @Test fun `laengstes Praefix gewinnt`() {
        val rules = listOf(
            rule(1, RuleType.PREFIX_BLOCK, ScreeningAction.BLOCK, "+49"),
            rule(2, RuleType.PREFIX_ALLOW, ScreeningAction.ALLOW, "+49341"),
        )
        assertEquals(ScreeningAction.ALLOW, evaluate(rules).action)
    }

    @Test fun `abgelaufene Regel wird ignoriert`() {
        val expired = rule(1, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK)
            .copy(expiresAtEpochMs = now - 1)
        assertEquals(ScreeningAction.ALLOW, evaluate(listOf(expired)).action)
    }

    @Test fun `deaktivierte Regel wird ignoriert`() {
        val disabled = rule(1, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK).copy(enabled = false)
        assertEquals(ScreeningAction.ALLOW, evaluate(listOf(disabled)).action)
    }

    @Test fun `gleichrangiger Konflikt ist deterministisch`() {
        val oldBlock = rule(2, RuleType.PREFIX_BLOCK, ScreeningAction.BLOCK, "+49341").copy(updatedAtEpochMs = now - 2)
        val newAllow = rule(1, RuleType.PREFIX_ALLOW, ScreeningAction.ALLOW, "+49341").copy(updatedAtEpochMs = now - 1)
        assertEquals(ScreeningAction.ALLOW, evaluate(listOf(oldBlock, newAllow)).action)
    }

    @Test fun `private Nummer folgt eigener Einstellung`() {
        val settings = ScreeningSettings(privateNumberAction = ScreeningAction.SILENCE)
        val snapshot = ScreeningSnapshot.compile(emptyList(), emptyList(), settings, 1)
        assertEquals(
            ScreeningAction.SILENCE,
            engine.evaluate(PhoneIdentity.PrivateNumber, CarrierVerification.NOT_VERIFIED, snapshot, now).action,
        )
    }

    @Test fun `Score und Konfidenz werden begrenzt`() {
        val decision = evaluate(reputation = listOf(reputation(140, 140)))
        assertTrue(decision.score in 0..100)
        assertTrue(decision.confidence in 0..100)
    }

    @Test fun `einzelne schwache Meldung blockiert nicht`() {
        val weak = reputation(100, 100).copy(reportCount = 1, sourceCount = 1)
        assertTrue(evaluate(reputation = listOf(weak)).action <= ScreeningAction.WARN)
    }

    @Test fun `Carrier Fehler allein blockiert nicht`() {
        val decision = evaluate(verification = CarrierVerification.FAILED)
        assertEquals(ScreeningAction.ALLOW, decision.action)
    }

    @Test fun `bestaetigter Betrug blockiert`() {
        val fraud = reputation(95, 95).copy(
            category = ReputationCategory.FRAUD,
            sourceCount = 4,
            reportCount = 40,
        )
        assertEquals(ScreeningAction.BLOCK, evaluate(reputation = listOf(fraud)).action)
    }

    @Test fun `veraltete Daten reduzieren Konfidenz`() {
        val stale = reputation(90, 90).copy(lastUpdatedEpochMs = now - 31L * 24 * 60 * 60 * 1000)
        assertTrue(evaluate(reputation = listOf(stale)).confidence < 90)
    }

    @Test fun `bestandene Carrier Verifikation senkt Score`() {
        val rep = reputation(80, 80).copy(sourceCount = 2, reportCount = 5)
        val neutral = evaluate(reputation = listOf(rep))
        val passed = evaluate(reputation = listOf(rep), verification = CarrierVerification.PASSED)
        assertTrue(passed.score < neutral.score)
    }

    @Test fun `Schutz aus bedeutet immer Freigabe`() {
        val settings = ScreeningSettings(protectionEnabled = false)
        val snapshot = ScreeningSnapshot.compile(
            listOf(rule(1, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK)),
            emptyList(),
            settings,
            1,
        )
        assertEquals(ScreeningAction.ALLOW, engine.evaluate(identity, CarrierVerification.FAILED, snapshot, now).action)
    }

    @Test fun `lokale Freigabe schlaegt PhoneBlock Community`() {
        val phoneBlock = rule(10, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK)
            .copy(source = RuleSource.PHONEBLOCK_COMMUNITY)
        val localAllow = rule(11, RuleType.EXACT_ALLOW, ScreeningAction.ALLOW)
        val decision = evaluate(listOf(phoneBlock, localAllow))
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertTrue(DecisionReason.PERSONAL_ALLOW in decision.reasons)
    }

    @Test fun `lokale Sperre schlaegt PhoneBlock Whitelist`() {
        val phoneBlockAllow = rule(10, RuleType.EXACT_ALLOW, ScreeningAction.ALLOW)
            .copy(source = RuleSource.PHONEBLOCK_PERSONAL)
        val localBlock = rule(11, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK)
        val decision = evaluate(listOf(phoneBlockAllow, localBlock))
        assertEquals(ScreeningAction.BLOCK, decision.action)
        assertTrue(DecisionReason.PERSONAL_BLOCK in decision.reasons)
    }

    @Test fun `PhoneBlock Whitelist schlaegt Community Sperre`() {
        val community = rule(10, RuleType.EXACT_BLOCK, ScreeningAction.BLOCK)
            .copy(source = RuleSource.PHONEBLOCK_COMMUNITY)
        val whitelist = rule(11, RuleType.EXACT_ALLOW, ScreeningAction.ALLOW)
            .copy(source = RuleSource.PHONEBLOCK_PERSONAL)
        val decision = evaluate(listOf(community, whitelist))
        assertEquals(ScreeningAction.ALLOW, decision.action)
        assertTrue(DecisionReason.PHONEBLOCK_PERSONAL in decision.reasons)
    }

    private fun evaluate(
        rules: List<NumberRule> = emptyList(),
        reputation: List<NumberReputation> = emptyList(),
        verification: CarrierVerification = CarrierVerification.NOT_VERIFIED,
    ): ScreeningDecision {
        val snapshot = ScreeningSnapshot.compile(rules, reputation, ScreeningSettings(), 1)
        return engine.evaluate(identity, verification, snapshot, now)
    }

    private fun rule(
        id: Long,
        type: RuleType,
        action: ScreeningAction,
        value: String = number.value,
    ) = NumberRule(id, type, value, action, true, now - 10, now - 10)

    private fun reputation(score: Int, confidence: Int) = NumberReputation(
        number,
        score,
        confidence,
        ReputationCategory.ADVERTISING,
        reportCount = 5,
        positiveCount = 0,
        sourceCount = 2,
        lastUpdatedEpochMs = now - 1_000,
        expiresAtEpochMs = null,
        provenance = listOf(ReputationSource("Test", "1")),
    )
}
