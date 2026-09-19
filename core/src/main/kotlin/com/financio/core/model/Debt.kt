package com.financio.core.model

import java.time.LocalDate

/** Wie aan wie: [I_OWE] is een schuld die ik heb (bijv. bij een familielid of een lening), [OWED_TO_ME] is geld dat iemand anders aan mij verschuldigd is. */
enum class DebtDirection { I_OWE, OWED_TO_ME }

/**
 * A debt or loan, tracked as a running balance the user updates by hand via [DebtRepository.recordPayment]
 * - unlike [SavingsGoal], which derives its progress from real categorized transactions, a debt's
 * counterparty is usually not a bank the app has an import feed for (a family loan, an informal
 * IOU), so there's no transaction history to derive this from automatically.
 */
data class Debt(
    val id: Long = 0,
    val name: String,
    val direction: DebtDirection,
    val counterpartyName: String,
    val principal: Money,
    /** What's left to pay off - starts equal to [principal], moves toward zero as payments are recorded. Never derived; this is the one value [DebtRepository.recordPayment] actually mutates. */
    val currentBalance: Money,
    /** Simple annual rate in basis points (1/100th of a percent), e.g. 350 = 3.50% - display-only, no amortization schedule is computed from it. */
    val interestRateBasisPoints: Int? = null,
    val startDate: LocalDate,
    val targetPayoffDate: LocalDate? = null,
    val notes: String? = null,
    /** "Afbetaald"/"terugbetaald" debts move to their own section and stop counting toward an open-debts total, without losing their history - same archived convention as [SavingsGoal.archived]. */
    val archived: Boolean = false,
) {
    val paidOff: Money get() = principal - currentBalance

    val percentagePaidOff: Int
        get() = if (principal.cents <= 0) 0 else ((paidOff.cents.toDouble() / principal.cents.toDouble()) * 100).toInt().coerceIn(0, 100)

    val settled: Boolean get() = principal.cents > 0 && currentBalance.cents <= 0
}
