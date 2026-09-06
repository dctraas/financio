package com.financio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.backup.BackupSerializer
import com.financio.core.backup.CategoryImport
import com.financio.core.backup.RuleImport
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class SettingsUiState(
    val biometricLockEnabled: Boolean = true,
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
    val limitsByCategory: Map<Long, Money> = emptyMap(),
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val currentMonth = YearMonth.now()

    val uiState: StateFlow<SettingsUiState> = combine(
        appPreferences.biometricLockEnabled,
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        budgetRepository.observeBudgets(currentMonth),
    ) { lockEnabled, categories, rules, budgets ->
        SettingsUiState(
            biometricLockEnabled = lockEnabled,
            categories = categories,
            rules = rules,
            limitsByCategory = budgets.associate { it.categoryId to it.limit },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    fun setBiometricLockEnabled(enabled: Boolean) {
        appPreferences.setBiometricLockEnabled(enabled)
    }

    /** Called once the user finishes editing a limit field — not on every keystroke. */
    fun setLimit(categoryId: Long, limit: Money) {
        viewModelScope.launch { budgetRepository.setLimit(categoryId, currentMonth, limit) }
    }

    /**
     * Parses and applies a categories/rules export. Additive only, per [CategoryImport] and
     * [RuleImport]: never renames/recolors an existing category or overwrites an existing rule's
     * priority, only adds what's genuinely new. Categories are created (and re-fetched) before
     * rules are resolved, so a rule pointing at a category this same import just created still
     * matches correctly.
     */
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
