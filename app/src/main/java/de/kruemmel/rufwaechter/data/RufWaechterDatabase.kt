package de.kruemmel.rufwaechter.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NumberRuleEntity::class,
        NumberReputationEntity::class,
        CallDecisionEntity::class,
        FeedMetadataEntity::class,
        PhoneBlockEntryEntity::class,
        PhoneBlockSyncStateEntity::class,
        PhoneBlockPendingReportEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RufWaechterDatabase : RoomDatabase() {
    abstract fun dao(): RufWaechterDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `phoneblock_entries` (
                        `normalizedNumber` TEXT NOT NULL,
                        `listType` TEXT NOT NULL,
                        `rating` TEXT,
                        `votes` INTEGER NOT NULL,
                        `comment` TEXT NOT NULL,
                        `lastActivity` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`normalizedNumber`, `listType`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_phoneblock_entries_listType` ON `phoneblock_entries` (`listType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_phoneblock_entries_updatedAt` ON `phoneblock_entries` (`updatedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `phoneblock_sync_state` (
                        `sourceId` TEXT NOT NULL,
                        `version` INTEGER,
                        `lastFullSyncAt` INTEGER,
                        `lastIncrementalSyncAt` INTEGER,
                        `lastPersonalSyncAt` INTEGER,
                        PRIMARY KEY(`sourceId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `phoneblock_pending_reports` (
                        `normalizedNumber` TEXT NOT NULL,
                        `rating` TEXT NOT NULL,
                        `comment` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        PRIMARY KEY(`normalizedNumber`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_phoneblock_pending_reports_createdAt` " +
                        "ON `phoneblock_pending_reports` (`createdAt`)",
                )
            }
        }
    }
}
