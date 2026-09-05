package com.financio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    fun setBiometricLockEnabled(enabled: Boolean) {
        appPreferences.setBiometricLockEnabled(enabled)
    }

    /** Called once the user finishes editing a limit field — not on every keystroke. */
    fun setLimit(categoryId: Long, limit: Money) {
        viewModelScope.launch { budgetRepository.setLimit(categoryId, currentMonth, limit) }
    }
}
