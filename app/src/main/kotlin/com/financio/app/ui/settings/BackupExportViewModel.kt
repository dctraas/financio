package com.financio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.backup.BackupSerializer
import com.financio.core.backup.CategoryImport
import com.financio.core.backup.RuleImport
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupExportUiState(
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
)

sealed interface ImportResult {
    data class Success(
        val categoriesAdded: Int,
        val categoriesSkipped: Int,
        val rulesAdded: Int,
        val rulesSkipped: Int,
    ) : ImportResult
    data class Failed(val message: String) : ImportResult
}

/**
 * Backs the "Back-up & export" section nested inside Meer — the old JSON categories/rules
 * import+export, plus the transactions CSV export the redesign brief calls out as new
 * ("de export van je transacties, die er nu niet is"). Split out from [SettingsViewModel]
 * because it's the only settings sub-screen that needs [TransactionRepository] at all.
 */
@HiltViewModel
class BackupExportViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<BackupExportUiState> = combine(
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        transactionRepository.observeAllTransactions(),
    ) { categories, rules, transactions ->
        BackupExportUiState(categories = categories, rules = rules, transactions = transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupExportUiState())

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    /** Same additive-only plan/apply as [SettingsViewModel.importBackup] — see that doc comment. */
    fun importBackup(content: String) {
        viewModelScope.launch {
            _importResult.value = runCatching {
                val bundle = BackupSerializer.parse(content)

                val categoryPlan = CategoryImport.plan(bundle.categories, categoryRepository.observeCategories().first())
                categoryPlan.toCreate.forEach { categoryRepository.addCategory(it.name, it.colorHex) }

                val categoryIdsByName = categoryRepository.observeCategories().first().associate { it.name to it.id }
                val rulePlan = RuleImport.plan(bundle.rules, categoryIdsByName, categoryRepository.observeRules().first())
                categoryRepository.addRules(rulePlan.toCreate)

                ImportResult.Success(
                    categoriesAdded = categoryPlan.toCreate.size,
                    categoriesSkipped = categoryPlan.skippedExisting,
                    rulesAdded = rulePlan.toCreate.size,
                    rulesSkipped = rulePlan.skippedUnresolvedCategory + rulePlan.skippedDuplicate,
                )
            }.getOrElse { e -> ImportResult.Failed(e.message ?: "Kon het bestand niet lezen.") }
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }
}
