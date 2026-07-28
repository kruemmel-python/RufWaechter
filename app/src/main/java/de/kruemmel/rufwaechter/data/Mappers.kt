package de.kruemmel.rufwaechter.data

import de.kruemmel.rufwaechter.domain.NormalizedPhoneNumber
import de.kruemmel.rufwaechter.domain.NumberReputation
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.ReputationCategory
import de.kruemmel.rufwaechter.domain.ReputationSource
import de.kruemmel.rufwaechter.domain.RuleSource
import de.kruemmel.rufwaechter.domain.RuleType
import de.kruemmel.rufwaechter.domain.ScreeningAction
import org.json.JSONArray
import org.json.JSONObject

fun NumberRuleEntity.toDomain(): NumberRule = NumberRule(
    id = id,
    type = RuleType.valueOf(ruleType),
    normalizedValue = normalizedValue,
    action = ScreeningAction.valueOf(action),
    enabled = enabled,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
    expiresAtEpochMs = expiresAt,
    note = note,
    source = RuleSource.valueOf(source),
)

fun NumberRule.toEntity(): NumberRuleEntity = NumberRuleEntity(
    id = id,
    ruleType = type.name,
    normalizedValue = normalizedValue,
    action = action.name,
    enabled = enabled,
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
    expiresAt = expiresAtEpochMs,
    note = note.take(500),
    source = source.name,
)

fun NumberReputationEntity.toDomain(): NumberReputation? {
    val number = NormalizedPhoneNumber.fromCanonical(normalizedNumber) ?: return null
    val sources = runCatching {
        val array = JSONArray(provenanceJson)
        List(array.length()) { index ->
            val source = array.getJSONObject(index)
            ReputationSource(source.getString("name"), source.optString("version"))
        }
    }.getOrDefault(emptyList())
    return NumberReputation(
        number,
        spamScore.coerceIn(0, 100),
        confidence.coerceIn(0, 100),
        ReputationCategory.valueOf(category),
        reportCount.coerceAtLeast(0),
        positiveCount.coerceAtLeast(0),
        sourceCount.coerceAtLeast(0),
        lastUpdated,
        expiresAt,
        sources,
    )
}

fun NumberReputation.toEntity(): NumberReputationEntity {
    val provenance = JSONArray().apply {
        provenance.forEach { source ->
            put(JSONObject().put("name", source.name.take(100)).put("version", source.version.take(50)))
        }
    }
    return NumberReputationEntity(
        normalizedNumber = number.value,
        spamScore = safeSpamScore,
        confidence = safeConfidence,
        category = category.name,
        reportCount = reportCount.coerceAtLeast(0),
        positiveCount = positiveCount.coerceAtLeast(0),
        sourceCount = sourceCount.coerceAtLeast(0),
        lastUpdated = lastUpdatedEpochMs,
        expiresAt = expiresAtEpochMs,
        provenanceJson = provenance.toString(),
    )
}
