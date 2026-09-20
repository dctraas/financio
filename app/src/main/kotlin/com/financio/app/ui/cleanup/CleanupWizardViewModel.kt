package com.financio.app.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Category
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.MerchantGrouper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanupWizardUiState(
    val loaded: Boolean = false,
    val uncategorizedCount: Int = 0,
    /** Merchant merge suggestions not yet confirmed or dismissed - same combined source (exact-prefix + fuzzy) as Tegenpartijen and Inzicht. */
    val merchantSuggestionCount: Int = 0,
    /** Categories with no transaction and no rule pointing at them - safe to delete without reassigning anything. */
    val unusedCategories: List<Category> = emptyList(),
)

/**
 * "Opschoon-wizard" — a guided pass through cleanup opportunities Financio can already detect on
 * its own, instead of expecting someone to go hunting for them across three separate screens
 * (Transacties' "Zonder categorie" filter, Tegenpartijen, Categorieën & regels). Nothing here acts
 * without an explicit tap — deleting an unused category still goes through the same
 * [CategoryRepository.deleteCategory] Categorieën & regels' own delete flow uses.
 */
@HiltViewModel
class CleanupWizardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<CleanupWizardUiState> = combine(
        transactionRepository.observeAllTransactions(),
        transactionRepository.observeSplitTransactionIds(),
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        combine(appPreferences.confirmedMerchantAliases, appPreferences.dismissedMerchantGroups) { aliases, dismissed -> aliases to dismissed },
    ) { transactions, splitIds, categories, rules, (aliases, dismissedGroups) ->
        // A split transaction's own categoryId is null by design (see TransactionDao.setSplits) -
        // it's already categorized (into several categories), not part of the uncategorized backlog.
        val uncategorizedCount = transactions.count { it.categoryId == null && it.id !in splitIds }

        val counterpartyNames = transactions.map { it.counterpartyName }
        val merchantSuggestionCount = (MerchantGrouper.candidateGroups(counterpartyNames) + MerchantGrouper.fuzzyCandidateGroups(counterpartyNames))
            .count { candidate ->
                candidate.canonicalName !in dismissedGroups &&
                    candidate.rawNames.any { aliases[it] != candidate.canonicalName }
            }

        val usedCategoryIds = transactions.mapNotNull { it.categoryId }.toSet()
        val categoryIdsWithRules = rules.map { it.categoryId }.toSet()
        val unusedCategories = categories.filter { it.id !in usedCategoryIds && it.id !in categoryIdsWithRules }

        CleanupWizardUiState(
            loaded = true,
            uncategorizedCount = uncategorizedCount,
            merchantSuggestionCount = merchantSuggestionCount,
            unusedCategories = unusedCategories,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanupWizardUiState())

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { categoryRepository.deleteCategory(categoryId) }
    }
}
