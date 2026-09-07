package com.financio.app.usecase

import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.usecase.SafeToSpendCalculator
import com.financio.core.usecase.SubscriptionDetector
import java.time.LocalDate

/**
 * Shared by Transacties (account-filterable) and Vandaag (always "the" account) so the two
 * screens can never quietly disagree about what "veilig te besteden" means. `transactions` is
 * already ordered "date DESC, id DESC" by the DAO wherever this is called from, so the first row
 * carrying a balance is the most recent one.
 */
fun safeToSpendFor(transactions: List<Transaction>, accountCount: Int, singleAccountSelected: Boolean): SafeToSpendCalculator.Result? {
    // Hidden while viewing "alle rekeningen" with more than one account: adding two accounts'
    // balances together isn't a number that means anything.
    if (!singleAccountSelected && accountCount > 1) return null
    val currentBalance = transactions.firstNotNullOfOrNull { it.balanceAfter } ?: return null
    val today = LocalDate.now()
    val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
    val upcomingCommitments = SubscriptionDetector.detect(transactions)
        .filter { it.estimatedNextDate in today..endOfMonth }
        .sumOf { kotlin.math.abs(it.averageAmount.cents) }
    return SafeToSpendCalculator.calculate(currentBalance, Money(upcomingCommitments), today)
}
