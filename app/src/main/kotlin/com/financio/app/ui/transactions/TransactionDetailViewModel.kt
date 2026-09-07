package com.financio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.categorize.RuleMatcher
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Bij Albert Heijn: 84 transacties, gemiddeld €41,80" — same counterparty, same account. */
data class CounterpartyStats(val count: Int, val average: Money)

data class TransactionDetailUiState(
    val loaded: Boolean = false,
    /** Null only while [loaded] is still false, or if the transaction was deleted from under this screen. */
    val transaction: Transaction? = null,
    val accountName: String? = null,
    val categoryName: String? = null,
    val categories: List<Category> = emptyList(),
    val splits: List<Pair<Category?, Money>> = emptyList(),
    val counterpartyStats: CounterpartyStats? = null,
    val matchingRule: CategoryRule? = null,
)

/**
 * Backs the new transaction detail screen (R3): everything a tap on a transaction row now opens,
 * instead of the category picker (that moved to a long-press — see TransactionsScreen).
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle["transactionId"])

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        transactionRepository.observeAllTransactions(),
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        accountRepository.observeAccounts(),
        transactionRepository.observeSplits(transactionId),
    ) { transactions, categories, rules, accounts, splits ->
        val transaction = transactions.firstOrNull { it.id == transactionId }
        val categoriesById = categories.associateBy { it.id }
        TransactionDetailUiState(
            loaded = true,
            transaction = transaction,
            accountName = transaction?.let { t -> accounts.firstOrNull { it.id == t.accountId }?.name },
            categoryName = transaction?.categoryId?.let { categoriesById[it]?.name },
            categories = categories,
            splits = splits.map { split -> categoriesById[split.categoryId] to split.amount },
            counterpartyStats = transaction?.let { counterpartyStatsFor(it, transactions) },
            matchingRule = transaction?.let { RuleMatcher(rules).matchingRule(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailUiState())

    private fun counterpartyStatsFor(transaction: Transaction, allTransactions: List<Transaction>): CounterpartyStats? {
        val sameCounterparty = allTransactions.filter {
            it.accountId == transaction.accountId && it.counterpartyName == transaction.counterpartyName
        }
        if (sameCounterparty.size <= 1) return null
        val averageCents = sameCounterparty.sumOf { kotlin.math.abs(it.amount.cents) } / sameCounterparty.size
        return CounterpartyStats(count = sameCounterparty.size, average = Money(averageCents))
    }

    fun setCategory(categoryId: Long) {
        viewModelScope.launch { transactionRepository.updateCategory(transactionId, categoryId) }
    }

    fun setNote(note: String?) {
        viewModelScope.launch { transactionRepository.setNote(transactionId, note?.ifBlank { null }) }
    }
}
