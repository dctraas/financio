package com.financio.app.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.SubscriptionDetector
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

enum class ChartMode { MONTH_OVER_MONTH, YEAR_OVER_YEAR }

data class ChartPoint(val label: String, val amount: Money, val isCurrent: Boolean, val period: YearMonth)

/**
 * One category's spend for the overview's donut + top-4 — see [ChartsViewModel.overviewFor].
 * [trailingAverage] and [isAnomaly] exist purely to flag "opvallend hoog" on the overview list and
 * feed [SavingsTip]s - null trailingAverage means no spend at all in the 3 months before this one
 * (a brand-new or dormant category), which is deliberately never flagged as an anomaly.
 */
data class CategorySpend(
    val category: Category,
    val spent: Money,
    val trailingAverage: Money? = null,
    val isAnomaly: Boolean = false,
)

/**
 * One counterparty's share of a selected category's spend for the period on screen — the
 * "waar komt dit vandaan?" drill-down under the trend chart. [previousAverage] is that same
 * counterparty's average spend in this category over the other periods the chart shows; null
 * means they didn't appear in this category at all in that window (a genuinely new expense).
 * Deliberately a gross sum of that period's debits (and matching split shares), not the netted
 * figure [TransactionRepository.observeCategorySpent] returns — a refund from the same shop
 * nets against it there, but showing "you spent X at this shop" reads more naturally here.
 */
data class CounterpartySpend(
    val counterpartyName: String,
    val amount: Money,
    val previousAverage: Money?,
)

enum class SavingsTipKind { CATEGORY_SPIKE, PRICE_INCREASE, UNCERTAIN_SUBSCRIPTION, NO_BUDGET_LIMIT }

/**
 * One actionable observation surfaced on the overview - Financio's own read of the data, not
 * something the user configured. [categoryId] is set when tapping the tip should jump straight to
 * that category's trend (see ChartsScreen); a subscription-related tip has no category and instead
 * points at Vaste lasten via its own callback.
 */
data class SavingsTip(
    val kind: SavingsTipKind,
    val title: String,
    val detail: String,
    val categoryId: Long? = null,
)

/** Categories whose direction of change is good when it goes *up*, not down — same name-based special-casing [com.financio.app.ui.common.categoryColorFor] already does for these two. */
private val positiveDirectionCategories = setOf("inkomsten", "sparen")

/** 30% above, and at least €15 extra — floors both a relative and absolute jump so a cheap category's normal wobble isn't flagged, and a huge category's trivial 5% wobble isn't either. Shared by the overview's per-category "opvallend hoog" flag and a selected category's own [ChartsViewModel.spikeInsightFor]. */
private const val SPIKE_RATIO_NUM = 13
private const val SPIKE_RATIO_DEN = 10
private const val SPIKE_MIN_EXTRA_CENTS = 1_500L

