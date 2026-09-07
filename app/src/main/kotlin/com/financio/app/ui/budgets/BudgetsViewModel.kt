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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
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

/** A category with spend this month but no limit set — shown as a dotted "set a limit?" chip. */
data class UnlimitedCategorySpend(val category: Category, val spent: Money)

data class BudgetsUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    /** Rows sorted by urgency: OVER first, then WARNING, then OK — see [BudgetsViewModel.spentRowsFor]. */
    val rows: List<BudgetRow> = emptyList(),
    val unlimitedSpend: List<UnlimitedCategorySpend> = emptyList(),
    val totalSpent: Money = Money.ZERO,
    val totalLimit: Money = Money.ZERO,
    /** The day-of-month fraction (e.g. 0.6 on the 18th of a 30-day month) that the pace tick marks on each bar — null for any month other than the current one, where "how far through the month are we" doesn't apply. */
    val pace: Float? = null,
) {
    val canGoToNextPeriod: Boolean get() = yearMonth.isBefore(YearMonth.now())
    val referenceLabel: String
        get() = (yearMonth.month.getDisplayName(TextStyle.FULL, Locale("nl")) + " " + yearMonth.year).replaceFirstChar { it.uppercase() }
}

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val referenceMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<BudgetsUiState> = referenceMonth.flatMapLatest { month ->
        combine(
            budgetRepository.observeBudgets(month),
            categoryRepository.observeCategories(),
        ) { budgets, categories -> budgets to categories }
            .flatMapLatest { (budgets, categories) -> combinedRowsFor(month, budgets, categories) }
            .map { (rows, unlimited) -> buildState(month, rows, unlimited) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    private fun buildState(month: YearMonth, rows: List<BudgetRow>, unlimited: List<UnlimitedCategorySpend>): BudgetsUiState {
        val sortedRows = rows.sortedWith(compareByDescending<BudgetRow> { it.status.ordinal }.thenByDescending { it.percentage })
        val today = LocalDate.now()
        val pace = if (month == YearMonth.now()) today.dayOfMonth / month.lengthOfMonth().toFloat() else null
        return BudgetsUiState(
            yearMonth = month,
            rows = sortedRows,
            unlimitedSpend = unlimited,
            totalSpent = Money(rows.sumOf { it.spent.cents }),
            totalLimit = Money(rows.sumOf { it.effectiveLimit.cents }),
            pace = pace,
        )
    }

    private fun combinedRowsFor(
        month: YearMonth,
        budgets: List<Budget>,
        categories: List<Category>,
    ): Flow<Pair<List<BudgetRow>, List<UnlimitedCategorySpend>>> {
        val categoriesById = categories.associateBy { it.id }
        val budgetedCategoryIds = budgets.map { it.categoryId }.toSet()
        val unbudgetedCategories = categories.filter { it.id !in budgetedCategoryIds }

        val rowsFlow = if (budgets.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(budgets.map { budget -> rowFlow(month, budget, categoriesById[budget.categoryId]) }) { it.toList() }
        }
        val unlimitedFlow = if (unbudgetedCategories.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(unbudgetedCategories.map { category -> unlimitedSpendFlow(month, category) }) { it.toList() }
        }
        return combine(rowsFlow, unlimitedFlow) { rows, unlimited ->
            rows to unlimited.filter { it.spent.cents != 0L }
        }
    }

    private fun unlimitedSpendFlow(month: YearMonth, category: Category): Flow<UnlimitedCategorySpend> =
        transactionRepository.observeCategorySpent(category.id, month).map { spent -> UnlimitedCategorySpend(category, spent) }

    /**
     * Without rollover this is just spent-vs-own-limit. With it, also pulls last month's budget
     * row (if any) and last month's spend for the same category, so [BudgetEvaluator.effectiveLimit]
     * can add on whatever headroom was left unused — the whole point of the toggle.
     */
    private fun rowFlow(month: YearMonth, budget: Budget, category: Category?): Flow<BudgetRow> {
        val spentFlow = transactionRepository.observeCategorySpent(budget.categoryId, month)
        if (!budget.rollover) {
            return spentFlow.map { spent -> BudgetRow(category, budget, spent, budget.limit) }
        }
        val previousMonth = month.minusMonths(1)
        return combine(
            spentFlow,
            budgetRepository.observeBudgets(previousMonth),
            transactionRepository.observeCategorySpent(budget.categoryId, previousMonth),
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

    fun goToPreviousPeriod() {
        referenceMonth.value = referenceMonth.value.minusMonths(1)
    }

    fun goToNextPeriod() {
        val candidate = referenceMonth.value.plusMonths(1)
        if (!candidate.isAfter(YearMonth.now())) referenceMonth.value = candidate
    }

    /** Sets or replaces a category's limit for the month currently being viewed — see [BudgetLimitDialog]. */
    fun setLimit(categoryId: Long, limit: Money, rollover: Boolean) {
        val month = referenceMonth.value
        viewModelScope.launch {
            budgetRepository.setLimit(categoryId, month, limit)
            budgetRepository.setRollover(categoryId, month, rollover)
        }
    }
}
