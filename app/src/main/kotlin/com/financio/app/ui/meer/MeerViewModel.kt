package com.financio.app.ui.meer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Money
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.AccountBalance
import com.financio.core.usecase.AccountBalanceResolver
import com.financio.core.usecase.SubscriptionCadence
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class MeerUiState(
    val subscriptionCount: Int = 0,
    val subscriptionMonthlyTotal: Money = Money.ZERO,
    val accountCount: Int = 0,
    val accountsTotalBalance: Money = Money.ZERO,
    val categoryCount: Int = 0,
    val ruleCount: Int = 0,
    /** Distinct counterparty names ever seen - "Winkels & tegenpartijen"'s own "86 namen". */
    val merchantNameCount: Int = 0,
    /** Raw names already folded into a confirmed tegenpartij group (see AppPreferences.confirmedMerchantAliases) - "9 samengevoegd". */
    val mergedMerchantCount: Int = 0,
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
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<MeerUiState> = combine(
        transactionRepository.observeAllTransactions(),
        accountRepository.observeAccounts(),
        combine(categoryRepository.observeCategories(), categoryRepository.observeRules()) { cats, rules -> cats.size to rules.size },
        appPreferences.confirmedMerchantAliases,
    ) { transactions, accounts, categoryCounts, merchantAliases ->
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
            accountCount = accounts.size,
            accountsTotalBalance = Money(countedBalances.sumOf { it.cents }),
            categoryCount = categoryCounts.first,
            ruleCount = categoryCounts.second,
            merchantNameCount = transactions.map { it.counterpartyName }.distinct().size,
            mergedMerchantCount = merchantAliases.size,
            mostRecentTransactionDate = transactions.maxOfOrNull { it.date },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeerUiState())
}
