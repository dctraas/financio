package com.financio.core.backup

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType

/**
 * Resolves each [RuleExport]'s category *name* against the caller's up-to-date id map — the
 * caller applies [CategoryImport.Plan.toCreate] first and re-fetches categories, so a rule
 * pointing at a category the same import just created still resolves correctly here rather than
 * needing awkward two-phase bookkeeping inside this function.
 *
 * Also additive-only, matching [CategoryImport]: a rule identical to one that already exists
 * (same category, match type and pattern) is skipped rather than duplicated — repeatedly
 * re-importing the same export file should be a no-op the second time, not pile up duplicate rows.
 * Priority is not compared, deliberately: an existing rule someone hand-tuned the priority of is
 * never silently overwritten by an import.
 */
object RuleImport {

    data class Plan(
        val toCreate: List<CategoryRule>,
        val skippedUnresolvedCategory: Int,
        val skippedDuplicate: Int,
    )

    fun plan(rules: List<RuleExport>, categoryIdsByName: Map<String, Long>, existing: List<CategoryRule>): Plan {
        val existingKeys = existing.map { RuleKey(it.categoryId, it.matchType, it.pattern) }.toSet()
        var unresolved = 0
        var duplicate = 0
        val toCreate = mutableListOf<CategoryRule>()

        for (rule in rules) {
            val categoryId = categoryIdsByName[rule.categoryName]
            val matchType = runCatching { MatchType.valueOf(rule.matchType) }.getOrNull()
            if (categoryId == null || matchType == null) {
                unresolved++
                continue
            }
            val key = RuleKey(categoryId, matchType, rule.pattern)
            if (key in existingKeys) {
                duplicate++
                continue
            }
            toCreate += CategoryRule(categoryId = categoryId, matchType = matchType, pattern = rule.pattern, priority = rule.priority)
        }

        return Plan(toCreate = toCreate, skippedUnresolvedCategory = unresolved, skippedDuplicate = duplicate)
    }

    private data class RuleKey(val categoryId: Long, val matchType: MatchType, val pattern: String)
}
