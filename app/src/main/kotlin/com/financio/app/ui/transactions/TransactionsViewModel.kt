package com.financio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.core.categorize.LearnedRule
import com.financio.core.model.Category
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mirrors the filter chips competing budgeting apps (bunq, YNAB, Buddy) put on their transaction list. */
sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object Uncategorized : CategoryFilter
    data class Specific(val categoryId: Long) : CategoryFilter
}

enum class TransactionSort(val label: String) {
    DATE_DESC("Datum (nieuw → oud)"),
    DATE_ASC("Datum (oud → nieuw)"),
    AMOUNT_DESC("Bedrag (hoog → laag)"),
    AMOUNT_ASC("Bedrag (laag → hoog)"),
}

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val searchQuery: String = "",
    val categoryFilter: CategoryFilter = CategoryFilter.All,
    val sort: TransactionSort = TransactionSort.DATE_DESC,
    /** True when the account has transactions at all but the current filter/search hides all of them. */
    val hasUnfilteredTransactions: Boolean = false,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    private val sort = MutableStateFlow(TransactionSort.DATE_DESC)

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(DefaultAccount.ID),
        categoryRepository.observeCategories(),
        searchQuery,
        categoryFilter,
        sort,
    ) { transactions, categories, query, filter, sortOrder ->
        val filtered = transactions
            .filter { matchesSearch(it, query) }
            .filter { matchesCategoryFilter(it, filter) }
            .sortedWith(comparatorFor(sortOrder))

        TransactionsUiState(
            transactions = filtered,
            categories = categories,
            categoriesById = categories.associateBy { it.id },
            searchQuery = query,
            categoryFilter = filter,
            sort = sortOrder,
            hasUnfilteredTransactions = transactions.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(filter: CategoryFilter) {
        categoryFilter.value = filter
    }

    fun setSort(newSort: TransactionSort) {
        sort.value = newSort
    }

    fun clearFilters() {
        searchQuery.value = ""
        categoryFilter.value = CategoryFilter.All
    }

    /** Same "remember the choice as a rule" behavior as the import screen's manual categorization. */
    fun categorize(transaction: Transaction, categoryId: Long) {
        viewModelScope.launch {
            transactionRepository.updateCategory(transaction.id, categoryId)
            categoryRepository.addRule(LearnedRule.from(categoryId, transaction.counterpartyName))
        }
    }

    /**
     * Applies [categoryId] to every other already-persisted transaction from [counterpartyName]
     * too — the follow-up prompt after [categorize] offers this so fixing one Albert Heijn line
     * doesn't mean fixing all of them one at a time. No separate rule needed: [categorize]
     * already added one, which is what made this category the "one" in the first place.
     */
    fun applyCategoryToCounterparty(counterpartyName: String, categoryId: Long) {
        viewModelScope.launch {
            transactionRepository.updateCategoryForCounterparty(DefaultAccount.ID, counterpartyName, categoryId)
        }
    }

    private fun matchesSearch(transaction: Transaction, query: String): Boolean =
        query.isBlank() ||
            transaction.counterpartyName.contains(query, ignoreCase = true) ||
            transaction.description.contains(query, ignoreCase = true)

    private fun matchesCategoryFilter(transaction: Transaction, filter: CategoryFilter): Boolean = when (filter) {
        CategoryFilter.All -> true
        CategoryFilter.Uncategorized -> transaction.categoryId == null
        is CategoryFilter.Specific -> transaction.categoryId == filter.categoryId
    }

    private fun comparatorFor(sortOrder: TransactionSort): Comparator<Transaction> = when (sortOrder) {
        // date then id as a tiebreaker, matching TransactionDao's own "date DESC, id DESC" order.
        TransactionSort.DATE_DESC -> compareByDescending<Transaction> { it.date }.thenByDescending { it.id }
        TransactionSort.DATE_ASC -> compareBy<Transaction> { it.date }.thenBy { it.id }
        TransactionSort.AMOUNT_DESC -> compareByDescending { it.amount.cents }
        TransactionSort.AMOUNT_ASC -> compareBy { it.amount.cents }
    }
}
