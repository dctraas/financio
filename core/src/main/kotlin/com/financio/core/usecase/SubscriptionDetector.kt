package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class SubscriptionCadence { MONTHLY, YEARLY }

/**
 * The most recent charge differs from whatever price came before it — "Netflix ging van €11,99
 * naar €13,99". [previousAmount] is the price the merchant billed *before* the run of charges at
 * [newAmount] (the current price) began, found by walking back from the latest charge past every
 * trailing occurrence that already matches it — see [SubscriptionDetector.priceChangeFor].
 */
data class PriceChange(val previousAmount: Money, val newAmount: Money) {
    /** The extra yearly cost this change works out to, scaled by how often [cadence] bills. */
    fun yearlyDifference(cadence: SubscriptionCadence): Money {
        val chargesPerYear = when (cadence) {
            SubscriptionCadence.MONTHLY -> 12
            SubscriptionCadence.YEARLY -> 1
        }
        return Money((newAmount.cents - previousAmount.cents) * chargesPerYear)
    }
}

/** One merchant that looks like a recurring subscription, going by its debit history alone. */
data class DetectedSubscription(
    val counterpartyName: String,
    val averageAmount: Money,
    /** The most recent charge specifically - [averageAmount] can mask a price change by averaging over it. */
    val lastAmount: Money,
    val occurrences: Int,
    val lastDate: LocalDate,
    val estimatedNextDate: LocalDate,
    val cadence: SubscriptionCadence,
    /** Null unless the current price differs from what this merchant billed before. */
    val priceChange: PriceChange?,
)

/**
 * A merchant with *some* recurring-looking activity - occurring often enough, roughly the right
 * number of days apart - but not confident enough for [SubscriptionDetector.detect]'s stricter
 * gate. Surfaced separately as a "twijfelgeval" yes/no confirmation instead of silently dropped,
 * so a real subscription whose amount fluctuates a bit more than usual (a variable-rate energy
 * contract, say) isn't just invisible.
 */
data class UncertainSubscription(
    val counterpartyName: String,
    val occurrences: Int,
    val lastAmount: Money,
    val lastDate: LocalDate,
    /** Human-readable, e.g. "bedragen wisselen, dag varieert" - which of the strict checks this merchant only barely failed. */
    val reason: String,
)

/**
 * Finds recurring debits — Netflix, Spotify, insurance, a gym membership — by looking for a
 * merchant whose charges are both regular in timing and consistent in amount, with no bank API or
 * merchant database involved: it's the same two signals a human would use scanning a statement by
 * eye. Deliberately conservative (both checks required, not just one) for [detect]'s confirmed
 * list; [detectUncertain] relaxes both checks to catch the "probably, but I'm not sure" cases
 * instead of just discarding them.
 */
object SubscriptionDetector {
    private const val MIN_MONTHLY_OCCURRENCES = 3
    private const val MIN_YEARLY_OCCURRENCES = 2
    private val MONTHLY_GAP_RANGE = 25L..35L
    private val YEARLY_GAP_RANGE = 350L..380L
    private val LOOSE_GAP_RANGE = 20L..40L
    private const val STRICT_TOLERANCE_PERCENT = 15
    private const val LOOSE_TOLERANCE_PERCENT = 30

    fun detect(transactions: List<Transaction>): List<DetectedSubscription> =
        candidateGroups(transactions)
            .mapNotNull { (name, group) -> detectFor(name, group) }
            .sortedByDescending { abs(it.averageAmount.cents) }

    /** Everything that looked plausible but didn't clear [detect]'s stricter bar - see [UncertainSubscription]. */
    fun detectUncertain(transactions: List<Transaction>): List<UncertainSubscription> {
        val confirmedNames = detect(transactions).map { it.counterpartyName }.toSet()
        return candidateGroups(transactions)
            .filterKeys { it !in confirmedNames }
            .mapNotNull { (name, group) -> uncertainFor(name, group) }
    }

    private fun candidateGroups(transactions: List<Transaction>): Map<String, List<Transaction>> =
        transactions.filter { it.amount.cents < 0 }.groupBy { it.counterpartyName } // subscriptions are always expenses

