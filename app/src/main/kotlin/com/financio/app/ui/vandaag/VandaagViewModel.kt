package com.financio.app.ui.vandaag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.usecase.safeToSpendFor
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.BalanceForecastCalculator
import com.financio.core.usecase.DetectedSubscription
import com.financio.core.usecase.RecurringIncomeDetector
import com.financio.core.usecase.SafeToSpendCalculator
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class VandaagUiState(
    val loaded: Boolean = false,
    val safeToSpend: SafeToSpendCalculator.Result? = null,
    /** Null when there's no balance to project from (no data yet, or more than one account — see [safeToSpendFor]'s own reasoning). */
    val forecast: BalanceForecastCalculator.Result? = null,
    val uncategorizedCount: Int = 0,
    val subscriptionCount: Int = 0,
    val subscriptionMonthlyTotal: Money = Money.ZERO,
    val nextSubscription: DetectedSubscription? = null,
    val thisWeekTransactions: List<Transaction> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val mostRecentTransactionDate: LocalDate? = null,
    /** True once the most recent transaction is over a week old — Vandaag turns the freshness caption into a tappable import reminder then. */
    val dataIsStale: Boolean = false,
)

/**
 * "The answer, not the data" — every number here is derived from data another screen already
 * computes from (SafeToSpendCalculator, SubscriptionDetector, BalanceForecastCalculator), just
 * rearranged around "how am I doing" instead of around a table.
 */
@HiltViewModel
class VandaagViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<VandaagUiState> = combine(
        transactionRepository.observeAllTransactions(),
        accountRepository.observeAccounts(),
        transactionRepository.observeSplitTransactionIds(),
        categoryRepository.observeCategories(),
    ) { transactions, accounts, splitIds, categories ->
        buildState(transactions, accounts.size, splitIds, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VandaagUiState())

    private fun buildState(transactions: List<Transaction>, accountCount: Int, splitIds: Set<Long>, categories: List<Category>): VandaagUiState {
        val today = LocalDate.now()
        val subscriptions = SubscriptionDetector.detect(transactions)

        // Uncategorized excludes split transactions - a split transaction's own categoryId is
        // null by design (see TransactionDao.setSplits), but it's not "needs a category", it
        // already has several.
        val uncategorizedCount = transactions.count { it.categoryId == null && it.id !in splitIds }

        val weekAgo = today.minusDays(6)
        val thisWeek = transactions
            .filter { it.date in weekAgo..today }
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
            .take(5)

        val mostRecentDate = transactions.maxOfOrNull { it.date }

        // Scoped to what's actually due before month-end, not every confirmed subscription's own
        // average - since SubscriptionDetector now also confirms yearly ones, blindly summing
        // every averageAmount here would count a whole year's charge as if it hit "this month".
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val dueThisMonth = subscriptions.filter { it.estimatedNextDate >= today && !it.estimatedNextDate.isAfter(endOfMonth) }

        return VandaagUiState(
            loaded = true,
            safeToSpend = safeToSpendFor(transactions, accountCount, singleAccountSelected = false),
            forecast = forecastFor(transactions, accountCount, today, subscriptions),
            uncategorizedCount = uncategorizedCount,
            subscriptionCount = dueThisMonth.size,
            subscriptionMonthlyTotal = Money(dueThisMonth.sumOf { kotlin.math.abs(it.averageAmount.cents) }),
            nextSubscription = subscriptions.filter { it.estimatedNextDate >= today }.minByOrNull { it.estimatedNextDate },
            thisWeekTransactions = thisWeek,
            categoriesById = categories.associateBy { it.id },
            mostRecentTransactionDate = mostRecentDate,
            dataIsStale = mostRecentDate != null && mostRecentDate.isBefore(today.minusDays(7)),
        )
    }

    /** Same "more than one account has no single meaningful balance" guard as [safeToSpendFor]. */
    private fun forecastFor(
        transactions: List<Transaction>,
        accountCount: Int,
        today: LocalDate,
        subscriptions: List<DetectedSubscription>,
    ): BalanceForecastCalculator.Result? {
        if (accountCount > 1) return null
        val currentBalance = transactions.firstNotNullOfOrNull { it.balanceAfter } ?: return null
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val upcoming = subscriptions
            .filter { it.estimatedNextDate.isAfter(today) && !it.estimatedNextDate.isAfter(endOfMonth) }
            .map { it.estimatedNextDate to Money(kotlin.math.abs(it.averageAmount.cents)) }
        val upcomingIncome = RecurringIncomeDetector.detect(transactions)
            .filter { it.estimatedNextDate.isAfter(today) && !it.estimatedNextDate.isAfter(endOfMonth) }
            .map { it.estimatedNextDate to it.averageAmount }
        return BalanceForecastCalculator.forecast(
            currentBalance,
            today,
            averageDailySpend(transactions, subscriptions, today),
            upcoming,
            upcomingIncome,
        )
    }

    /**
     * A flat "what a typical day costs" over the last 30 days, subscription charges excluded
     * (those are already layered on separately in [forecastFor], by exact date - counting them
     * here too would double them up).
     */
    private fun averageDailySpend(transactions: List<Transaction>, subscriptions: List<DetectedSubscription>, today: LocalDate): Money {
        val subscriptionCounterparties = subscriptions.map { it.counterpartyName }.toSet()
        val windowStart = today.minusDays(29)
        val totalCents = transactions
            .filter { it.date in windowStart..today && it.amount.cents < 0 && it.counterpartyName !in subscriptionCounterparties }
            .sumOf { kotlin.math.abs(it.amount.cents) }
        return Money(totalCents / 30)
    }
}
