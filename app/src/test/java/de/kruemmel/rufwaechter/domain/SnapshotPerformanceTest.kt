package de.kruemmel.rufwaechter.domain

import de.kruemmel.rufwaechter.screening.ScreeningSnapshotStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPerformanceTest {
    @Test fun `grosse Datenbasis wird ohne linearen Vollscan ausgewertet`() {
        val now = System.currentTimeMillis()
        val rules = (0 until 10_000).map { index ->
            NumberRule(
                id = index.toLong() + 1,
                type = RuleType.EXACT_BLOCK,
                normalizedValue = "+4915${index.toString().padStart(10, '0')}",
                action = ScreeningAction.BLOCK,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
        }
        val target = rules.last().normalizedValue!!
        val snapshot = ScreeningSnapshot.compile(rules, emptyList(), ScreeningSettings(), 1)
        val start = System.nanoTime()
        val decision = ScreeningEngine().evaluate(
            PhoneIdentity.Number(NormalizedPhoneNumber.fromCanonical(target)!!),
            CarrierVerification.NOT_VERIFIED,
            snapshot,
            now,
        )
        val durationMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(ScreeningAction.BLOCK, decision.action)
        assertTrue("Auswertung dauerte $durationMs ms", durationMs < 250)
    }

    @Test fun `Snapshottausch bleibt unter parallelen Bewertungen konsistent`() {
        val store = ScreeningSnapshotStore()
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(100)
        repeat(100) { index ->
            pool.execute {
                if (index % 10 == 0) {
                    store.install(ScreeningSnapshot.empty().copy(version = index.toLong()))
                } else {
                    val current = store.current()
                    assertTrue(current.version >= 0)
                }
                latch.countDown()
            }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()
    }

    @Test fun `zehntausend Praefixe werden indexiert ausgewertet`() {
        val now = System.currentTimeMillis()
        val rules = (10_000..19_999).map { suffix ->
            NumberRule(
                id = suffix.toLong(),
                type = RuleType.PREFIX_BLOCK,
                normalizedValue = "+49$suffix",
                action = ScreeningAction.BLOCK,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
        }
        val snapshot = ScreeningSnapshot.compile(rules, emptyList(), ScreeningSettings(), 1)
        val target = NormalizedPhoneNumber.fromCanonical("+4919999123456")!!
        val start = System.nanoTime()
        val decision = ScreeningEngine().evaluate(
            PhoneIdentity.Number(target),
            CarrierVerification.NOT_VERIFIED,
            snapshot,
            now,
        )
        val durationMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(ScreeningAction.BLOCK, decision.action)
        assertTrue("Präfixauswertung dauerte $durationMs ms", durationMs < 250)
    }

    @Test fun `fuenfzigtausend Reputationswerte bleiben exakt adressierbar`() {
        val now = System.currentTimeMillis()
        val entries = (0 until 50_000).map { index ->
            val value = "+4916${index.toString().padStart(10, '0')}"
            NumberReputation(
                NormalizedPhoneNumber.fromCanonical(value)!!,
                90,
                90,
                ReputationCategory.FRAUD,
                20,
                0,
                3,
                now,
                null,
                listOf(ReputationSource("Leistungstest", "1")),
            )
        }
        val snapshot = ScreeningSnapshot.compile(emptyList(), entries, ScreeningSettings(), 1)
        val target = entries.last().number
        val start = System.nanoTime()
        val decision = ScreeningEngine().evaluate(
            PhoneIdentity.Number(target),
            CarrierVerification.NOT_VERIFIED,
            snapshot,
            now,
        )
        val durationMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(ScreeningAction.BLOCK, decision.action)
        assertTrue("Reputationssuche dauerte $durationMs ms", durationMs < 250)
    }
}
