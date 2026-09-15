package com.financio.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.categorize.ManualRule
import com.financio.core.categorize.RuleMatcher
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
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

data class CategoryManagementUiState(
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
    /** Non-null while the "regels met terugwerkende kracht toepassen" confirmation is open - how many transactions would actually change, computed once on request rather than kept live. */
    val ruleApplicationPreview: Int? = null,
    /** Set right after applying, so the screen can show "X transacties bijgewerkt" once; cleared via [CategoryManagementViewModel.dismissRuleApplicationResult]. */
    val ruleApplicationResult: Int? = null,
)

/** A small rotation, not a picker: keeps "add a category" down to just typing a name. */
private val COLOR_ROTATION = listOf(
    "#5B7A52", "#4C6E77", "#8A4A3D", "#7A6A45", "#6B6485",
    "#4A5A8A", "#3D8A6E", "#9C7A3D", "#3D8FA3", "#A35D82",
)

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    // Own state, not derived from categories/rules: those two combine-source flows can re-emit
    // for unrelated reasons (adding a category while the preview dialog happens to be open, say),
    // which would otherwise reset an in-progress preview/result out from under the dialog.
    private val ruleApplicationPreview = MutableStateFlow<Int?>(null)
    private val ruleApplicationResult = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<CategoryManagementUiState> = combine(
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        ruleApplicationPreview,
        ruleApplicationResult,
    ) { categories, rules, preview, result -> CategoryManagementUiState(categories, rules, preview, result) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManagementUiState())

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val color = COLOR_ROTATION[uiState.value.categories.size % COLOR_ROTATION.size]
            categoryRepository.addCategory(trimmed, color)
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { categoryRepository.deleteCategory(categoryId) }
    }

    fun addRule(categoryId: Long, matchType: MatchType, pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { categoryRepository.addRule(ManualRule.from(categoryId, matchType, trimmed)) }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch { categoryRepository.deleteRule(ruleId) }
    }

    /** Opens the "regels met terugwerkende kracht toepassen" confirmation, with a real count of what it would change. */
    fun requestRuleApplicationPreview() {
        viewModelScope.launch {
            ruleApplicationPreview.value = pendingRuleChanges().size
        }
    }

    fun cancelRuleApplicationPreview() {
        ruleApplicationPreview.value = null
    }

    /** Applies every current rule to every non-split transaction, updating only the ones whose matched category actually differs from what they have now. */
    fun confirmRuleApplication() {
        viewModelScope.launch {
            val changes = pendingRuleChanges()
            changes.forEach { (transactionId, categoryId) -> transactionRepository.updateCategory(transactionId, categoryId) }
            ruleApplicationPreview.value = null
            ruleApplicationResult.value = changes.size
        }
    }

    fun dismissRuleApplicationResult() {
        ruleApplicationResult.value = null
    }

    /**
     * (transactionId, matchedCategoryId) for every transaction that would actually change: a
     * currently-split transaction is skipped entirely (its own categoryId is null by design, and
     * blindly filling that in would silently discard the split), and a transaction whose matched
     * rule agrees with its current category is a no-op, not a "change".
     */
    private suspend fun pendingRuleChanges(): List<Pair<Long, Long>> {
        val matcher = RuleMatcher(uiState.value.rules)
        val transactions = transactionRepository.observeAllTransactions().first()
        val splitIds = transactionRepository.observeSplitTransactionIds().first()
        return transactions.mapNotNull { t ->
            if (t.id in splitIds) return@mapNotNull null
            val matchedCategoryId = matcher.matchingRule(t)?.categoryId ?: return@mapNotNull null
            if (matchedCategoryId == t.categoryId) null else t.id to matchedCategoryId
        }
    }
}
