package com.financio.core.backup

import com.financio.core.model.Budget
import java.time.YearMonth

/**
 * Additive-only, same philosophy as [CategoryImport]: a category+month that already has a budget
 * row locally is left alone rather than overwritten - someone's hand-tuned limit for a month
 * should never be silently replaced by an import, matching the app's own explanatory copy on the
 * back-up screen ("bestaat iets al lokaal, dan blijft dat ongewijzigd staan").
 */
object BudgetImport {

    data class Resolved(val export: BudgetExport, val categoryId: Long) {
        val yearMonth: YearMonth get() = YearMonth.parse(export.yearMonth)
    }

    data class Plan(val toCreate: List<Resolved>, val skippedUnresolvedCategory: Int, val skippedExisting: Int)

    fun plan(budgets: List<BudgetExport>, categoryIdsByName: Map<String, Long>, existing: List<Budget>): Plan {
        val existingKeys = existing.map { it.categoryId to it.yearMonth.toString() }.toSet()
        var unresolved = 0
        var skippedExisting = 0
        val toCreate = mutableListOf<Resolved>()

        for (budget in budgets) {
            val categoryId = categoryIdsByName[budget.categoryName]
            if (categoryId == null) {
                unresolved++
                continue
            }
            if ((categoryId to budget.yearMonth) in existingKeys) {
                skippedExisting++
                continue
            }
            toCreate += Resolved(budget, categoryId)
        }

        return Plan(toCreate = toCreate, skippedUnresolvedCategory = unresolved, skippedExisting = skippedExisting)
    }
}
