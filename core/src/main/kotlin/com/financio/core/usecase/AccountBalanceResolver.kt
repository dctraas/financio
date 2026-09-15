package com.financio.core.usecase

import com.financio.core.model.Account
import com.financio.core.model.Money
import com.financio.core.model.Transaction

/** An account's balance is either a real number, or genuinely unknown — see [AccountBalanceResolver]. */
sealed interface AccountBalance {
    data class Known(val amount: Money) : AccountBalance
    data object Unknown : AccountBalance
}

/**
 * Resolves one account's current balance without ever silently treating "no data" as €0 - the
 * correction the Rekeningen redesign calls out explicitly: a shared MT940 export with no
 * closing-balance record, or an account with no imports at all yet, has no real balance to show,
 * and folding that into a total as €0 makes every downstream number (this screen's total,
 * "Veilig te besteden") quietly wrong instead of visibly incomplete.
 */
object AccountBalanceResolver {
    /**
     * The most recent transaction (by date, then id as the tie-break the rest of the app uses for
     * same-day ordering) that actually carries a [Transaction.balanceAfter], falling back to the
     * account's own manually-typed balance, falling back to [AccountBalance.Unknown] - never a
     * bare zero standing in for "nothing to go on yet".
     */
    fun resolve(account: Account, transactions: List<Transaction>): AccountBalance {
        val mostRecentKnown = transactions
            .filter { it.accountId == account.id && it.balanceAfter != null }
            .maxWithOrNull(compareBy<Transaction> { it.date }.thenBy { it.id })
            ?.balanceAfter
        return when {
            mostRecentKnown != null -> AccountBalance.Known(mostRecentKnown)
            account.manualBalance != null -> AccountBalance.Known(account.manualBalance)
            else -> AccountBalance.Unknown
        }
    }
}
