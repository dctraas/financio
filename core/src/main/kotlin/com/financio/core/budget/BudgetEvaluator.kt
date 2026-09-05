package com.financio.core.budget

import com.financio.core.model.Money

/** Mirrors the thresholds shown in both the routekaart mockup and the schermontwerp. */
enum class BudgetStatus { OK, WARNING, OVER }

object BudgetEvaluator {
    private const val WARNING_THRESHOLD = 0.8

    /** [spent] and [limit] as positive magnitudes (i.e. already abs()'d expense totals). */
    fun evaluate(spent: Money, limit: Money): BudgetStatus {
        if (limit.cents <= 0) return BudgetStatus.OK
        val ratio = spent.cents.toDouble() / limit.cents.toDouble()
        return when {
            ratio > 1.0 -> BudgetStatus.OVER
            ratio >= WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.OK
        }
    }

    fun percentage(spent: Money, limit: Money): Int {
        if (limit.cents <= 0) return 0
        return ((spent.cents.toDouble() / limit.cents.toDouble()) * 100).toInt()
    }
}
