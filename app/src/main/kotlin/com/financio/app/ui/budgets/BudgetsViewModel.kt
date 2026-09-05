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
) {
    val status: BudgetStatus get() = BudgetEvaluator.evaluate(spent, budget.limit)
    val percentage: Int get() = BudgetEvaluator.percentage(spent, budget.limit)
}

data class BudgetsUiState(val yearMonth: YearMonth = YearMonth.now(), val rows: List<BudgetRow> = emptyList())

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val yearMonth = YearMonth.now()

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
            combine(
                budgets.map { budget ->
                    transactionRepository.observeSpent(budget.categoryId, budget.yearMonth).map { spent ->
                        BudgetRow(category = categoriesById[budget.categoryId], budget = budget, spent = spent)
                    }
                },
            ) { rows -> rows.toList() }
        }
}