    private fun detectFor(counterpartyName: String, group: List<Transaction>): DetectedSubscription? {
        val sorted = group.sortedBy { it.date }
        val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val amounts = sorted.map { abs(it.amount.cents) }
        if (!isAmountConsistent(amounts, STRICT_TOLERANCE_PERCENT)) return null

        val cadence = when {
            sorted.size >= MIN_MONTHLY_OCCURRENCES && isGapRegular(gaps, MONTHLY_GAP_RANGE) -> SubscriptionCadence.MONTHLY
            sorted.size >= MIN_YEARLY_OCCURRENCES && isGapRegular(gaps, YEARLY_GAP_RANGE) -> SubscriptionCadence.YEARLY
            else -> return null
        }

        return buildResult(counterpartyName, sorted, gaps, amounts, cadence)
    }

    /**
     * Loosens both checks (a wider gap window, a wider amount tolerance) to catch merchants that
     * only barely missed [detectFor]'s bar, rather than treating "almost regular" the same as
     * "not a subscription at all".
     */
    private fun uncertainFor(counterpartyName: String, group: List<Transaction>): UncertainSubscription? {
        if (group.size < MIN_MONTHLY_OCCURRENCES) return null
        val sorted = group.sortedBy { it.date }
        val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val amounts = sorted.map { abs(it.amount.cents) }

        val looseGapsOk = isGapRegular(gaps, LOOSE_GAP_RANGE)
        val looseAmountsOk = isAmountConsistent(amounts, LOOSE_TOLERANCE_PERCENT)
        if (!looseGapsOk || !looseAmountsOk) return null // not even a plausible pattern at the loosest thresholds

        val reasons = buildList {
            if (!isAmountConsistent(amounts, STRICT_TOLERANCE_PERCENT)) add("bedragen wisselen")
            if (!isGapRegular(gaps, MONTHLY_GAP_RANGE)) add("dag varieert")
        }
        if (reasons.isEmpty()) return null // passed every strict check after all - detect() should already have this one

        return UncertainSubscription(
            counterpartyName = counterpartyName,
            occurrences = sorted.size,
            lastAmount = sorted.last().amount,
            lastDate = sorted.last().date,
            reason = reasons.joinToString(", "),
        )
    }

    /** 15%/30% tolerance, floored at 100 cents so a €1 subscription's rounding isn't stricter than a €50 one's. */
    private fun isAmountConsistent(amounts: List<Long>, tolerancePercent: Int): Boolean {
        val averageCents = amounts.sum() / amounts.size
        val maxDeviation = amounts.maxOf { abs(it - averageCents) }
        val tolerance = maxOf(averageCents * tolerancePercent / 100, 100)
        return maxDeviation <= tolerance
    }

    /** Allows one skipped/merged cycle (e.g. a missed month, or two charges collapsed into a refund+recharge) without losing the pattern, but requires most gaps to actually fall in [range]. */
    private fun isGapRegular(gaps: List<Long>, range: LongRange): Boolean {
        if (gaps.isEmpty()) return false
        val matching = gaps.count { it in range }
        return matching >= gaps.size - 1 && matching > 0
    }

    private fun buildResult(
        counterpartyName: String,
        sorted: List<Transaction>,
        gaps: List<Long>,
        amounts: List<Long>,
        cadence: SubscriptionCadence,
    ): DetectedSubscription {
        val range = if (cadence == SubscriptionCadence.MONTHLY) MONTHLY_GAP_RANGE else YEARLY_GAP_RANGE
        val averageCents = amounts.sum() / amounts.size
        val lastDate = sorted.last().date
        val fallbackGapDays = if (cadence == SubscriptionCadence.MONTHLY) 30.0 else 365.0
        val averageGap = gaps.filter { it in range }.average().takeIf { !it.isNaN() } ?: fallbackGapDays

        return DetectedSubscription(
            counterpartyName = counterpartyName,
            averageAmount = Money(-averageCents), // sign restored: a subscription is always a debit
            lastAmount = sorted.last().amount,
            occurrences = sorted.size,
            lastDate = lastDate,
            estimatedNextDate = lastDate.plusDays(averageGap.toLong()),
            cadence = cadence,
            priceChange = priceChangeFor(sorted),
        )
    }

    /**
     * Walks back from the most recent charge past every trailing occurrence that already matches
     * it, to find the price billed just before the current one took over. Null if the price has
     * never changed (or there's only one occurrence to compare against).
     */
    private fun priceChangeFor(sorted: List<Transaction>): PriceChange? {
        val amounts = sorted.map { it.amount }
        val current = amounts.last()
        var i = amounts.lastIndex
        while (i > 0 && amounts[i] == current) i--
        if (i <= 0 && amounts.getOrNull(i) == current) return null
        val previous = amounts.getOrNull(i) ?: return null
        return if (previous != current) PriceChange(previousAmount = previous, newAmount = current) else null
    }
}
