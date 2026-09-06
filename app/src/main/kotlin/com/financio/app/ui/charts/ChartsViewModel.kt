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
    /** The rightmost bar's period, e.g. "september 2026" or "2026" — shown next to the ‹ › navigator. */
    val referenceLabel: String = "",
    val canGoToNextPeriod: Boolean = false,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val mode = MutableStateFlow(ChartMode.MONTH_OVER_MONTH)
    // The rightmost bar's period. Defaults to "now"; goToPreviousPeriod/goToNextPeriod shift the
    // whole 6-month/4-year window, which is how you get to see a month or year further back than
    // the fixed trailing window this screen used to show with no way to move it.
    private val referenceMonth = MutableStateFlow(YearMonth.now())
    private val categories = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ChartsUiState> = combine(
        categories, selectedCategoryId, mode, referenceMonth,
    ) { cats, selected, m, anchor ->
        ChartQuery(cats, selected ?: cats.firstOrNull()?.id, m, anchor)
    }.flatMapLatest { query ->
        val categoryId = query.categoryId
        if (categoryId == null) {
            flowOf(ChartsUiState(categories = query.categories))
        } else {
            seriesFor(query.categories, categoryId, query.mode, query.anchor)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun selectMode(newMode: ChartMode) {
        mode.value = newMode
    }

    fun goToPreviousPeriod() {
        referenceMonth.value = shift(referenceMonth.value, mode.value, -1)
    }

    fun goToNextPeriod() {
        val candidate = shift(referenceMonth.value, mode.value, +1)
        if (!candidate.isAfter(YearMonth.now())) referenceMonth.value = candidate
    }

    private fun shift(anchor: YearMonth, m: ChartMode, steps: Long): YearMonth = when (m) {
        ChartMode.MONTH_OVER_MONTH -> anchor.plusMonths(steps)
        ChartMode.YEAR_OVER_YEAR -> anchor.plusYears(steps)
    }

    private data class ChartQuery(
        val categories: List<Category>,
        val categoryId: Long?,
        val mode: ChartMode,
        val anchor: YearMonth,
    )

    private fun seriesFor(cats: List<Category>, categoryId: Long, m: ChartMode, anchor: YearMonth) = run {
        val periods = periodsFor(m, anchor)
        // observeCategoryTotal, not observeSpent: the latter only sums debits (it's "money spent
        // against a budget"), so a category that's all credits - Inkomsten, say - always summed
        // to zero here and its chart looked empty, even though Transacties' filter on that same
        // category showed a full list of matching rows.
        combine(periods.map { period -> transactionRepository.observeCategoryTotal(categoryId, period) }) { amounts ->
            buildState(cats, categoryId, m, anchor, periods, amounts.toList())
        }.flatMapLatest { partial ->
            budgetRepository.observeBudgets(anchor).map { budgets ->
                partial.copy(limit = budgets.firstOrNull { it.categoryId == categoryId }?.limit)
            }
        }
    }

    private fun periodsFor(m: ChartMode, anchor: YearMonth): List<YearMonth> = when (m) {
        ChartMode.MONTH_OVER_MONTH -> (5 downTo 0).map { anchor.minusMonths(it.toLong()) }
        ChartMode.YEAR_OVER_YEAR -> (3 downTo 0).map { anchor.minusYears(it.toLong()) }
    }

    private fun buildState(
        cats: List<Category>,
        categoryId: Long,
        m: ChartMode,
        anchor: YearMonth,
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
            referenceLabel = referenceLabelFor(anchor, m),
            canGoToNextPeriod = anchor.isBefore(YearMonth.now()),
        )
    }

    private fun referenceLabelFor(anchor: YearMonth, m: ChartMode): String = when (m) {
        ChartMode.MONTH_OVER_MONTH ->
            "${anchor.month.getDisplayName(TextStyle.FULL, Locale("nl")).replaceFirstChar { it.uppercase() }} ${anchor.year}"
        ChartMode.YEAR_OVER_YEAR -> anchor.year.toString()
    }

    private fun labelFor(period: YearMonth, m: ChartMode): String = when (m) {
        ChartMode.MONTH_OVER_MONTH -> period.month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", "")
        ChartMode.YEAR_OVER_YEAR -> period.year.toString()
    }
}

private fun Money.absoluteDisplayString(): String = Money(kotlin.math.abs(cents)).toDisplayString()
