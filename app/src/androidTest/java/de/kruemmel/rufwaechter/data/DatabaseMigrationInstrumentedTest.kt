package de.kruemmel.rufwaechter.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    @Suppress("DEPRECATION")
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RufWaechterDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After fun deleteDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    @Throws(IOException::class)
    fun MigrationVonEinsNachZweiErhaeltRegelnUndErzeugtPhoneBlockTabellen() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO number_rules (
                    id, ruleType, normalizedValue, action, enabled,
                    createdAt, updatedAt, expiresAt, note, source
                ) VALUES (
                    1, 'EXACT_BLOCK', '+493411234567', 'BLOCK', 1,
                    1000, 1000, NULL, 'Bestand', 'USER'
                )
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            RufWaechterDatabase.MIGRATION_1_2,
        ).close()

        val migrated = Room.databaseBuilder(context, RufWaechterDatabase::class.java, TEST_DATABASE)
            .addMigrations(RufWaechterDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, migrated.dao().getAllRulesBlockingForTest().size)
            assertEquals(0, migrated.dao().getAllPhoneBlockEntriesBlockingForTest().size)
        } finally {
            migrated.close()
        }
    }

    private fun RufWaechterDao.getAllRulesBlockingForTest() =
        kotlinx.coroutines.runBlocking { getAllRules() }

    private fun RufWaechterDao.getAllPhoneBlockEntriesBlockingForTest() =
        kotlinx.coroutines.runBlocking { getAllPhoneBlockEntries() }

    companion object {
        private const val TEST_DATABASE = "migration-test"
    }
}
