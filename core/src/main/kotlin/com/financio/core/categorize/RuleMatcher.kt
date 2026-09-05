package com.financio.core.categorize

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.ParsedTransaction

/**
 * Applies categorization rules in priority order (lowest number first) and returns the
 * category id of the first match — or null, meaning "te categoriseren": the app asks the
 * user once and remembers the answer as a new rule, rather than guessing.
 */
class RuleMatcher(private val rules: List<CategoryRule>) {

    private val sortedRules = rules.sortedBy { it.priority }

    fun categorize(transaction: ParsedTransaction): Long? =
        sortedRules.firstOrNull { rule -> matches(rule, transaction) }?.categoryId

    private fun matches(rule: CategoryRule, transaction: ParsedTransaction): Boolean = when (rule.matchType) {
        MatchType.COUNTERPARTY_EXACT ->
            transaction.counterpartyIban?.equals(rule.pattern, ignoreCase = true) == true

        MatchType.KEYWORD -> {
            val haystack = "${transaction.counterpartyName} ${transaction.description}"
            haystack.contains(rule.pattern, ignoreCase = true)
        }
    }
}
