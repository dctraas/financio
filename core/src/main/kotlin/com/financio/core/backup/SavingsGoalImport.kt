package com.financio.core.backup

import com.financio.core.model.SavingsGoal

/**
 * Additive-only, same philosophy as [CategoryImport]: a goal is matched by [SavingsGoal.name] -
 * there's no other stable natural key on a savings goal - and one that already exists locally is
 * left alone rather than re-linked or re-targeted.
 */
object SavingsGoalImport {

    data class Plan(val toCreate: List<SavingsGoalExport>, val skippedExisting: Int, val skippedUnresolvedCategory: Int)

    fun plan(goals: List<SavingsGoalExport>, categoryIdsByName: Map<String, Long>, existing: List<SavingsGoal>): Plan {
        val existingNames = existing.map { it.name }.toSet()
        var skippedExisting = 0
        var unresolved = 0
        val toCreate = mutableListOf<SavingsGoalExport>()

        for (goal in goals) {
            if (goal.name in existingNames) {
                skippedExisting++
                continue
            }
            if (goal.categoryName !in categoryIdsByName) {
                unresolved++
                continue
            }
            toCreate += goal
        }

        return Plan(toCreate = toCreate, skippedExisting = skippedExisting, skippedUnresolvedCategory = unresolved)
    }
}
