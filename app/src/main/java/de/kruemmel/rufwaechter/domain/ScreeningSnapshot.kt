package de.kruemmel.rufwaechter.domain

class PrefixRuleIndex private constructor(
    private val byPrefix: Map<String, List<NumberRule>>,
) {
    fun bestMatch(number: String, nowEpochMs: Long): NumberRule? {
        for (length in number.length downTo 2) {
            val candidates = byPrefix[number.substring(0, length)].orEmpty()
                .filter { it.isActive(nowEpochMs) }
            if (candidates.isNotEmpty()) return candidates.bestByDeterministicPriority()
        }
        return null
    }

    companion object {
        fun from(rules: List<NumberRule>): PrefixRuleIndex =
            PrefixRuleIndex(
                rules.filter { it.type == RuleType.PREFIX_ALLOW || it.type == RuleType.PREFIX_BLOCK }
                    .mapNotNull { rule -> rule.normalizedValue?.let { it to rule } }
                    .groupBy({ it.first }, { it.second }),
            )

        val EMPTY = PrefixRuleIndex(emptyMap())
    }
}

internal fun List<NumberRule>.bestByDeterministicPriority(): NumberRule =
    sortedWith(
        compareByDescending<NumberRule> { it.updatedAtEpochMs }
            .thenByDescending { it.action.safetyRank }
            .thenBy { it.id },
    ).first()

private val ScreeningAction.safetyRank: Int
    get() = when (this) {
        ScreeningAction.BLOCK -> 4
        ScreeningAction.SILENCE -> 3
        ScreeningAction.WARN -> 2
        ScreeningAction.ALLOW -> 1
    }

data class ScreeningSnapshot(
    val exactRules: Map<String, List<NumberRule>>,
    val prefixRules: PrefixRuleIndex,
    val countryRules: Map<String, List<NumberRule>>,
    val specialRules: Map<RuleType, List<NumberRule>>,
    val reputation: Map<String, NumberReputation>,
    val settings: ScreeningSettings,
    val version: Long,
) {
    companion object {
        fun compile(
            rules: List<NumberRule>,
            reputation: List<NumberReputation>,
            settings: ScreeningSettings,
            version: Long,
        ): ScreeningSnapshot {
            val exactTypes = setOf(RuleType.EXACT_ALLOW, RuleType.EXACT_BLOCK, RuleType.TEMPORARY_EXACT)
            return ScreeningSnapshot(
                exactRules = rules.filter { it.type in exactTypes }
                    .mapNotNull { rule -> rule.normalizedValue?.let { it to rule } }
                    .groupBy({ it.first }, { it.second }),
                prefixRules = PrefixRuleIndex.from(rules),
                countryRules = rules.filter { it.type == RuleType.COUNTRY }
                    .mapNotNull { rule -> rule.normalizedValue?.let { it to rule } }
                    .groupBy({ it.first }, { it.second }),
                specialRules = rules.filter {
                    it.type == RuleType.PRIVATE_NUMBER || it.type == RuleType.UNKNOWN_NUMBER
                }.groupBy { it.type },
                reputation = reputation.associateBy { it.number.value },
                settings = settings,
                version = version,
            )
        }

        fun empty(settings: ScreeningSettings = ScreeningSettings()) =
            ScreeningSnapshot(emptyMap(), PrefixRuleIndex.EMPTY, emptyMap(), emptyMap(), emptyMap(), settings, 0)
    }
}
