package de.kruemmel.rufwaechter.phoneblock

import de.kruemmel.rufwaechter.data.PhoneBlockEntryEntity
import de.kruemmel.rufwaechter.domain.NumberRule
import de.kruemmel.rufwaechter.domain.RuleSource
import de.kruemmel.rufwaechter.domain.RuleType
import de.kruemmel.rufwaechter.domain.ScreeningAction

enum class PhoneBlockAuthMode { BASIC, API_KEY }

data class PhoneBlockCredentials(
    val mode: PhoneBlockAuthMode,
    val username: String,
    val secret: String,
) {
    fun isValid(): Boolean = when (mode) {
        PhoneBlockAuthMode.BASIC -> username.isNotBlank() && secret.isNotBlank()
        PhoneBlockAuthMode.API_KEY -> secret.isNotBlank()
    }
}

enum class PhoneBlockListType { COMMUNITY, BLACKLIST, WHITELIST }

enum class PhoneBlockRating(val apiValue: String, val germanLabel: String) {
    MISSED("B_MISSED", "Unbekannter/verpasster Anruf"),
    PING("C_PING", "Ping-Anruf"),
    POLL("D_POLL", "Umfrage"),
    ADVERTISING("E_ADVERTISING", "Werbung"),
    GAMBLE("F_GAMBLE", "Gewinnspiel"),
    FRAUD("G_FRAUD", "Betrug"),
}

data class PhoneBlockCommunityPayload(
    val version: Long,
    val entries: List<PhoneBlockEntryEntity>,
    val removals: List<String>,
)

sealed interface PhoneBlockSyncResult {
    data class Updated(
        val communityChanges: Int,
        val removed: Int,
        val personalEntries: Int,
        val reportsSent: Int,
        val version: Long?,
        val note: String? = null,
    ) : PhoneBlockSyncResult

    data class UpToDate(val reportsSent: Int, val version: Long?) : PhoneBlockSyncResult
    data class Failed(val message: String, val retryable: Boolean) : PhoneBlockSyncResult
    data object NotConfigured : PhoneBlockSyncResult
}

fun PhoneBlockEntryEntity.toScreeningRule(): NumberRule {
    val personal = listType != PhoneBlockListType.COMMUNITY.name
    val allow = listType == PhoneBlockListType.WHITELIST.name
    val stableId = Long.MIN_VALUE + ((normalizedNumber + listType).hashCode().toLong() and 0x7fffffffL)
    return NumberRule(
        id = stableId,
        type = if (allow) RuleType.EXACT_ALLOW else RuleType.EXACT_BLOCK,
        normalizedValue = normalizedNumber,
        action = if (allow) ScreeningAction.ALLOW else ScreeningAction.BLOCK,
        enabled = true,
        createdAtEpochMs = lastActivity ?: updatedAt,
        updatedAtEpochMs = updatedAt,
        note = comment.ifBlank {
            if (personal) "Persönliche PhoneBlock-Liste" else "PhoneBlock Community (${rating.orEmpty()}, $votes Stimmen)"
        },
        source = if (personal) RuleSource.PHONEBLOCK_PERSONAL else RuleSource.PHONEBLOCK_COMMUNITY,
    )
}
