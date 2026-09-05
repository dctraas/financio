package com.financio.app.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class ChartMode { MONTH_OVER_MONTH, YEAR_OVER_YEAR }

data class ChartPoint(val label: String, val amount: Money, val isCurrent: Boolean)

data class ChartsUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val mode: ChartMode = ChartMode.MONTH_OVER_MONTH,
    val points: List<ChartPoint> = emptyList(),
    val currentTotal: Money = Money.ZERO,
    val deltaLabel: String? = null,
    val deltaIsIncrease: Boolean = false,
    val limit: Money? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val mode = MutableStateFlow(ChartMode.MONTH_OVER_MONTH)
    private val categories = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ChartsUiState> = combine(categories, selectedCategoryId, mode) { cats, selected, m ->
        Triple(cats, selected ?: cats.firstOrNull()?.id, m)
    }.flatMapLatest { (cats, categoryId, m) ->
        if (categoryId == null) {
            flowOf(ChartsUiState(categories = cats))
        } else {
            seriesFor(cats, categoryId, m)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun selectMode(newMode: ChartMode) {
        mode.value = newMode
    }

    private fun seriesFor(cats: List<Category>, categoryId: Long, m: ChartMode) = run {
        val periods = periodsFor(m)
        combine(periods.map { period -> transactionRepository.observeSpent(categoryId, period) }) { amounts ->
            buildState(cats, categoryId, m, periods, amounts.toList())
        }.flatMapLatest { partial ->
            budgetRepository.observeBudgets(YearMonth.now()).map { budgets ->
                partial.copy(limit = budgets.firstOrNull { it.categoryId == categoryId }?.limit)
            }
        }
    }

    private fun periodsFor(m: ChartMode): List<YearMonth> {
        val now = YearMonth.now()
        return when (m) {
            ChartMode.MONTH_OVER_MONTH -> (5 downTo 0).map { now.minusMonths(it.toLong()) }
            ChartMode.YEAR_OVER_YEAR -> (3 downTo 0).map { now.minusYears(it.toLong()) }
        }
    }

    private fun buildState(
        cats: List<Category>,
        categoryId: Long,
        m: ChartMode,
        periods: List<YearMonth>,
        amounts: List<Money>,
    ): ChartsUiState {
        val points = periods.zip(amounts).mapIndexed { index, (period, amount) ->
            ChartPoint(label = labelFor(period, m), amount = amount, isCurrent = index == periods.lastIndex)
        }
        val current = amounts.lastOrNull() ?: Money.ZERO
        val previous = amounts.getOrNull(amounts.lastIndex - 1)
        val deltaLabel = previous?.let { prev ->
            val diff = Money(current.cents - prev.cents)
            val referencePoint = if (m == ChartMode.MONTH_OVER_MONTH) "vorige maand" else "vorig jaar"
            "${diff.absoluteDisplayString()} t.o.v. $referencePoint"
        }
        return ChartsUiState(
            categories = cats,
            selectedCategoryId = categoryId,
            mode = m,
            points = points,
            currentTotal = current,
            deltaLabel = deltaLabel,
            deltaIsIncrease = previous != null && current.cents > previous.cents,
        )
    }

    private fun labelFor(period: YearMonth, m: ChartMode): String = when (m) {
        ChartMode.MONTH_OVER_MONTH -> period.month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", "")
        ChartMode.YEAR_OVER_YEAR -> period.year.toString()
    }
}

private fun Money.absoluteDisplayString(): String = Money(kotlin.math.abs(cents)).toDisplayString()
