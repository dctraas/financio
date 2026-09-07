package com.financio.app.ui.meer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Money
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class MeerUiState(
    val subscriptionCount: Int = 0,
    val subscriptionMonthlyTotal: Money = Money.ZERO,
    val savingsGoalCount: Int = 0,
    val savingsTotalSaved: Money = Money.ZERO,
    val accountCount: Int = 0,
    val accountsTotalBalance: Money = Money.ZERO,
    val categoryCount: Int = 0,
    val ruleCount: Int = 0,
    val mostRecentTransactionDate: LocalDate? = null,
)

/**
 * Every number on Meer's four tiles and its import row is a live summary, not a static label — the
 * whole point ("de vier tegels dragen hun eigen samenvatting") is that you often don't need to tap
 * through at all. Each figure reuses a repository/use case another screen already depends on; this
 * ViewModel only combines them.
 */
@HiltViewModel
class MeerViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    savingsGoalRepository: SavingsGoalRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<MeerUiState> = combine(
        transactionRepository.observeAllTransactions(),
        savingsGoalsSummary(savingsGoalRepository, transactionRepository),
        accountsTotalBalance(accountRepository, transactionRepository),
        combine(categoryRepository.observeCategories(), categoryRepository.observeRules()) { cats, rules -> cats.size to rules.size },
    ) { transactions, savings, accounts, categoryCounts ->
        val subscriptions = SubscriptionDetector.detect(transactions)
        MeerUiState(
            subscriptionCount = subscriptions.size,
            subscriptionMonthlyTotal = Money(subscriptions.sumOf { kotlin.math.abs(it.averageAmount.cents) }),
            savingsGoalCount = savings.first,
            savingsTotalSaved = savings.second,
            accountCount = accounts.first,
            accountsTotalBalance = accounts.second,
            categoryCount = categoryCounts.first,
            ruleCount = categoryCounts.second,
            mostRecentTransactionDate = transactions.maxOfOrNull { it.date },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeerUiState())

    private fun savingsGoalsSummary(
        savingsGoalRepository: SavingsGoalRepository,
        transactionRepository: TransactionRepository,
    ): Flow<Pair<Int, Money>> =
        savingsGoalRepository.observeGoals().flatMapLatest { goals ->
            if (goals.isEmpty()) {
                flowOf(0 to Money.ZERO)
            } else {
                combine(goals.map { transactionRepository.observeCategoryNetAllTime(it.categoryId) }) { progresses ->
                    goals.size to Money(progresses.sumOf { it.cents })
                }
            }
        }

    /**
     * One account's balance is whatever its most recent transaction's [com.financio.core.model.Transaction.balanceAfter]
     * says — the same "first hit walking date-DESC order" approximation Grafieken's Saldoverloop
     * uses, since ING's CSV carries no time-of-day (see `ChartsViewModel.balanceHistoryState`).
     */
    private fun accountsTotalBalance(
        accountRepository: AccountRepository,
        transactionRepository: TransactionRepository,
    ): Flow<Pair<Int, Money>> =
        accountRepository.observeAccounts().flatMapLatest { accounts ->
            if (accounts.isEmpty()) {
                flowOf(0 to Money.ZERO)
            } else {
                combine(accounts.map { account -> transactionRepository.observeTransactions(account.id) }) { perAccount ->
                    val total = perAccount.sumOf { transactions -> transactions.firstNotNullOfOrNull { it.balanceAfter }?.cents ?: 0L }
                    accounts.size to Money(total)
                }
            }
        }
}
