package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.Transaction
import java.time.LocalDate

/**
 * One merchant's worth of transactions that still need a category, bundled so the import review
 * screen asks once per merchant instead of once per line. For a busy account that's the
 * difference between a handful of decisions and hundreds of identical ones — the single biggest
 * usability problem with a line-by-line review queue (see the README's categorization UX notes).
 */
data class UncategorizedGroup(
    val counterpartyName: String,
    val transactions: List<Transaction>,
) {
    init {
        require(transactions.isNotEmpty()) { "Een groep zonder transacties heeft geen zin." }
    }

    val count: Int get() = transactions.size

    /** Net of all amounts in the group — the number that actually shows up in a budget total. */
    val totalAmount: Money get() = Money(transactions.sumOf { it.amount.cents })

    val minAmount: Money get() = transactions.minOf { it.amount }
    val maxAmount: Money get() = transactions.maxOf { it.amount }

    val firstDate: LocalDate get() = transactions.minOf { it.date }
    val lastDate: LocalDate get() = transactions.maxOf { it.date }
}

/**
 * Groups by counterparty name and sorts by financial impact (total absolute amount) descending —
 * the merchant that moves the month's numbers most is worth the user's attention first, the same
 * priority order budgeting apps like YNAB use for their own transaction review queues.
 */
fun List<Transaction>.groupForReview(): List<UncategorizedGroup> =
    groupBy { it.counterpartyName }
        .map { (name, transactions) -> UncategorizedGroup(name, transactions) }
        .sortedByDescending { kotlin.math.abs(it.totalAmount.cents) }
