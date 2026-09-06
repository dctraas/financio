package com.financio.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.budget.BudgetEvaluator
import com.financio.core.budget.BudgetStatus
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
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
import java.time.YearMonth
import javax.inject.Inject

data class BudgetRow(
    val category: Category?,
    val budget: Budget,
    val spent: Money,
    /** [budget.limit] plus any rolled-over headroom from last month — see [BudgetEvaluator.effectiveLimit]. */
    val effectiveLimit: Money,
) {
    val status: BudgetStatus get() = BudgetEvaluator.evaluate(spent, effectiveLimit)
    val percentage: Int get() = BudgetEvaluator.percentage(spent, effectiveLimit)
}

data class BudgetsUiState(val yearMonth: YearMonth = YearMonth.now(), val rows: List<BudgetRow> = emptyList())

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val yearMonth = YearMonth.now()
    private val previousYearMonth = yearMonth.minusMonths(1)

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetRepository.observeBudgets(yearMonth),
        categoryRepository.observeCategories(),
    ) { budgets, categories -> budgets to categories }
        .flatMapLatest { (budgets, categories) -> spentRowsFor(budgets, categories) }
        .map { rows -> BudgetsUiState(yearMonth = yearMonth, rows = rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState(yearMonth = yearMonth))

    private fun spentRowsFor(budgets: List<Budget>, categories: List<Category>) =
        if (budgets.isEmpty()) {
            flowOf(emptyList())
        } else {
            val categoriesById = categories.associateBy { it.id }
            combine(budgets.map { budget -> rowFlow(budget, categoriesById[budget.categoryId]) }) { rows -> rows.toList() }
        }

    /**
     * Without rollover this is just spent-vs-own-limit. With it, also pulls last month's budget
     * row (if any) and last month's spend for the same category, so [BudgetEvaluator.effectiveLimit]
     * can add on whatever headroom was left unused — the whole point of the toggle.
     */
    private fun rowFlow(budget: Budget, category: Category?): Flow<BudgetRow> {
        val spentFlow = transactionRepository.observeSpent(budget.categoryId, budget.yearMonth)
        if (!budget.rollover) {
            return spentFlow.map { spent -> BudgetRow(category, budget, spent, budget.limit) }
        }
        return combine(
            spentFlow,
            budgetRepository.observeBudgets(previousYearMonth),
            transactionRepository.observeSpent(budget.categoryId, previousYearMonth),
        ) { spent, previousBudgets, previousSpent ->
            val previousBudget = previousBudgets.find { it.categoryId == budget.categoryId }
            val effectiveLimit = BudgetEvaluator.effectiveLimit(
                baseLimit = budget.limit,
                rollover = true,
                previousLimit = previousBudget?.limit,
                previousSpent = previousBudget?.let { previousSpent },
            )
            BudgetRow(category, budget, spent, effectiveLimit)
        }
    }
}
