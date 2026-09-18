package com.financio.app.ui.categorizequeue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.notifications.BudgetThresholdNotifier
import com.financio.core.categorize.CategorySuggester
import com.financio.core.categorize.CategorySuggestion
import com.financio.core.categorize.CounterpartyConflict
import com.financio.core.categorize.LearnedRule
import com.financio.core.model.Category
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.UncategorizedGroup
import com.financio.core.usecase.groupForReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [allGroups]/[categorizedTransactionsSnapshot] are frozen once at [CategorizeQueueViewModel]
 * construction, same as [com.financio.app.ui.importing.ImportUiState.Ready] freezes its own
 * `preview` for the duration of one categorize session - a queue that kept reshuffling live as
 * transactions changed underneath the player would undermine the whole "groep X van Y, net zolang
 * tot je klaar bent" framing this screen exists for.
 */
data class CategorizeQueueUiState(
    val loaded: Boolean = false,
    val allGroups: List<UncategorizedGroup> = emptyList(),
    val categorizedTransactionsSnapshot: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Groups this session has already assigned or skipped - diffed against [allGroups] for [remainingGroups], same shape as the import flow's own skippedGroups/manualCategoryChoices diff. */
    val resolvedNames: Set<String> = emptySet(),
    val categorizedCount: Int = 0,
    val skippedCount: Int = 0,
) {
    val totalGroups: Int get() = allGroups.size
    val doneCount: Int get() = resolvedNames.size
    val remainingGroups: List<UncategorizedGroup> get() = allGroups.filterNot { it.counterpartyName in resolvedNames }
}

/**
 * The "spelletje" version of categorizing the already-imported backlog (Transacties' "Zonder
 * categorie" filter, or Vandaag's "Nu doen" tile) — the same one-group-at-a-time swipe/suggest/
 * confirm mechanic as the import flow's own categorize screen (see
 * [com.financio.app.ui.common.CategorizeCard]), but acting directly on already-persisted
 * transactions instead of staging choices for one big confirm() at the end, since these
 * transactions already exist in the database.
 */
@HiltViewModel
class CategorizeQueueViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetThresholdNotifier: BudgetThresholdNotifier,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategorizeQueueUiState())
    val uiState: StateFlow<CategorizeQueueUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val transactions = transactionRepository.observeAllTransactions().first()
            val categories = categoryRepository.observeCategories().first()
            _uiState.value = CategorizeQueueUiState(
                loaded = true,
                allGroups = transactions.filter { it.categoryId == null }.groupForReview(),
                categorizedTransactionsSnapshot = transactions.filter { it.categoryId != null },
                categories = categories,
            )
        }
    }

    /** Live "waar hoort dit bij" ranking for [counterpartyName] - pure computation over the frozen snapshot, called straight from Compose per card. */
    fun suggestCategories(counterpartyName: String): List<CategorySuggestion> =
        CategorySuggester.rank(_uiState.value.categorizedTransactionsSnapshot, counterpartyName)

    /**
     * Persists [categoryId] to every transaction in [group] - always, since the player explicitly
     * reviewed exactly this batch. A whole-counterparty rule is only learned when [learnRule] is
     * on AND this counterparty doesn't already mean something else elsewhere (see
     * [CounterpartyConflict]) - unlike Transacties' own [com.financio.app.ui.transactions.TransactionsViewModel.categorize],
     * a conflict here doesn't pause the game with a dialog, it just quietly skips learning a rule
     * that would wrongly capture the other, differently-categorized series too; the categorization
     * the player chose for this reviewed batch still goes through either way.
     */
    fun assign(group: UncategorizedGroup, categoryId: Long, learnRule: Boolean) {
        viewModelScope.launch {
            val allTransactions = transactionRepository.observeAllTransactions().first()
            val conflict = CounterpartyConflict.existingDifferentCategory(allTransactions, group.counterpartyName, categoryId)
            val previousSpent = budgetThresholdNotifier.currentSpent(categoryId)
            group.transactions.forEach { transaction -> transactionRepository.updateCategory(transaction.id, categoryId) }
            if (learnRule && conflict == null) {
                categoryRepository.addRule(LearnedRule.from(categoryId, group.counterpartyName))
            }
            budgetThresholdNotifier.checkAndNotify(categoryId, previousSpent)
            markResolved(group.counterpartyName, categorized = true)
        }
    }

    /** Moves the queue past this group without categorizing it - it stays uncategorized and will show up again next time the queue is opened, same as the import flow's own skip. */
    fun skip(group: UncategorizedGroup) {
        markResolved(group.counterpartyName, categorized = false)
    }

    private fun markResolved(counterpartyName: String, categorized: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            resolvedNames = current.resolvedNames + counterpartyName,
            categorizedCount = current.categorizedCount + if (categorized) 1 else 0,
            skippedCount = current.skippedCount + if (categorized) 0 else 1,
        )
    }
}
