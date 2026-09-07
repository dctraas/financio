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

data class ChartPoint(val label: String, val amount: Money, val isCurrent: Boolean, val period: YearMonth)

/** One category's spend for the overview's donut + top-4 — see [ChartsViewModel.overviewFor]. */
data class CategorySpend(val category: Category, val spent: Money)

/** Categories whose direction of change is good when it goes *up*, not down — same name-based special-casing [com.financio.app.ui.common.categoryColorFor] already does for these two. */
private val positiveDirectionCategories = setOf("inkomsten", "sparen")

data class ChartsUiState(
    val categories: List<Category> = emptyList(),
    /** Null = the overview (donut + top-4) — see [overviewSpends]. Non-null = one category's own trend. */
    val selectedCategoryId: Long? = null,
    val mode: ChartMode = ChartMode.MONTH_OVER_MONTH,
    val points: List<ChartPoint> = emptyList(),
    val currentTotal: Money = Money.ZERO,
    val deltaLabel: String? = null,
    /** Whether [deltaLabel]'s direction is good news - green for a spending category going down, but also green for Inkomsten/Sparen going *up*. Replaces a plain "is this bigger than before" check, which colored every increase red regardless of what the category even was. */
    val deltaIsGood: Boolean = false,
    val limit: Money? = null,
    /** The flat average across every bar currently shown - drawn as its own reference line alongside the limit line. */
    val average: Money? = null,
    /** The rightmost bar's period, e.g. "september 2026" or "2026" — shown next to the ‹ › navigator. */
    val referenceLabel: String = "",
    val canGoToNextPeriod: Boolean = false,
    /** Every category with nonzero spend this month, sorted by spend descending — the overview's donut and top-4 list. Only populated when [selectedCategoryId] is null. */
    val overviewSpends: List<CategorySpend> = emptyList(),
    /** "62% van je inkomen" - null when there's no "Inkomsten" category or it has no spend yet this month to divide by. */
    val incomeRatioLabel: String? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    /** Null = overview (donut + top-4). Deliberately never auto-resolved to "the first category" any more - the overview is a real default view now, not a placeholder before one gets picked. */
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
    ) { cats, selected, m, anchor -> ChartQuery(cats, selected, m, anchor) }
        .flatMapLatest { query ->
            val categoryId = query.categoryId
            if (categoryId == null) {
                overviewFor(query.categories, query.anchor)
            } else {
                seriesFor(query.categories, categoryId, query.mode, query.anchor)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    /** Back to the overview - the "Overzicht" chip, or after picking a donut segment once already viewing one category's trend. */
    fun clearCategorySelection() {
        selectedCategoryId.value = null
    }

    fun selectMode(newMode: ChartMode) {
        mode.value = newMode
    }

    fun goToPreviousPeriod() {
        referenceMonth.value = shift(referenceMonth.value, mode.value, -1)
    }

    fun goToNextPeriod() {
        moveTo(shift(referenceMonth.value, mode.value, +1))
    }

    /** Tapping a bar re-centers the whole trailing window on that bar's own period, making it the new rightmost one. */
    fun goToPeriod(period: YearMonth) {
        moveTo(period)
    }

    /** Never lets the anchor move past "now" — a tapped bar is already ≤ now, but goToNextPeriod's step might not be. */
    private fun moveTo(candidate: YearMonth) {
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

    /**
     * The default view once no category is explicitly picked: every category's spend this month,
     * for the donut and its top-4 list, plus the spent-vs-income ratio. Always scoped to one
     * month regardless of the month/year trend toggle - a donut is a snapshot, not a trend, so
     * there's nothing for "jaar-op-jaar" to mean here (the toggle itself is hidden in this view,
     * see ChartsScreen).
     */
    private fun overviewFor(cats: List<Category>, anchor: YearMonth) =
        if (cats.isEmpty()) {
            flowOf(overviewState(cats, anchor, emptyList()))
        } else {
            combine(
                cats.map { cat -> transactionRepository.observeCategorySpent(cat.id, anchor).map { spent -> CategorySpend(cat, spent) } },
            ) { spends -> overviewState(cats, anchor, spends.toList()) }
        }

    private fun overviewState(cats: List<Category>, anchor: YearMonth, spends: List<CategorySpend>): ChartsUiState {
        val nonZero = spends.filter { it.spent.cents > 0 }.sortedByDescending { it.spent.cents }
        val income = spends.firstOrNull { it.category.name.equals("Inkomsten", ignoreCase = true) }?.spent
        val expenseTotal = nonZero
            .filterNot { it.category.name.equals("Inkomsten", ignoreCase = true) }
            .sumOf { it.spent.cents }
        val incomeRatioLabel = income?.cents?.takeIf { it > 0 }?.let { incomeCents ->
            "${expenseTotal * 100 / incomeCents}% van je inkomen"
        }
        return ChartsUiState(
            categories = cats,
            overviewSpends = nonZero,
            incomeRatioLabel = incomeRatioLabel,
            referenceLabel = referenceLabelFor(anchor, ChartMode.MONTH_OVER_MONTH),
            canGoToNextPeriod = anchor.isBefore(YearMonth.now()),
        )
    }

    private fun seriesFor(cats: List<Category>, categoryId: Long, m: ChartMode, anchor: YearMonth) = run {
        val periods = periodsFor(m, anchor)
        // observeCategorySpent - the same "spent" number Budget's progress bars use, see that
        // method's doc comment for why a single unified calculation replaced the two this screen
        // and Budget each used to compute separately.
        combine(periods.map { period -> transactionRepository.observeCategorySpent(categoryId, period) }) { amounts ->
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
            ChartPoint(label = labelFor(period, m), amount = amount, isCurrent = index == periods.lastIndex, period = period)
        }
        val current = amounts.lastOrNull() ?: Money.ZERO
        val previous = amounts.getOrNull(amounts.lastIndex - 1)
        val categoryName = cats.firstOrNull { it.id == categoryId }?.name
        val isPositiveDirection = categoryName != null && categoryName.lowercase() in positiveDirectionCategories
        val deltaLabel = previous?.let { prev ->
            val diff = Money(current.cents - prev.cents)
            val referencePoint = if (m == ChartMode.MONTH_OVER_MONTH) "vorige maand" else "vorig jaar"
            "${diff.absoluteDisplayString()} t.o.v. $referencePoint"
        }
        val isIncrease = previous != null && current.cents > previous.cents
        return ChartsUiState(
            categories = cats,
            selectedCategoryId = categoryId,
            mode = m,
            points = points,
            currentTotal = current,
            deltaLabel = deltaLabel,
            deltaIsGood = previous != null && (isIncrease == isPositiveDirection),
            average = if (amounts.isNotEmpty()) Money(amounts.sumOf { it.cents } / amounts.size) else null,
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
