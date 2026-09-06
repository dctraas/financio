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

    /**
     * The limit actually in effect this month once rollover is applied: unused budget from last
     * month (limit minus spent, never negative) is added on top of this month's own limit — the
     * `BudgetEntity.rollover` flag existed since the very first schema but nothing ever read it
     * until now. An overspend last month is never carried forward as a *penalty*: rollover only
     * ever adds headroom, it doesn't shrink this month's limit if last month went over.
     */
    fun effectiveLimit(baseLimit: Money, rollover: Boolean, previousLimit: Money?, previousSpent: Money?): Money {
        if (!rollover || previousLimit == null || previousSpent == null) return baseLimit
        val leftover = (previousLimit.cents - previousSpent.cents).coerceAtLeast(0)
        return Money(baseLimit.cents + leftover)
    }

    /**
     * True only the moment spending pushes a category into a *more* severe [BudgetStatus] than it
     * was in before (OK→WARNING, OK→OVER, WARNING→OVER) — what a budget-threshold notification
     * should fire on. Relies on [BudgetStatus]'s declaration order (OK, WARNING, OVER) matching
     * severity, so an *improvement* (e.g. un-splitting a transaction moves spending back to OK)
     * never reports a crossing, and repeatedly overspending an already-OVER category doesn't
     * either — there's nothing new to say once the worst status is already showing.
     */
    fun crossedIntoWorseStatus(previousSpent: Money, newSpent: Money, limit: Money): Boolean {
        val previousStatus = evaluate(previousSpent, limit)
        val newStatus = evaluate(newSpent, limit)
        return newStatus.ordinal > previousStatus.ordinal
    }
}
