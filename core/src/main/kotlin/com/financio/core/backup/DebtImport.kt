package com.financio.core.backup

import com.financio.core.model.Debt

/**
 * Additive-only, same philosophy as [SavingsGoalImport]: a debt is matched by [Debt.name] -
 * there's no other stable natural key - and one that already exists locally is left alone rather
 * than re-linked or re-valued.
 */
object DebtImport {

    data class Plan(val toCreate: List<DebtExport>, val skippedExisting: Int)

    fun plan(debts: List<DebtExport>, existing: List<Debt>): Plan {
        val existingNames = existing.map { it.name }.toSet()
        val toCreate = debts.filter { it.name !in existingNames }
        return Plan(toCreate = toCreate, skippedExisting = debts.size - toCreate.size)
    }
}
