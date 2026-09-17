package com.financio.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Account
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.AccountBalance
import com.financio.core.usecase.AccountBalanceResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

/** One account's own row - its resolved balance (never a bare zero standing in for "unknown"), transaction count, and how far its import coverage reaches. */
data class AccountRow(
    val account: Account,
    val balance: AccountBalance,
    val transactionCount: Int,
    val coverageStart: LocalDate?,
    val coverageEnd: LocalDate?,
    /** Whole months between this account's most recent transaction and today - 0 while it's current. */
    val monthsBehind: Int,
    /** Name of the (first) goal following this account, if any - "gevolgd door Buffer". Purely informational; see SavingsGoalsScreen for the actual link. */
    val followedByGoalName: String?,
)

data class AccountsUiState(
    val loaded: Boolean = false,
    val visibleAccounts: List<AccountRow> = emptyList(),
    /** Behind a "X verborgen rekeningen tonen" link — hiding an account is never a one-way trap. */
    val hiddenAccounts: List<AccountRow> = emptyList(),
    /** Sum of every [AccountBalance.Known], non-excluded account - never silently includes an Unknown one as €0. */
    val total: Money = Money.ZERO,
    val includedCount: Int = 0,
    val excludedCount: Int = 0,
)

/**
 * Multiple accounts, still entirely local: fase 1 assumed exactly one ING betaalrekening (see
 * [com.financio.app.DefaultAccount]); this lets a second (or third) account exist by importing
 * its own separate CSV/MT940 export into it — no bank connection, no aggregator, none of the
 * fase-3 architecture step this whole batch was scoped to avoid needing.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    savingsGoalRepository: SavingsGoalRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeAllTransactions(),
        savingsGoalRepository.observeGoals(),
    ) { accounts, transactions, goals -> buildState(accounts, transactions, goals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    private fun buildState(accounts: List<Account>, transactions: List<Transaction>, goals: List<SavingsGoal>): AccountsUiState {
        val transactionsByAccount = transactions.groupBy { it.accountId }
        val followingGoalNameByAccountId = goals.mapNotNull { goal -> goal.linkedAccountId?.let { it to goal.name } }.toMap()
        val today = LocalDate.now()

        fun rowFor(account: Account): AccountRow {
            val accountTransactions = transactionsByAccount[account.id].orEmpty()
            val coverageStart = accountTransactions.minOfOrNull { it.date }
            val coverageEnd = accountTransactions.maxOfOrNull { it.date }
            return AccountRow(
                account = account,
                balance = AccountBalanceResolver.resolve(account, transactions),
                transactionCount = accountTransactions.size,
                coverageStart = coverageStart,
                coverageEnd = coverageEnd,
                monthsBehind = coverageEnd?.let { Period.between(it.withDayOfMonth(1), today.withDayOfMonth(1)).let { p -> p.years * 12 + p.months } } ?: 0,
                followedByGoalName = followingGoalNameByAccountId[account.id],
            )
        }

        val (hidden, visible) = accounts.partition { it.hidden }
        val visibleRows = visible.map(::rowFor)

        val includedKnown = visibleRows.filter { !it.account.excludedFromTotal }.mapNotNull { (it.balance as? AccountBalance.Known)?.amount }
        return AccountsUiState(
            loaded = true,
            visibleAccounts = visibleRows,
            hiddenAccounts = hidden.map(::rowFor),
            total = Money(includedKnown.sumOf { it.cents }),
            includedCount = includedKnown.size,
            excludedCount = visibleRows.count { it.account.excludedFromTotal },
        )
    }

    fun addAccount(name: String, ibanMasked: String) {
        viewModelScope.launch { accountRepository.addAccount(name, ibanMasked) }
    }

    fun renameAccount(accountId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { accountRepository.renameAccount(accountId, trimmed) }
    }

    fun setHidden(accountId: Long, hidden: Boolean) {
        viewModelScope.launch { accountRepository.setAccountHidden(accountId, hidden) }
    }

    fun setExcludedFromTotal(accountId: Long, excluded: Boolean) {
        viewModelScope.launch { accountRepository.setAccountExcludedFromTotal(accountId, excluded) }
    }

    fun setManualBalance(accountId: Long, balance: Money?) {
        viewModelScope.launch { accountRepository.setManualBalance(accountId, balance) }
    }
}
