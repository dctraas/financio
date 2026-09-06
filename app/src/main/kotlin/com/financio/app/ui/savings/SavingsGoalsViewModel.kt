package com.financio.app.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavingsGoalRow(val goal: SavingsGoal, val category: Category?, val progress: Money) {
    val percentage: Int
        get() = if (goal.targetAmount.cents <= 0) {
            0
        } else {
            ((progress.cents.toDouble() / goal.targetAmount.cents.toDouble()) * 100).toInt().coerceIn(0, 100)
        }
}

data class SavingsGoalsUiState(val rows: List<SavingsGoalRow> = emptyList(), val categories: List<Category> = emptyList())

/**
 * A savings goal's progress reuses [TransactionRepository.observeCategoryNetAllTime] — the same
 * net debit-minus-credit sign convention as [com.financio.core.budget.BudgetEvaluator], just
 * unscoped by month. That's deliberate: "how much have I net moved into this category, ever" is
 * exactly what a goal's progress means, and a later withdrawal (a credit) naturally lowers it
 * again without needing a separate contribution ledger.
 */
@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<SavingsGoalsUiState> = combine(
        savingsGoalRepository.observeGoals(),
        categoryRepository.observeCategories(),
    ) { goals, categories -> goals to categories }
        .flatMapLatest { (goals, categories) -> rowsFor(goals, categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsGoalsUiState())

    private fun rowsFor(goals: List<SavingsGoal>, categories: List<Category>): Flow<SavingsGoalsUiState> =
        if (goals.isEmpty()) {
            flowOf(SavingsGoalsUiState(rows = emptyList(), categories = categories))
        } else {
            val categoriesById = categories.associateBy { it.id }
            combine(
                goals.map { goal ->
                    transactionRepository.observeCategoryNetAllTime(goal.categoryId).map { progress ->
                        SavingsGoalRow(goal = goal, category = categoriesById[goal.categoryId], progress = progress)
                    }
                },
            ) { rows -> SavingsGoalsUiState(rows = rows.toList(), categories = categories) }
        }

    fun addGoal(name: String, targetAmount: Money, categoryId: Long) {
        viewModelScope.launch { savingsGoalRepository.addGoal(name, targetAmount, categoryId) }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch { savingsGoalRepository.deleteGoal(goalId) }
    }
}
