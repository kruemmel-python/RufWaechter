package de.kruemmel.rufwaechter.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "number_rules",
    indices = [
        Index(value = ["ruleType", "normalizedValue"], unique = true),
        Index(value = ["normalizedValue"]),
        Index(value = ["enabled"]),
        Index(value = ["expiresAt"]),
    ],
)
data class NumberRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleType: String,
    val normalizedValue: String?,
    val action: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long?,
    val note: String,
    val source: String,
)

@Entity(
    tableName = "number_reputation",
    indices = [Index(value = ["expiresAt"]), Index(value = ["lastUpdated"])],
)
data class NumberReputationEntity(
    @PrimaryKey val normalizedNumber: String,
    val spamScore: Int,
    val confidence: Int,
    val category: String,
    val reportCount: Int,
    val positiveCount: Int,
    val sourceCount: Int,
    val lastUpdated: Long,
    val expiresAt: Long?,
    val provenanceJson: String,
)

@Entity(
    tableName = "call_decisions",
    indices = [
        Index(value = ["normalizedNumber"]),
        Index(value = ["timestamp"]),
    ],
)
data class CallDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedNumber: String?,
    val displayNumber: String,
    val identityType: String,
    val timestamp: Long,
    val action: String,
    val score: Int,
    val confidence: Int,
    val reasonCodes: String,
    val matchedRuleId: Long?,
    val verificationStatus: String,
    val evaluationDurationMs: Long,
    val userCorrection: String?,
)

@Entity(tableName = "feed_metadata")
data class FeedMetadataEntity(
    @PrimaryKey val feedId: String,
    val sourceName: String,
    val version: String,
    val downloadedAt: Long,
    val recordCount: Int,
    val sha256: String,
    val status: String,
    val errorMessage: String?,
)

@Entity(
    tableName = "phoneblock_entries",
    primaryKeys = ["normalizedNumber", "listType"],
    indices = [Index(value = ["listType"]), Index(value = ["updatedAt"])],
)
data class PhoneBlockEntryEntity(
    val normalizedNumber: String,
    val listType: String,
    val rating: String?,
    val votes: Int,
    val comment: String,
    val lastActivity: Long?,
    val updatedAt: Long,
)

@Entity(tableName = "phoneblock_sync_state")
data class PhoneBlockSyncStateEntity(
    @PrimaryKey val sourceId: String = "phoneblock",
    val version: Long?,
    val lastFullSyncAt: Long?,
    val lastIncrementalSyncAt: Long?,
    val lastPersonalSyncAt: Long?,
)

@Entity(
    tableName = "phoneblock_pending_reports",
    indices = [Index(value = ["createdAt"])],
)
data class PhoneBlockPendingReportEntity(
    @PrimaryKey val normalizedNumber: String,
    val rating: String,
    val comment: String,
    val createdAt: Long,
    val attempts: Int = 0,
)
