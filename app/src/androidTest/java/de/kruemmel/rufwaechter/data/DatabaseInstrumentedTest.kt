package de.kruemmel.rufwaechter.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.RuleType
import de.kruemmel.rufwaechter.domain.ScreeningAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var database: RufWaechterDatabase

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RufWaechterDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun RegelCrudUndEindeutigerKonflikt() = runBlocking {
        val now = System.currentTimeMillis()
        val first = NumberRule(0, RuleType.EXACT_BLOCK, "+493411234567", ScreeningAction.BLOCK, true, now, now)
        database.dao().insertRule(first.toEntity())
        database.dao().insertRule(first.copy(note = "ersetzt", updatedAtEpochMs = now + 1).toEntity())
        val loaded = database.dao().getAllRules()
        assertEquals(1, loaded.size)
        assertEquals("ersetzt", loaded.single().note)
        database.dao().deleteRule(loaded.single())
        assertEquals(0, database.dao().getAllRules().size)
    }

    @Test fun VerlaufKannVollstaendigGeloeschtWerden() = runBlocking {
        database.dao().insertDecision(
            CallDecisionEntity(
                normalizedNumber = null,
                displayNumber = "Privat",
                identityType = "PrivateNumber",
                timestamp = System.currentTimeMillis(),
                action = "ALLOW",
                score = 0,
                confidence = 0,
                reasonCodes = "DEFAULT_ACTION",
                matchedRuleId = null,
                verificationStatus = "NOT_VERIFIED",
                evaluationDurationMs = 1,
                userCorrection = null,
            ),
        )
        database.dao().clearDecisions()
        assertEquals(0, database.dao().purgeOldDecisions(Long.MAX_VALUE))
    }

    @Test fun PhoneBlockEintraegeUndWarteschlangeWerdenGespeichert() = runBlocking {
        database.dao().upsertPhoneBlockEntries(
            listOf(
                PhoneBlockEntryEntity(
                    normalizedNumber = "+493411234567",
                    listType = "COMMUNITY",
                    rating = "G_FRAUD",
                    votes = 10,
                    comment = "",
                    lastActivity = 1000,
                    updatedAt = 2000,
                ),
            ),
        )
        database.dao().upsertPendingPhoneBlockReport(
            PhoneBlockPendingReportEntity(
                normalizedNumber = "+493411234568",
                rating = "E_ADVERTISING",
                comment = "Werbung",
                createdAt = 2000,
            ),
        )
        assertEquals(1, database.dao().getAllPhoneBlockEntries().size)
        assertEquals(1, database.dao().getPendingPhoneBlockReports(10).size)
        database.dao().deletePendingPhoneBlockReport("+493411234568")
        assertEquals(0, database.dao().getPendingPhoneBlockReports(10).size)
    }
}
