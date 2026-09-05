package com.financio.core.categorize

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType

/**
 * A rule the user deliberately authored in the categories & rules management screen, as opposed
 * to one inferred from a single manual categorization during import (see [LearnedRule]) or one
 * from the seeded starter set (see [DefaultCategorization]).
 *
 * Priority sits below the curated architecture-doc examples (1-3, currently illustrative only —
 * nothing seeds at that level) but above both [DefaultCategorization.PRIORITY] (20) and
 * [LearnedRule.PRIORITY] (50): something the user typed in on purpose should beat a generic
 * default guess or an incidental one-off learned from a single import choice.
 */
object ManualRule {
    const val PRIORITY = 10

    fun from(categoryId: Long, matchType: MatchType, pattern: String) = CategoryRule(
        categoryId = categoryId,
        matchType = matchType,
        pattern = pattern,
        priority = PRIORITY,
    )
}
