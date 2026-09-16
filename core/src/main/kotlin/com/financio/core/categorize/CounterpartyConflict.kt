package com.financio.core.categorize

import com.financio.core.model.Transaction

/**
 * The "Belastingdienst already means Vervoer elsewhere" signal a manual categorize checks before
 * blindly learning a rule for the whole counterparty (see [LearnedRule]) — a counterparty can
 * legitimately send transactions for more than one purpose under one name (motorrijtuigenbelasting
 * vs. kinderopvangtoeslag, both from "Belastingdienst"), and a rule keyed on the bare name would
 * wrongly capture both instead of just the one the user actually meant.
 */
object CounterpartyConflict {
    /** The counterparty's own already-categorized category, if any of its transactions already use a *different* one than [proposedCategoryId]. Null when this would be the counterparty's first category, or every existing one already agrees. */
    fun existingDifferentCategory(transactions: List<Transaction>, counterpartyName: String, proposedCategoryId: Long): Long? =
        transactions.firstOrNull {
            it.counterpartyName == counterpartyName && it.categoryId != null && it.categoryId != proposedCategoryId
        }?.categoryId

    /**
     * How many of [transactions] from [counterpartyName] would actually match [keyword] — the live
     * preview shown while typing a keyword to scope a new rule to "transactions like this one"
     * instead of the whole counterparty. Mirrors [MatchType.KEYWORD]'s own counterpartyName+description
     * check exactly, so the count the user sees here matches what the resulting rule will actually
     * catch everywhere else in the app.
     */
    fun matchingKeywordCount(transactions: List<Transaction>, counterpartyName: String, keyword: String): Int =
        if (keyword.isBlank()) {
            0
        } else {
            transactions.count {
                it.counterpartyName == counterpartyName && "${it.counterpartyName} ${it.description}".contains(keyword, ignoreCase = true)
            }
        }
}
