package com.financio.core.usecase

import com.financio.core.model.Money
import java.time.LocalDate

/**
 * "How much kan ik deze maand nog veilig uitgeven" — current balance minus subscriptions
 * expected to bill before the month ends, spread over the days left. Deliberately simple and
 * honest about it: this only knows about detected recurring subscriptions, not every future
 * obligation (a one-off bill, an irregular rent payment that never matched the recurrence
 * pattern) — see [SubscriptionDetector]. Budget limits are a target the user manages on the
 * Budgetten screen, not subtracted here too: doing both would double-count the same money.
 */
object SafeToSpendCalculator {
    data class Result(val safeToSpendTotal: Money, val safeToSpendPerDay: Money, val daysRemaining: Int)

    fun calculate(currentBalance: Money, upcomingCommitments: Money, today: LocalDate): Result {
        val daysRemaining = (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
        val safeTotal = Money((currentBalance.cents - upcomingCommitments.cents).coerceAtLeast(0))
        return Result(
            safeToSpendTotal = safeTotal,
            safeToSpendPerDay = Money(safeTotal.cents / daysRemaining),
            daysRemaining = daysRemaining,
        )
    }
}
