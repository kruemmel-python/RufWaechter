package de.kruemmel.rufwaechter.importexport

import de.kruemmel.rufwaechter.data.AppRepository
import de.kruemmel.rufwaechter.data.CallDecisionEntity
import de.kruemmel.rufwaechter.data.FeedMetadataEntity
import de.kruemmel.rufwaechter.data.NumberReputationEntity
import de.kruemmel.rufwaechter.data.PhoneBlockEntryEntity
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.ScreeningSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

class ImportExportManager(
    private val codec: ReputationJsonCodec,
    private val repository: AppRepository,
) {
    suspend fun importReputation(input: InputStream, feedId: String = "local-import"): ReputationParseOutcome {
        return when (val parsed = codec.parse(input)) {
            is ReputationParseOutcome.Failure -> parsed
            is ReputationParseOutcome.Success -> {
                val result = parsed.result
                repository.importReputation(
                    result.accepted,
                    FeedMetadataEntity(
                        feedId = feedId,
                        sourceName = result.sourceName,
                        version = result.sourceVersion,
                        downloadedAt = System.currentTimeMillis(),
                        recordCount = result.accepted.size,
                        sha256 = result.sha256,
                        status = "SUCCESS",
                        errorMessage = null,
                    ),
                )
                parsed
            }
        }
    }

    fun exportData(
        output: OutputStream,
        rules: List<NumberRule>,
        reputation: List<NumberReputationEntity>,
        decisions: List<CallDecisionEntity>,
        feeds: List<FeedMetadataEntity>,
        phoneBlockEntries: List<PhoneBlockEntryEntity>,
        settings: ScreeningSettings,
    ) {
        val root = JSONObject()
            .put("schemaVersion", 2)
            .put("generatedAt", Instant.now().toString())
            .put("source", JSONObject().put("name", "RufWächter-Export").put("version", "1.0"))
        root.put("settings", JSONObject()
            .put("protectionEnabled", settings.protectionEnabled)
            .put("defaultAction", settings.defaultAction.name)
            .put("warnAt", settings.thresholds.warnAt)
            .put("silenceAt", settings.thresholds.silenceAt)
            .put("blockAt", settings.thresholds.blockAt)
            .put("minimumBlockConfidence", settings.minimumBlockConfidence)
            .put("phoneBlockEnabled", settings.phoneBlockEnabled)
            .put("phoneBlockContribute", settings.phoneBlockContribute)
            .put("phoneBlockDefaultRating", settings.phoneBlockDefaultRating))
        root.put("rules", JSONArray().apply {
            rules.forEach { rule ->
                put(JSONObject()
                    .put("id", rule.id)
                    .put("ruleType", rule.type.name)
                    .put("normalizedValue", rule.normalizedValue)
                    .put("action", rule.action.name)
                    .put("enabled", rule.enabled)
                    .put("createdAt", Instant.ofEpochMilli(rule.createdAtEpochMs).toString())
                    .put("updatedAt", Instant.ofEpochMilli(rule.updatedAtEpochMs).toString())
                    .put("expiresAt", rule.expiresAtEpochMs?.let { Instant.ofEpochMilli(it).toString() })
                    .put("note", rule.note)
                    .put("source", rule.source.name))
            }
        })
        root.put("reputation", JSONArray().apply {
            reputation.forEach { entry ->
                put(JSONObject()
                    .put("normalizedNumber", entry.normalizedNumber)
                    .put("spamScore", entry.spamScore)
                    .put("confidence", entry.confidence)
                    .put("category", entry.category)
                    .put("reportCount", entry.reportCount)
                    .put("positiveCount", entry.positiveCount)
                    .put("sourceCount", entry.sourceCount)
                    .put("lastUpdated", Instant.ofEpochMilli(entry.lastUpdated).toString())
                    .put("expiresAt", entry.expiresAt?.let { Instant.ofEpochMilli(it).toString() })
                    .put("provenance", JSONArray(entry.provenanceJson)))
            }
        })
        root.put("decisions", JSONArray().apply {
            decisions.forEach { entry ->
                put(JSONObject()
                    .put("id", entry.id)
                    .put("normalizedNumber", entry.normalizedNumber)
                    .put("displayNumber", entry.displayNumber)
                    .put("identityType", entry.identityType)
                    .put("timestamp", Instant.ofEpochMilli(entry.timestamp).toString())
                    .put("action", entry.action)
                    .put("score", entry.score)
                    .put("confidence", entry.confidence)
                    .put("reasonCodes", entry.reasonCodes)
                    .put("matchedRuleId", entry.matchedRuleId)
                    .put("verificationStatus", entry.verificationStatus)
                    .put("evaluationDurationMs", entry.evaluationDurationMs)
                    .put("userCorrection", entry.userCorrection))
            }
        })
        root.put("feeds", JSONArray().apply {
            feeds.forEach { feed ->
                put(JSONObject()
                    .put("feedId", feed.feedId)
                    .put("sourceName", feed.sourceName)
                    .put("version", feed.version)
                    .put("downloadedAt", Instant.ofEpochMilli(feed.downloadedAt).toString())
                    .put("recordCount", feed.recordCount)
                    .put("sha256", feed.sha256)
                    .put("status", feed.status))
            }
        })
        root.put("phoneBlockEntries", JSONArray().apply {
            phoneBlockEntries.forEach { entry ->
                put(
                    JSONObject()
                        .put("normalizedNumber", entry.normalizedNumber)
                        .put("listType", entry.listType)
                        .put("rating", entry.rating)
                        .put("votes", entry.votes)
                        .put("comment", entry.comment)
                        .put("lastActivity", entry.lastActivity?.let { Instant.ofEpochMilli(it).toString() })
                        .put("updatedAt", Instant.ofEpochMilli(entry.updatedAt).toString()),
                )
            }
        })
        root.put("phoneBlockCredentialsIncluded", false)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }
}