/** Floor for the "no budget limit" tip - a category with a few euros of unbudgeted spend isn't worth nagging about. */
private const val HIGH_SPEND_NO_LIMIT_CENTS = 5_000L

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
    /** Savings suggestions for the overview - category spikes, subscription price rises, unconfirmed recurring merchants, high spend with no budget limit. Only populated when [selectedCategoryId] is null. */
    val savingsTips: List<SavingsTip> = emptyList(),
    /** The selected category's spend for the period on screen, broken down by counterparty, biggest first — "waar komt dit vandaan?". Only populated when [selectedCategoryId] is non-null. */
    val counterpartyBreakdown: List<CounterpartySpend> = emptyList(),
    /** A one-line "why is this higher than normal" explanation naming the counterparty behind most of the rise - null unless the period on screen is a genuine spike (see [SPIKE_RATIO_NUM]/[SPIKE_MIN_EXTRA_CENTS]). Only populated when [selectedCategoryId] is non-null. */
    val spikeInsight: String? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val appPreferences: AppPreferences,
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
     * for the donut and its top-4 list, the spent-vs-income ratio, and the Bespaartips list. Always
     * scoped to one month regardless of the month/year trend toggle - a donut is a snapshot, not a
     * trend, so there's nothing for "jaar-op-jaar" to mean here (the toggle itself is hidden in
     * this view, see ChartsScreen).
     */
    private fun overviewFor(cats: List<Category>, anchor: YearMonth): Flow<ChartsUiState> =
        if (cats.isEmpty()) {
            flowOf(overviewState(cats, anchor, emptyList(), emptyList()))
        } else {
            combine(cats.map { cat -> categorySpendFlow(cat, anchor) }) { it.toList() }
                .flatMapLatest { spends -> savingsTipsFor(anchor, spends).map { tips -> overviewState(cats, anchor, spends, tips) } }
        }

    /** One category's spend this month plus its own trailing 3-month average, which is all [CategorySpend.isAnomaly] needs. */
    private fun categorySpendFlow(cat: Category, anchor: YearMonth): Flow<CategorySpend> {
        val trailingMonths = (1..3).map { anchor.minusMonths(it.toLong()) }
        return combine(
            transactionRepository.observeCategorySpent(cat.id, anchor),
            combine(trailingMonths.map { m -> transactionRepository.observeCategorySpent(cat.id, m) }) { it.toList() },
        ) { current, trailing ->
            val trailingAverage = trailing.takeIf { amounts -> amounts.any { it.cents > 0 } }
                ?.let { amounts -> Money(amounts.sumOf { it.cents } / amounts.size) }
            val isAnomaly = trailingAverage != null && trailingAverage.cents > 0 &&
                current.cents > trailingAverage.cents * SPIKE_RATIO_NUM / SPIKE_RATIO_DEN &&
                current.cents - trailingAverage.cents >= SPIKE_MIN_EXTRA_CENTS
            CategorySpend(cat, current, trailingAverage, isAnomaly)
        }
    }

    private fun savingsTipsFor(anchor: YearMonth, spends: List<CategorySpend>): Flow<List<SavingsTip>> =
        combine(
            transactionRepository.observeAllTransactions(),
            appPreferences.confirmedSubscriptionNames,
            appPreferences.dismissedSubscriptionNames,
            budgetRepository.observeBudgets(anchor),
        ) { transactions, confirmed, dismissed, budgets -> buildSavingsTips(transactions, confirmed, dismissed, budgets, spends) }

    private fun buildSavingsTips(
        transactions: List<Transaction>,
        confirmedNames: Set<String>,
        dismissedNames: Set<String>,
        budgets: List<Budget>,
        spends: List<CategorySpend>,
    ): List<SavingsTip> = buildList {
        spends.filter { it.isAnomaly }
            .sortedByDescending { it.spent.cents - (it.trailingAverage?.cents ?: 0L) }
            .take(2)
            .forEach { s ->
                val extra = Money(s.spent.cents - (s.trailingAverage?.cents ?: 0L))
                add(
                    SavingsTip(
                        kind = SavingsTipKind.CATEGORY_SPIKE,
                        title = "${s.category.name} is opvallend hoog",
                        detail = "${extra.toDisplayString()} meer dan een gemiddelde maand — tik voor de uitsplitsing.",
                        categoryId = s.category.id,
                    ),
                )
            }

        val priceIncreases = SubscriptionDetector.detect(transactions).filter { it.priceChange != null }
        if (priceIncreases.isNotEmpty()) {
            val extraYearlyCents = priceIncreases.sumOf { it.priceChange!!.yearlyDifference(it.cadence).cents }
            add(
                SavingsTip(
                    kind = SavingsTipKind.PRICE_INCREASE,
                    title = if (priceIncreases.size == 1) {
                        "${priceIncreases.first().counterpartyName} werd duurder"
                    } else {
                        "${priceIncreases.size} abonnementen werden duurder"
                    },
                    detail = "Samen ${Money(extraYearlyCents).toDisplayString()} per jaar meer — bekijk Vaste lasten.",
                ),
            )
        }

        // Same "not yet answered" filter SubscriptionsViewModel applies to its own twijfelgeval
        // list - a merchant the user already confirmed or dismissed there isn't a fresh tip here.
        val unanswered = SubscriptionDetector.detectUncertain(transactions)
            .filter { it.counterpartyName !in confirmedNames && it.counterpartyName !in dismissedNames }
        if (unanswered.isNotEmpty()) {
            add(
                SavingsTip(
                    kind = SavingsTipKind.UNCERTAIN_SUBSCRIPTION,
                    title = if (unanswered.size == 1) {
                        "Mogelijk abonnement: ${unanswered.first().counterpartyName}"
                    } else {
                        "${unanswered.size} mogelijke abonnementen nog onbevestigd"
                    },
                    detail = "Nog niet bevestigd of dit een terugkerende kost is — bekijk Vaste lasten.",
                ),
            )
        }

        // A category with no Budget row at all this month is "unlimited" - the same definition
        // BudgetsViewModel uses for its own unlimited-spend chips.
        val limitedCategoryIds = budgets.map { it.categoryId }.toSet()
        spends.filter { it.category.id !in limitedCategoryIds && it.spent.cents >= HIGH_SPEND_NO_LIMIT_CENTS }
            .maxByOrNull { it.spent.cents }
            ?.let { s ->
                add(
                    SavingsTip(
                        kind = SavingsTipKind.NO_BUDGET_LIMIT,
                        title = "Geen limiet voor ${s.category.name}",
                        detail = "${s.spent.toDisplayString()} deze maand zonder budget — een limiet helpt dit in de gaten te houden.",
                        categoryId = s.category.id,
                    ),
                )
            }
    }

    private fun overviewState(cats: List<Category>, anchor: YearMonth, spends: List<CategorySpend>, tips: List<SavingsTip>): ChartsUiState {
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
            savingsTips = tips,
            referenceLabel = referenceLabelFor(anchor, ChartMode.MONTH_OVER_MONTH),
            canGoToNextPeriod = anchor.isBefore(YearMonth.now()),
        )
    }

    private fun seriesFor(cats: List<Category>, categoryId: Long, m: ChartMode, anchor: YearMonth): Flow<ChartsUiState> {
        val periods = periodsFor(m, anchor)
        // observeCategorySpent - the same "spent" number Budget's progress bars use, see that
        // method's doc comment for why a single unified calculation replaced the two this screen
        // and Budget each used to compute separately.
        return combine(periods.map { period -> transactionRepository.observeCategorySpent(categoryId, period) }) { it.toList() }
            .flatMapLatest { amounts ->
                combine(
                    budgetRepository.observeBudgets(anchor),
                    transactionRepository.observeAllTransactions(),
                    transactionRepository.observeAllSplits(),
                ) { budgets, transactions, splits ->
                    val partial = buildState(cats, categoryId, m, anchor, periods, amounts)
                    val breakdown = breakdownFor(transactions, splits, categoryId, periods, m, amounts)
                    partial.copy(
                        limit = budgets.firstOrNull { it.categoryId == categoryId }?.limit,
                        counterpartyBreakdown = breakdown.list,
                        spikeInsight = breakdown.insight,
                    )
                }
            }
    }

    private data class Breakdown(val list: List<CounterpartySpend>, val insight: String?)

    /** The rightmost period's spend by counterparty, plus each one's own average over the other periods on screen - see [CounterpartySpend]. */
    private fun breakdownFor(
        transactions: List<Transaction>,
        splitsByTransaction: Map<Long, List<TransactionSplit>>,
        categoryId: Long,
        periods: List<YearMonth>,
        m: ChartMode,
        amounts: List<Money>,
    ): Breakdown {
        val priorPeriods = periods.dropLast(1)
        val currentByCounterparty = spendByCounterparty(transactions, splitsByTransaction, categoryId, periods.last(), m)
        val priorByCounterpartyPerPeriod = priorPeriods.map { p -> spendByCounterparty(transactions, splitsByTransaction, categoryId, p, m) }

        val list = currentByCounterparty.entries
            .map { (name, cents) ->
                val seenBefore = priorByCounterpartyPerPeriod.any { name in it }
                val previousAverage = if (priorPeriods.isEmpty() || !seenBefore) {
                    null
                } else {
                    Money(priorByCounterpartyPerPeriod.sumOf { it[name] ?: 0L } / priorPeriods.size)
                }
                CounterpartySpend(name, Money(cents), previousAverage)
            }
            .sortedByDescending { it.amount.cents }

        return Breakdown(list, spikeInsightFor(list, amounts, m))
    }

    private fun spendByCounterparty(
        transactions: List<Transaction>,
        splitsByTransaction: Map<Long, List<TransactionSplit>>,
        categoryId: Long,
        period: YearMonth,
        m: ChartMode,
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        transactions.forEach { t ->
            if (!matchesPeriod(t.date, period, m)) return@forEach
            val splits = splitsByTransaction[t.id]
            if (splits.isNullOrEmpty()) {
                if (t.categoryId == categoryId) result.merge(t.counterpartyName, abs(t.amount.cents), Long::plus)
            } else {
                splits.forEach { split -> if (split.categoryId == categoryId) result.merge(t.counterpartyName, abs(split.amount.cents), Long::plus) }
            }
        }
        return result
    }

    private fun matchesPeriod(date: LocalDate, period: YearMonth, m: ChartMode): Boolean = when (m) {
        ChartMode.MONTH_OVER_MONTH -> YearMonth.from(date) == period
        ChartMode.YEAR_OVER_YEAR -> date.year == period.year
    }

    /**
     * "Why is this period higher than normal" in one sentence, naming whichever counterparty
     * contributed most to the rise - null unless the period on screen actually clears the same
     * spike bar the overview's own badge uses (see [SPIKE_RATIO_NUM]/[SPIKE_MIN_EXTRA_CENTS]).
     * Compares against the average of every *other* bar the trend chart currently shows, which is
     * a wider window than the overview badge's fixed trailing 3 months - two different views, each
     * using whatever window it already has on hand, not a hard inconsistency.
     */
    private fun spikeInsightFor(breakdown: List<CounterpartySpend>, amounts: List<Money>, m: ChartMode): String? {
        if (amounts.size < 2) return null
        val current = amounts.last().cents
        val priorAmounts = amounts.dropLast(1)
        val priorAverage = priorAmounts.sumOf { it.cents } / priorAmounts.size
        if (priorAverage <= 0 || current <= priorAverage * SPIKE_RATIO_NUM / SPIKE_RATIO_DEN || current - priorAverage < SPIKE_MIN_EXTRA_CENTS) {
            return null
        }
        val topDriver = breakdown.maxByOrNull { it.amount.cents - (it.previousAverage?.cents ?: 0L) } ?: return null
        val periodWord = if (m == ChartMode.MONTH_OVER_MONTH) "gemiddelde maand" else "gemiddelde jaar"
        return if (topDriver.previousAverage == null) {
            "Vooral een nieuwe uitgave bij ${topDriver.counterpartyName}: ${topDriver.amount.toDisplayString()}."
        } else {
            val extra = Money(topDriver.amount.cents - topDriver.previousAverage.cents)
            "Vooral door ${topDriver.counterpartyName}: ${extra.toDisplayString()} meer dan je $periodWord bij hen (normaal ${topDriver.previousAverage.toDisplayString()})."
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
