package com.financio.app.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.app.notifications.BudgetThresholdNotifier
import com.financio.core.categorize.LearnedRule
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.usecase.ImportPreview
import com.financio.core.usecase.ImportStatementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object PickFile : ImportUiState
    data object Loading : ImportUiState

    /**
     * [manualCategoryChoices] maps a counterparty name — one of [ImportPreview.needsCategoryGrouped]'s
     * groups — to the category the user picked for it, applying to *every* transaction sharing
     * that name in this batch. Kept here rather than mutating [preview] itself so the "X te
     * controleren" count in the summary stays accurate as choices come in. Every transaction in
     * [preview] ends up imported on confirm regardless of whether it got a manual category (see
     * [ImportViewModel.confirm]), so [preview]'s own `total` is the number that will be imported.
     */
    data class Ready(
        val fileName: String,
        val preview: ImportPreview,
        val manualCategoryChoices: Map<String, Long> = emptyMap(),
    ) : ImportUiState

    data class Failed(val message: String) : ImportUiState
    data object Imported : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importStatementUseCase: ImportStatementUseCase,
    private val categoryRepository: CategoryRepository,
    private val budgetThresholdNotifier: BudgetThresholdNotifier,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.PickFile)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which account the next import goes into — only ever surfaced in the UI once a second account exists. */
    private val _selectedAccountId = MutableStateFlow(DefaultAccount.ID)
    val selectedAccountId: StateFlow<Long> = _selectedAccountId.asStateFlow()

    fun selectAccount(accountId: Long) {
        _selectedAccountId.value = accountId
    }

    fun onFilePicked(fileName: String, content: String) {
        _uiState.value = ImportUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val preview = importStatementUseCase.preview(content, _selectedAccountId.value)
                ImportUiState.Ready(fileName, preview)
            } catch (e: Exception) {
                ImportUiState.Failed(e.message ?: "Kon het bestand niet lezen.")
            }
        }
    }

    /** [counterpartyName] is a group key from `preview.needsCategoryGrouped`, applying to every transaction that shares it. */
    fun assignCategory(counterpartyName: String, categoryId: Long) {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        _uiState.value = current.copy(
            manualCategoryChoices = current.manualCategoryChoices + (counterpartyName to categoryId),
        )
    }

    /**
     * Persists the auto-categorized transactions, the ones the user just assigned by hand (which
     * also become a remembered rule, per the architecture's "geen match → vraag het → onthoud
     * het" behavior — one rule per merchant, not per line), and — unlike dropping them — the rest
     * of `needsCategory` too, uncategorized, so nothing an import found silently disappears; they
     * show up as "Te categoriseren" in the transaction list and can be fixed there instead.
     */
    fun confirm() {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        viewModelScope.launch {
            val manuallyCategorized = current.preview.needsCategory.map { transaction ->
                current.manualCategoryChoices[transaction.counterpartyName]?.let { categoryId ->
                    transaction.copy(categoryId = categoryId)
                } ?: transaction
            }
            val toImport = current.preview.ready + manuallyCategorized

            // Snapshotted before the import itself: an import is exactly the moment a budget is
            // most likely to newly cross a threshold, since it can add many transactions to a
            // category at once instead of the one-at-a-time changes Transacties makes.
            val affectedCategoryIds = toImport.mapNotNull { it.categoryId }.distinct()
            val previousSpentByCategory = affectedCategoryIds.associateWith { budgetThresholdNotifier.currentSpent(it) }

            importStatementUseCase.confirm(toImport)

            current.manualCategoryChoices.forEach { (counterpartyName, categoryId) ->
                categoryRepository.addRule(LearnedRule.from(categoryId, counterpartyName))
            }

            affectedCategoryIds.forEach { categoryId ->
                budgetThresholdNotifier.checkAndNotify(categoryId, previousSpentByCategory.getValue(categoryId))
            }

            _uiState.value = ImportUiState.Imported
        }
    }
}
