package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One recurring credit (e.g. salary) regular enough in timing and amount to expect again. */
data class RecurringIncome(val counterpartyName: String, val averageAmount: Money, val estimatedNextDate: LocalDate)

/**
 * The income-side counterpart to [SubscriptionDetector], used by Vandaag's balance forecast: a
 * flat daily-spend projection with subscription debits layered on top but *no* expected income at
 * all used to run the balance steadily downward for the rest of the month even when a paycheck
 * was actually still due before month-end - projecting a false, alarming shortfall for exactly the
 * households whose real end-of-month balance would be fine. Same two signals as
 * [SubscriptionDetector] (regular gap, consistent amount), just applied to credits instead of
 * debits, and without any of that detector's cadence/price-change/uncertain-tier machinery this
 * forecast doesn't need.
 */
object RecurringIncomeDetector {
    private const val MIN_OCCURRENCES = 3
    private val GAP_RANGE = 25L..35L
    private const val TOLERANCE_PERCENT = 15

    fun detect(transactions: List<Transaction>): List<RecurringIncome> =
        transactions.filter { it.amount.cents > 0 }
            .groupBy { it.counterpartyName }
            .mapNotNull { (name, group) -> detectFor(name, group) }

    private fun detectFor(counterpartyName: String, group: List<Transaction>): RecurringIncome? {
        if (group.size < MIN_OCCURRENCES) return null
        val sorted = group.sortedBy { it.date }
        val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val amounts = sorted.map { it.amount.cents }
        if (!isAmountConsistent(amounts) || !isGapRegular(gaps)) return null

        val averageCents = amounts.sum() / amounts.size
        val lastDate = sorted.last().date
        val averageGap = gaps.filter { it in GAP_RANGE }.average().takeIf { !it.isNaN() } ?: 30.0
        return RecurringIncome(counterpartyName, Money(averageCents), lastDate.plusDays(averageGap.toLong()))
    }

    /** Same 15% tolerance (floored at 100 cents) [SubscriptionDetector] uses for its own strict check. */
    private fun isAmountConsistent(amounts: List<Long>): Boolean {
        val averageCents = amounts.sum() / amounts.size
        val maxDeviation = amounts.maxOf { abs(it - averageCents) }
        val tolerance = maxOf(abs(averageCents) * TOLERANCE_PERCENT / 100, 100)
        return maxDeviation <= tolerance
    }

    /** Same "allow one skipped/merged cycle" rule [SubscriptionDetector] uses for its own monthly gap check. */
    private fun isGapRegular(gaps: List<Long>): Boolean {
        if (gaps.isEmpty()) return false
        val matching = gaps.count { it in GAP_RANGE }
        return matching >= gaps.size - 1 && matching > 0
    }
}
