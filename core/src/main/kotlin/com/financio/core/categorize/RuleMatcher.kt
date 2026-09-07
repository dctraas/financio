package com.financio.core.categorize

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.Transaction

/**
 * Applies categorization rules in priority order (lowest number first) and returns the
 * category id of the first match — or null, meaning "te categoriseren": the app asks the
 * user once and remembers the answer as a new rule, rather than guessing.
 */
class RuleMatcher(private val rules: List<CategoryRule>) {

    private val sortedRules = rules.sortedBy { it.priority }

    fun categorize(transaction: ParsedTransaction): Long? = matchingRule(transaction)?.categoryId

    fun matchingRule(transaction: ParsedTransaction): CategoryRule? =
        sortedRules.firstOrNull { rule -> matches(rule, transaction.counterpartyIban, transaction.counterpartyName, transaction.description) }

    /**
     * Same lookup, for an already-persisted transaction — the transaction detail screen shows
     * this alongside the category so a wrong pick is traceable to the rule that caused it,
     * rather than only being explainable at import time.
     */
    fun matchingRule(transaction: Transaction): CategoryRule? =
        sortedRules.firstOrNull { rule -> matches(rule, transaction.counterpartyIban, transaction.counterpartyName, transaction.description) }

    private fun matches(rule: CategoryRule, counterpartyIban: String?, counterpartyName: String, description: String): Boolean = when (rule.matchType) {
        MatchType.COUNTERPARTY_EXACT -> counterpartyIban?.equals(rule.pattern, ignoreCase = true) == true
        MatchType.KEYWORD -> "$counterpartyName $description".contains(rule.pattern, ignoreCase = true)
    }
}
