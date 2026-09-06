package com.financio.core.usecase

import com.financio.core.model.Money

/**
 * A split only makes sense if the pieces add up to the whole — checked here so both the UI and
 * the repository layer can refuse an inconsistent split with the same rule, rather than silently
 * storing an allocation that doesn't reconcile with the transaction's actual amount.
 */
object SplitValidation {
    data class Result(val isValid: Boolean, val difference: Money)

    fun validate(totalAmount: Money, splitAmounts: List<Money>): Result {
        val sum = Money(splitAmounts.sumOf { it.cents })
        val difference = Money(totalAmount.cents - sum.cents)
        return Result(isValid = splitAmounts.size >= 2 && difference.cents == 0L, difference)
    }
}
