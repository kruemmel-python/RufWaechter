package de.kruemmel.rufwaechter.data

import androidx.room.withTransaction
import de.kruemmel.rufwaechter.domain.CarrierVerification
import de.kruemmel.rufwaechter.domain.NumberReputation
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.RuleSource
import de.kruemmel.rufwaechter.domain.RuleType
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ScreeningDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockListType

class AppRepository(
    private val database: RufWaechterDatabase,
) {
    private val dao = database.dao()

    val rules: Flow<List<NumberRule>> = dao.observeRules().map { list -> list.map(NumberRuleEntity::toDomain) }
    val decisions: Flow<List<CallDecisionEntity>> = dao.observeDecisions()
    val feedMetadata: Flow<List<FeedMetadataEntity>> = dao.observeFeedMetadata()
    val reputationCount: Flow<Int> = dao.observeActiveReputationCount(System.currentTimeMillis())
    val phoneBlockEntries: Flow<List<PhoneBlockEntryEntity>> = dao.observePhoneBlockEntries()
    val phoneBlockCommunityCount: Flow<Int> = dao.observePhoneBlockCommunityCount()
    val phoneBlockPendingCount: Flow<Int> = dao.observePhoneBlockPendingCount()

    suspend fun loadRules(): List<NumberRule> = dao.getAllRules().map(NumberRuleEntity::toDomain)
    suspend fun loadReputation(now: Long): List<NumberReputation> =
        dao.getActiveReputation(now).mapNotNull(NumberReputationEntity::toDomain)
    suspend fun loadAllReputation(): List<NumberReputationEntity> = dao.getAllReputation()
    suspend fun loadAllDecisions(): List<CallDecisionEntity> = dao.getAllDecisions()
    suspend fun loadAllFeedMetadata(): List<FeedMetadataEntity> = dao.getAllFeedMetadata()
    suspend fun loadPhoneBlockEntries(): List<PhoneBlockEntryEntity> = dao.getAllPhoneBlockEntries()
    suspend fun loadPhoneBlockSyncState(): PhoneBlockSyncStateEntity? = dao.getPhoneBlockSyncState()

    suspend fun saveRule(rule: NumberRule): Long =
        if (rule.id == 0L) dao.insertRule(rule.toEntity()) else {
            dao.updateRule(rule.toEntity())
            rule.id
        }

    suspend fun deleteRule(rule: NumberRule) = dao.deleteRule(rule.toEntity())

    suspend fun importBlockedNumbers(numbers: Collection<String>): Int {
        val now = System.currentTimeMillis()
        val entities = numbers.distinct().map { number ->
            NumberRule(
                id = 0,
                type = RuleType.EXACT_BLOCK,
                normalizedValue = number,
                action = ScreeningAction.BLOCK,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                note = "Aus exportierter Telefon-Sperrliste importiert",
                source = RuleSource.IMPORT,
            ).toEntity()
        }
        return database.withTransaction {
            dao.insertRules(entities).count { it != -1L }
        }
    }

    suspend fun recordDecision(
        identity: PhoneIdentity,
        displayNumber: String,
        decision: ScreeningDecision,
        verification: CarrierVerification,
    ): Long = dao.insertDecision(
        CallDecisionEntity(
            normalizedNumber = (identity as? PhoneIdentity.Number)?.normalized?.value,
            displayNumber = displayNumber.take(64),
            identityType = identity::class.simpleName.orEmpty(),
            timestamp = System.currentTimeMillis(),
            action = decision.action.name,
            score = decision.score,
            confidence = decision.confidence,
            reasonCodes = decision.reasons.joinToString(",") { it.name },
            matchedRuleId = decision.matchedRuleId,
            verificationStatus = verification.name,
            evaluationDurationMs = decision.evaluationDurationMs,
            userCorrection = null,
        ),
    )

    suspend fun correctDecision(entry: CallDecisionEntity, spam: Boolean) {
        val number = entry.normalizedNumber ?: return
        val now = System.currentTimeMillis()
        val type = if (spam) RuleType.EXACT_BLOCK else RuleType.EXACT_ALLOW
        val action = if (spam) ScreeningAction.BLOCK else ScreeningAction.ALLOW
        database.withTransaction {
            dao.insertRule(
                NumberRule(
                    id = 0,
                    type = type,
                    normalizedValue = number,
                    action = action,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    note = if (spam) "Vom Benutzer als Spam markiert" else "Vom Benutzer als legitim markiert",
                    source = RuleSource.LOCAL_CORRECTION,
                ).toEntity(),
            )
            dao.setDecisionCorrection(entry.id, if (spam) "SPAM" else "LEGITIM")
        }
    }

    suspend fun deleteDecision(id: Long) = dao.deleteDecision(id)
    suspend fun clearHistory() = dao.clearDecisions()

    suspend fun importReputation(
        entries: List<NumberReputation>,
        metadata: FeedMetadataEntity,
    ) = database.withTransaction {
        dao.upsertReputation(entries.map(NumberReputation::toEntity))
        dao.upsertFeedMetadata(metadata)
    }

    suspend fun applyPhoneBlockCommunity(
        entries: List<PhoneBlockEntryEntity>,
        removedNumbers: List<String>,
        full: Boolean,
        version: Long,
        synchronizedAt: Long,
    ) = database.withTransaction {
        if (full) dao.clearPhoneBlockEntries(PhoneBlockListType.COMMUNITY.name)
        if (removedNumbers.isNotEmpty()) dao.deletePhoneBlockCommunityNumbers(removedNumbers)
        if (entries.isNotEmpty()) dao.upsertPhoneBlockEntries(entries)
        val previous = dao.getPhoneBlockSyncState()
        dao.upsertPhoneBlockSyncState(
            PhoneBlockSyncStateEntity(
                version = version,
                lastFullSyncAt = if (full) synchronizedAt else previous?.lastFullSyncAt,
                lastIncrementalSyncAt = synchronizedAt,
                lastPersonalSyncAt = previous?.lastPersonalSyncAt,
            ),
        )
        dao.upsertFeedMetadata(
            FeedMetadataEntity(
                feedId = "phoneblock-community",
                sourceName = "PhoneBlock Community",
                version = version.toString(),
                downloadedAt = synchronizedAt,
                recordCount = entries.size,
                sha256 = "",
                status = if (full) "FULL_SUCCESS" else "INCREMENTAL_SUCCESS",
                errorMessage = null,
            ),
        )
    }

    suspend fun replacePhoneBlockPersonalEntries(
        blacklist: List<PhoneBlockEntryEntity>,
        whitelist: List<PhoneBlockEntryEntity>,
        synchronizedAt: Long,
    ) = database.withTransaction {
        dao.clearPhoneBlockEntries(PhoneBlockListType.BLACKLIST.name)
        dao.clearPhoneBlockEntries(PhoneBlockListType.WHITELIST.name)
        if (blacklist.isNotEmpty()) dao.upsertPhoneBlockEntries(blacklist)
        if (whitelist.isNotEmpty()) dao.upsertPhoneBlockEntries(whitelist)
        val previous = dao.getPhoneBlockSyncState()
        dao.upsertPhoneBlockSyncState(
            previous?.copy(lastPersonalSyncAt = synchronizedAt)
                ?: PhoneBlockSyncStateEntity(lastPersonalSyncAt = synchronizedAt, version = null, lastFullSyncAt = null, lastIncrementalSyncAt = null),
        )
    }

    suspend fun queuePhoneBlockReport(number: String, rating: String, comment: String) {
        dao.upsertPendingPhoneBlockReport(
            PhoneBlockPendingReportEntity(
                normalizedNumber = number,
                rating = rating,
                comment = comment.take(500),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun pendingPhoneBlockReports(limit: Int = 50): List<PhoneBlockPendingReportEntity> =
        dao.getPendingPhoneBlockReports(limit)

    suspend fun markPhoneBlockReportSent(number: String) = dao.deletePendingPhoneBlockReport(number)
    suspend fun markPhoneBlockReportFailed(number: String) = dao.incrementPendingPhoneBlockReportAttempts(number)

    suspend fun performMaintenance(settingsRetentionDays: Int): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val historyCutoff = now - settingsRetentionDays * 24L * 60 * 60 * 1000
        return database.withTransaction {
            dao.purgeOldDecisions(historyCutoff) to dao.purgeExpiredReputation(now)
        }
    }

    suspend fun clearAll() = database.withTransaction {
        dao.clearDecisions()
        dao.clearRules()
        dao.clearReputation()
        dao.clearFeedMetadata()
        dao.clearPhoneBlockEntries()
        dao.clearPhoneBlockSyncState()
        dao.clearPendingPhoneBlockReports()
    }
}
