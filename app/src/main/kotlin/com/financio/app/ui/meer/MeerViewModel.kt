package com.financio.app.ui.meer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Money
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.AccountBalance
import com.financio.core.usecase.AccountBalanceResolver
import com.financio.core.usecase.SubscriptionCadence
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
import java.time.YearMonth
import javax.inject.Inject

data class MeerUiState(
    val subscriptionCount: Int = 0,
    val subscriptionMonthlyTotal: Money = Money.ZERO,
    val savingsGoalCount: Int = 0,
    val savingsTotalSaved: Money = Money.ZERO,
    val budgetCount: Int = 0,
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
    budgetRepository: BudgetRepository,
) : ViewModel() {

    val uiState: StateFlow<MeerUiState> = combine(
        transactionRepository.observeAllTransactions(),
        savingsGoalsSummary(savingsGoalRepository, transactionRepository),
        accountRepository.observeAccounts(),
        combine(categoryRepository.observeCategories(), categoryRepository.observeRules()) { cats, rules -> cats.size to rules.size },
        budgetRepository.observeBudgets(YearMonth.now()),
    ) { transactions, savings, accounts, categoryCounts, budgets ->
        val subscriptions = SubscriptionDetector.detect(transactions)
        // Amortized to a monthly-equivalent figure per subscription (÷12 for a yearly one) - since
        // SubscriptionDetector now also confirms yearly subscriptions, summing their full
        // averageAmount here would badly overstate this tile's "€X/mnd" for anyone with one.
        val monthlyEquivalentTotal = subscriptions.sumOf { subscription ->
            val cents = kotlin.math.abs(subscription.averageAmount.cents)
            if (subscription.cadence == SubscriptionCadence.YEARLY) cents / 12 else cents
        }
        // Same definition as Rekeningen's own total: a hidden or excluded account never
        // contributes, and an Unknown balance (no closing balance in the import at all, and no
        // manual override yet) contributes nothing rather than silently counting as €0 - see
        // AccountBalanceResolver.
        val countedBalances = accounts
            .filter { !it.hidden && !it.excludedFromTotal }
            .mapNotNull { account -> (AccountBalanceResolver.resolve(account, transactions) as? AccountBalance.Known)?.amount }
        MeerUiState(
            subscriptionCount = subscriptions.size,
            subscriptionMonthlyTotal = Money(monthlyEquivalentTotal),
            savingsGoalCount = savings.first,
            savingsTotalSaved = savings.second,
            budgetCount = budgets.size,
            accountCount = accounts.size,
            accountsTotalBalance = Money(countedBalances.sumOf { it.cents }),
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
}
