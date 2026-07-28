package de.kruemmel.rufwaechter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RufWaechterDao {
    @Query("SELECT * FROM number_rules ORDER BY updatedAt DESC, id ASC")
    fun observeRules(): Flow<List<NumberRuleEntity>>

    @Query("SELECT * FROM call_decisions ORDER BY timestamp DESC LIMIT :limit")
    fun observeDecisions(limit: Int = 500): Flow<List<CallDecisionEntity>>

    @Query("SELECT * FROM feed_metadata ORDER BY downloadedAt DESC")
    fun observeFeedMetadata(): Flow<List<FeedMetadataEntity>>

    @Query("SELECT * FROM phoneblock_entries ORDER BY normalizedNumber")
    fun observePhoneBlockEntries(): Flow<List<PhoneBlockEntryEntity>>

    @Query("SELECT COUNT(*) FROM phoneblock_entries WHERE listType = 'COMMUNITY'")
    fun observePhoneBlockCommunityCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM phoneblock_pending_reports")
    fun observePhoneBlockPendingCount(): Flow<Int>

    @Query("SELECT * FROM number_rules")
    suspend fun getAllRules(): List<NumberRuleEntity>

    @Query("SELECT * FROM number_reputation WHERE expiresAt IS NULL OR expiresAt > :now")
    suspend fun getActiveReputation(now: Long): List<NumberReputationEntity>

    @Query("SELECT * FROM number_reputation ORDER BY normalizedNumber")
    suspend fun getAllReputation(): List<NumberReputationEntity>

    @Query("SELECT * FROM call_decisions ORDER BY timestamp DESC")
    suspend fun getAllDecisions(): List<CallDecisionEntity>

    @Query("SELECT * FROM feed_metadata ORDER BY downloadedAt DESC")
    suspend fun getAllFeedMetadata(): List<FeedMetadataEntity>

    @Query("SELECT * FROM phoneblock_entries ORDER BY normalizedNumber")
    suspend fun getAllPhoneBlockEntries(): List<PhoneBlockEntryEntity>

    @Query("SELECT * FROM phoneblock_sync_state WHERE sourceId = 'phoneblock'")
    suspend fun getPhoneBlockSyncState(): PhoneBlockSyncStateEntity?

    @Query("SELECT * FROM phoneblock_pending_reports ORDER BY createdAt LIMIT :limit")
    suspend fun getPendingPhoneBlockReports(limit: Int): List<PhoneBlockPendingReportEntity>

    @Query("SELECT COUNT(*) FROM number_reputation WHERE expiresAt IS NULL OR expiresAt > :now")
    fun observeActiveReputationCount(now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: NumberRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRules(rules: List<NumberRuleEntity>): List<Long>

    @Update
    suspend fun updateRule(rule: NumberRuleEntity)

    @Delete
    suspend fun deleteRule(rule: NumberRuleEntity)

    @Insert
    suspend fun insertDecision(decision: CallDecisionEntity): Long

    @Query("UPDATE call_decisions SET userCorrection = :correction WHERE id = :id")
    suspend fun setDecisionCorrection(id: Long, correction: String)

    @Query("DELETE FROM call_decisions WHERE id = :id")
    suspend fun deleteDecision(id: Long)

    @Query("DELETE FROM call_decisions")
    suspend fun clearDecisions()

    @Query("DELETE FROM call_decisions WHERE timestamp < :cutoff")
    suspend fun purgeOldDecisions(cutoff: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReputation(entries: List<NumberReputationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeedMetadata(metadata: FeedMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoneBlockEntries(entries: List<PhoneBlockEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoneBlockSyncState(state: PhoneBlockSyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingPhoneBlockReport(report: PhoneBlockPendingReportEntity)

    @Query("DELETE FROM phoneblock_entries WHERE listType = :listType")
    suspend fun clearPhoneBlockEntries(listType: String)

    @Query("DELETE FROM phoneblock_entries WHERE listType = 'COMMUNITY' AND normalizedNumber IN (:numbers)")
    suspend fun deletePhoneBlockCommunityNumbers(numbers: List<String>)

    @Query("DELETE FROM phoneblock_pending_reports WHERE normalizedNumber = :number")
    suspend fun deletePendingPhoneBlockReport(number: String)

    @Query("UPDATE phoneblock_pending_reports SET attempts = attempts + 1 WHERE normalizedNumber = :number")
    suspend fun incrementPendingPhoneBlockReportAttempts(number: String)

    @Query("DELETE FROM number_reputation WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpiredReputation(now: Long): Int

    @Query("DELETE FROM number_reputation")
    suspend fun clearReputation()

    @Query("DELETE FROM number_rules")
    suspend fun clearRules()

    @Query("DELETE FROM feed_metadata")
    suspend fun clearFeedMetadata()

    @Query("DELETE FROM phoneblock_entries")
    suspend fun clearPhoneBlockEntries()

    @Query("DELETE FROM phoneblock_sync_state")
    suspend fun clearPhoneBlockSyncState()

    @Query("DELETE FROM phoneblock_pending_reports")
    suspend fun clearPendingPhoneBlockReports()
}
