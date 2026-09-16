package com.financio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.notifications.BudgetThresholdNotifier
import com.financio.core.categorize.CounterpartyConflict
import com.financio.core.categorize.LearnedRule
import com.financio.core.categorize.RuleMatcher
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    /** Other transactions sharing this one's account + counterparty — same "ook toepassen op de rest?" trigger count Transacties uses, so the follow-up prompt fires here too, not just from a long-press in the list. */
    val otherTransactionsWithSameCounterparty: Int = 0,
    /** Non-null while the "meerdere soorten transacties bij deze rekeninghouder?" dialog is open — see [CategorizationConflict]. */
    val categorizationConflict: CategorizationConflict? = null,
    /** Non-null right after setCategory() actually persists - the screen turns this into its "ook toepassen op de rest?" follow-up, then clears it via [TransactionDetailViewModel.consumeAppliedCategorization]. */
    val appliedCategorization: AppliedCategorization? = null,
)

/**
 * Backs the new transaction detail screen (R3): everything a tap on a transaction row now opens,
 * instead of the category picker (that moved to a long-press — see TransactionsScreen).
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetThresholdNotifier: BudgetThresholdNotifier,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle["transactionId"])

    private val categorizationConflict = MutableStateFlow<CategorizationConflict?>(null)
    private val appliedCategorization = MutableStateFlow<AppliedCategorization?>(null)

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        transactionRepository.observeAllTransactions(),
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        accountRepository.observeAccounts(),
        combine(transactionRepository.observeSplits(transactionId), categorizationConflict, appliedCategorization) { splits, conflict, applied ->
            Triple(splits, conflict, applied)
        },
    ) { transactions, categories, rules, accounts, extra ->
        val (splits, conflict, applied) = extra
        val transaction = transactions.firstOrNull { it.id == transactionId }
        val categoriesById = categories.associateBy { it.id }
        val otherWithSameCounterparty = transaction?.let { t ->
            transactions.count { it.accountId == t.accountId && it.counterpartyName == t.counterpartyName && it.id != t.id }
        } ?: 0
        TransactionDetailUiState(
            loaded = true,
            transaction = transaction,
            accountName = transaction?.let { t -> accounts.firstOrNull { it.id == t.accountId }?.name },
            categoryName = transaction?.categoryId?.let { categoriesById[it]?.name },
            categories = categories,
            splits = splits.map { split -> categoriesById[split.categoryId] to split.amount },
            counterpartyStats = transaction?.let { counterpartyStatsFor(it, transactions) },
            categorizationConflict = conflict,
            appliedCategorization = applied,
            matchingRule = transaction?.let { RuleMatcher(rules).matchingRule(it) },
            otherTransactionsWithSameCounterparty = otherWithSameCounterparty,
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

    /**
     * Same "remember the choice as a rule" behavior as Transacties' own categorize() - this
     * screen's quick category dropdown shouldn't behave differently just because it's reached via
     * a tap instead of a long-press. Also shares that same conflict check: a counterparty with
     * transactions already in a *different* category elsewhere pauses on [CategorizationConflict]
     * instead of blindly learning a whole-counterparty rule.
     */
    fun setCategory(categoryId: Long) {
        val transaction = uiState.value.transaction ?: return
        viewModelScope.launch {
            val allTransactions = transactionRepository.observeAllTransactions().first()
            val existingCategoryId = CounterpartyConflict.existingDifferentCategory(allTransactions, transaction.counterpartyName, categoryId)
            if (existingCategoryId != null) {
                val existingCategoryName = categoryRepository.observeCategories().first().firstOrNull { it.id == existingCategoryId }?.name
                categorizationConflict.value = CategorizationConflict(transaction, categoryId, existingCategoryName, allTransactions)
                return@launch
            }
            applySetCategory(transaction, categoryId)
        }
    }

    private suspend fun applySetCategory(transaction: Transaction, categoryId: Long) {
        val previousSpent = budgetThresholdNotifier.currentSpent(categoryId)
        transactionRepository.updateCategory(transactionId, categoryId)
        categoryRepository.addRule(LearnedRule.from(categoryId, transaction.counterpartyName))
        budgetThresholdNotifier.checkAndNotify(categoryId, previousSpent)
        appliedCategorization.value = AppliedCategorization(transaction, categoryId)
    }

    fun resolveConflictForAll() {
        val conflict = categorizationConflict.value ?: return
        viewModelScope.launch {
            applySetCategory(conflict.transaction, conflict.categoryId)
            categorizationConflict.value = null
        }
    }

    /** Scopes the learned rule to [keyword] instead of the bare counterparty name - see TransactionsViewModel's identical method for the full rationale. */
    fun resolveConflictWithKeyword(keyword: String) {
        val conflict = categorizationConflict.value ?: return
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val previousSpent = budgetThresholdNotifier.currentSpent(conflict.categoryId)
            transactionRepository.updateCategory(transactionId, conflict.categoryId)
            categoryRepository.addRule(LearnedRule.from(conflict.categoryId, trimmed))
            budgetThresholdNotifier.checkAndNotify(conflict.categoryId, previousSpent)
            categorizationConflict.value = null
        }
    }

    fun cancelConflict() {
        categorizationConflict.value = null
    }

    fun consumeAppliedCategorization() {
        appliedCategorization.value = null
    }

    fun previewConflictKeywordCount(keyword: String): Int {
        val conflict = categorizationConflict.value ?: return 0
        return CounterpartyConflict.matchingKeywordCount(conflict.allTransactionsSnapshot, conflict.transaction.counterpartyName, keyword)
    }

    /** The "ook toepassen op de rest?" follow-up's confirm action — see [otherTransactionsWithSameCounterparty]. */
    fun applyCategoryToCounterparty(categoryId: Long) {
        val transaction = uiState.value.transaction ?: return
        viewModelScope.launch {
            val previousSpent = budgetThresholdNotifier.currentSpent(categoryId)
            transactionRepository.updateCategoryForCounterparty(transaction.accountId, transaction.counterpartyName, categoryId)
            budgetThresholdNotifier.checkAndNotify(categoryId, previousSpent)
        }
    }

    fun setNote(note: String?) {
        viewModelScope.launch { transactionRepository.setNote(transactionId, note?.ifBlank { null }) }
    }
}
