package com.financio.core.categorize

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType

/**
 * When a transaction matches no rule and the user assigns a category by hand, that choice
 * becomes a new keyword rule — the "geen match → vraag het de gebruiker, en onthoud het
 * antwoord" behavior from the architecture doc's rule table.
 *
 * Priority is deliberately coarser (higher number = later) than the curated examples in the
 * architecture doc (1-3): a rule learned from one transaction shouldn't outrank a rule someone
 * set up on purpose.
 */
object LearnedRule {
    const val PRIORITY = 50

    /**
     * [pattern] is usually the transaction's own counterparty name (the whole-counterparty rule
     * this always used to learn), but can also be a narrower keyword the user picked from the
     * description — see [CounterpartyConflict] — for a counterparty that sends transactions for
     * more than one purpose under one name.
     */
    fun from(categoryId: Long, pattern: String) = CategoryRule(
        categoryId = categoryId,
        matchType = MatchType.KEYWORD,
        pattern = pattern,
        priority = PRIORITY,
    )
}
