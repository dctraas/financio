package com.financio.app.ui.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Account
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** One bar in the net worth trend - the total at the end of [label]'s month. */
data class NetWorthPoint(val label: String, val amount: Money, val isCurrent: Boolean)

data class NetWorthUiState(
    val loaded: Boolean = false,
    val hasAnyData: Boolean = false,
    val current: Money = Money.ZERO,
    val accountsTotal: Money = Money.ZERO,
    val savingsTotal: Money = Money.ZERO,
    /** Trailing 6 months, end-of-month totals - the rightmost point is [current]. */
    val points: List<NetWorthPoint> = emptyList(),
    val deltaLabel: String? = null,
    /** Green for a gain, red for a drop - unlike a spend category, "up" is always the good direction for net worth. */
    val deltaIsGood: Boolean = false,
)

/**
 * "Vermogen" = every account's own most recent balance, plus every savings goal's progress - the
 * same two numbers Meer's tiles already surface separately (see MeerViewModel), combined into one
 * figure and, unlike Meer, tracked back 6 months so it reads as a trend rather than a single
 * snapshot. A savings goal's progress lives *outside* any imported account's balance (money that
 * left a checking account for a savings pot Financio was never shown transactions for), so adding
 * it here isn't double-counting - it's the only place that money is visible at all.
 *
 * The trailing months are reconstructed from the same transaction history already loaded for the
 * current total, not a second query: for each month's end, an account's balance is whichever of
 * its own transactions is the most recent one on or before that date (same "date, then id"
 * tie-break the rest of the app uses for same-day ordering), and a goal's progress is its
 * category's net signed total up to that date.
 */
@HiltViewModel
class NetWorthViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    savingsGoalRepository: SavingsGoalRepository,
) : ViewModel() {

    val uiState: StateFlow<NetWorthUiState> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeAllTransactions(),
        savingsGoalRepository.observeGoals(),
    ) { accounts, transactions, goals -> buildState(accounts, transactions, goals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetWorthUiState())

    private fun buildState(accounts: List<Account>, transactions: List<Transaction>, goals: List<SavingsGoal>): NetWorthUiState {
        if (accounts.isEmpty() && goals.isEmpty()) return NetWorthUiState(loaded = true, hasAnyData = false)

        val transactionsByAccount = transactions.groupBy { it.accountId }
        val transactionsByCategory = transactions.groupBy { it.categoryId }
        val months = (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) }

        val points = months.mapIndexed { index, month ->
            val cutoff = month.atEndOfMonth()
            val accountsAsOf = accounts.sumOf { account ->
                transactionsByAccount[account.id]
                    ?.filter { !it.date.isAfter(cutoff) }
                    ?.maxWithOrNull(compareBy<Transaction> { it.date }.thenBy { it.id })
                    ?.balanceAfter?.cents ?: 0L
            }
            val savingsAsOf = goals.sumOf { goal ->
                transactionsByCategory[goal.categoryId]?.filter { !it.date.isAfter(cutoff) }?.sumOf { it.amount.cents } ?: 0L
            }
            NetWorthPoint(labelFor(month), Money(accountsAsOf + savingsAsOf), isCurrent = index == months.lastIndex)
        }

        val current = points.last().amount
        val sixMonthsAgo = points.first().amount
        val diff = Money(current.cents - sixMonthsAgo.cents)
        val deltaLabel = if (points.size > 1) "${diff.absoluteDisplayString()} t.o.v. 6 maanden geleden" else null

        return NetWorthUiState(
            loaded = true,
            hasAnyData = true,
            current = current,
            accountsTotal = Money(accounts.sumOf { account -> transactionsByAccount[account.id]?.maxWithOrNull(compareBy<Transaction> { it.date }.thenBy { it.id })?.balanceAfter?.cents ?: 0L }),
            savingsTotal = Money(goals.sumOf { goal -> transactionsByCategory[goal.categoryId]?.sumOf { it.amount.cents } ?: 0L }),
            points = points,
            deltaLabel = deltaLabel,
            deltaIsGood = diff.cents >= 0,
        )
    }

    private fun labelFor(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", "")
}

private fun Money.absoluteDisplayString(): String = Money(kotlin.math.abs(cents)).toDisplayString()
