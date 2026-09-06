package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One merchant that looks like a recurring subscription, going by its debit history alone. */
data class DetectedSubscription(
    val counterpartyName: String,
    val averageAmount: Money,
    val occurrences: Int,
    val lastDate: LocalDate,
    val estimatedNextDate: LocalDate,
)

/**
 * Finds recurring monthly debits — Netflix, Spotify, insurance, a gym membership — by looking
 * for a merchant whose charges are both regular in timing (roughly a month apart) and consistent
 * in amount, with no bank API or merchant database involved: it's the same two signals a human
 * would use scanning a statement by eye. Deliberately conservative (both checks required, not
 * just one) — a supermarket visited every few weeks for wildly different amounts fails the
 * amount check; a rent payment that varies by a few cents in different months still passes it.
 */
object SubscriptionDetector {
    private const val MIN_OCCURRENCES = 3
    private val MONTHLY_GAP_RANGE = 25..35

    fun detect(transactions: List<Transaction>): List<DetectedSubscription> =
        transactions
            .filter { it.amount.cents < 0 } // subscriptions are always expenses
            .groupBy { it.counterpartyName }
            .mapNotNull { (name, group) -> detectFor(name, group) }
            .sortedByDescending { abs(it.averageAmount.cents) }

    private fun detectFor(counterpartyName: String, group: List<Transaction>): DetectedSubscription? {
        if (group.size < MIN_OCCURRENCES) return null
        val sorted = group.sortedBy { it.date }

        val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val monthlyGaps = gaps.count { it in MONTHLY_GAP_RANGE.first.toLong()..MONTHLY_GAP_RANGE.last.toLong() }
        // Allow one skipped/merged cycle (e.g. a missed month, or two charges collapsed into a
        // refund+recharge) without losing the pattern, but require most gaps to actually be ~monthly.
        val isRegular = monthlyGaps >= gaps.size - 1 && monthlyGaps > 0

        val amounts = sorted.map { abs(it.amount.cents) }
        val averageCents = amounts.sum() / amounts.size
        val maxDeviation = amounts.maxOf { abs(it - averageCents) }
        // 15% tolerance, floored at 100 cents so a €1 subscription's rounding isn't stricter than
        // a €50 one's — either way this rejects a merchant like a supermarket where amounts swing wildly.
        val tolerance = maxOf(averageCents * 15 / 100, 100)
        val isConsistentAmount = maxDeviation <= tolerance

        if (!isRegular || !isConsistentAmount) return null

        val lastDate = sorted.last().date
        val averageGap = gaps.filter { it in MONTHLY_GAP_RANGE.first.toLong()..MONTHLY_GAP_RANGE.last.toLong() }
            .average().takeIf { !it.isNaN() } ?: 30.0

        return DetectedSubscription(
            counterpartyName = counterpartyName,
            averageAmount = Money(-averageCents), // sign restored: a subscription is always a debit
            occurrences = sorted.size,
            lastDate = lastDate,
            estimatedNextDate = lastDate.plusDays(averageGap.toLong()),
        )
    }
}
