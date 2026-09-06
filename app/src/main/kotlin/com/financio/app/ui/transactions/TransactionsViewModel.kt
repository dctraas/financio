package com.financio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.categorize.LearnedRule
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.SafeToSpendCalculator
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val safeToSpend: SafeToSpendCalculator.Result? = null,
    /** Ids of transactions that are currently split — see [TransactionRow]'s "Gesplitst" label. */
    val splitTransactionIds: Set<Long> = emptySet(),
    /** Every account that exists — the account filter row is only shown once there's more than one. */
    val accounts: List<Account> = emptyList(),
    /** null = alle rekeningen. */
    val selectedAccountId: Long? = null,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)
    private val sort = MutableStateFlow(TransactionSort.DATE_DESC)

    /** null = alle rekeningen — the default, and the only state a single-account install ever sees. */
    private val selectedAccountId = MutableStateFlow<Long?>(null)

    private val accounts = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val transactionsForSelection = selectedAccountId.flatMapLatest { accountId ->
        if (accountId == null) transactionRepository.observeAllTransactions() else transactionRepository.observeTransactions(accountId)
    }

    // A 6th input flow would need the vararg combine() overload's less readable Array<T> callback,
    // so instead the usual 5-arg combine() is chained with two more via the 3-arg overload.
    private val filteredSnapshot = combine(
        transactionsForSelection,
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
            safeToSpend = safeToSpendFor(transactions, accounts.value.size),
            selectedAccountId = selectedAccountId.value,
        )
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        filteredSnapshot,
        transactionRepository.observeSplitTransactionIds(),
        accounts,
    ) { snapshot, splitIds, accountList ->
        snapshot.copy(splitTransactionIds = splitIds, accounts = accountList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    /**
     * Current balance minus subscriptions expected to bill before the month ends, spread over the
     * days left — see [SafeToSpendCalculator]. `transactions` is already ordered "date DESC, id
     * DESC" by the DAO, so the first row carrying a balance is the most recent one; `null` (no
     * balance data at all, e.g. an MT940 import or a pre-migration transaction) hides the card
     * entirely rather than showing a number computed from a stale or missing balance. Also hidden
     * while viewing "alle rekeningen" with more than one account: adding two accounts' balances
     * together isn't a number that means anything.
     */
    private fun safeToSpendFor(transactions: List<Transaction>, accountCount: Int): SafeToSpendCalculator.Result? {
        if (selectedAccountId.value == null && accountCount > 1) return null
        val currentBalance = transactions.firstNotNullOfOrNull { it.balanceAfter } ?: return null
        val today = LocalDate.now()
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val upcomingCommitments = SubscriptionDetector.detect(transactions)
            .filter { it.estimatedNextDate in today..endOfMonth }
            .sumOf { kotlin.math.abs(it.averageAmount.cents) }
        return SafeToSpendCalculator.calculate(currentBalance, Money(upcomingCommitments), today)
    }

    fun selectAccount(accountId: Long?) {
        selectedAccountId.value = accountId
    }

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
     * Applies [categoryId] to every other already-persisted transaction from [counterpartyName] on
     * [accountId] too — the follow-up prompt after [categorize] offers this so fixing one Albert
     * Heijn line doesn't mean fixing all of them one at a time. No separate rule needed:
     * [categorize] already added one, which is what made this category the "one" in the first
     * place. Scoped to the transaction's own account (not "all accounts"): the same counterparty
     * name on a different account is a coincidence, not necessarily the same real-world merchant
     * relationship — a shared rule (added by [categorize]) already covers that case anyway.
     */
    fun applyCategoryToCounterparty(accountId: Long, counterpartyName: String, categoryId: Long) {
        viewModelScope.launch {
            transactionRepository.updateCategoryForCounterparty(accountId, counterpartyName, categoryId)
        }
    }

    /** The splits currently stored for one transaction — empty for a transaction that isn't split. */
    fun observeSplits(transactionId: Long): Flow<List<TransactionSplit>> =
        transactionRepository.observeSplits(transactionId)

    /**
     * Replaces a transaction's splits entirely; an empty [splits] un-splits it back to
     * [fallbackCategoryId]. [SplitValidation] is enforced by the caller (the dialog disables
     * saving until valid) — this just persists whatever it's handed.
     */
    fun saveSplits(transactionId: Long, splits: List<TransactionSplit>, fallbackCategoryId: Long?) {
        viewModelScope.launch {
            transactionRepository.setSplits(transactionId, splits, fallbackCategoryId)
        }
    }

    private fun matchesSearch(transaction: Transaction, query: String): Boolean =
        query.isBlank() ||
            transaction.counterpartyName.contains(query, ignoreCase = true) ||
            transaction.description.contains(query, ignoreCase = true) ||
            (transaction.tag?.contains(query, ignoreCase = true) ?: false)

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
