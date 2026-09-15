package com.financio.app.ui.yearreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class TopCategoryStat(val name: String, val amount: Money)
data class TopCounterpartyStat(val name: String, val amount: Money, val occurrences: Int)
data class MonthStat(val label: String, val amount: Money)

data class YearReviewUiState(
    val loaded: Boolean = false,
    val year: Int = LocalDate.now().year,
    val hasData: Boolean = false,
    val canGoToNextYear: Boolean = false,
    val totalSpent: Money = Money.ZERO,
    val totalIncome: Money = Money.ZERO,
    /** Income minus spent - can be negative, unlike a savings goal's progress. */
    val netSaved: Money = Money.ZERO,
    val transactionCount: Int = 0,
    val topCategory: TopCategoryStat? = null,
    /** Resolved through confirmed merchant aliases (see MerchantGrouper) - one row for "Albert Heijn", not five for its branches. */
    val topCounterparty: TopCounterpartyStat? = null,
    val busiestMonth: MonthStat? = null,
    /** Null when there's no spend at all recorded for [year] - 1 to compare against. */
    val previousYearComparisonLabel: String? = null,
    /** Spending less than the year before is the good direction, same convention as Inzicht's delta coloring. */
    val previousYearComparisonIsGood: Boolean = false,
)

/**
 * A "wrapped"-style once-a-year summary — the same transaction history every other screen already
 * has, just rolled up to a whole calendar year instead of a month, with the numbers people
 * actually want to see once a year: what did I spend in total, on what, at which shop most, and
 * was this year better or worse than the last one.
 */
@HiltViewModel
class YearReviewViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    appPreferences: AppPreferences,
) : ViewModel() {

    private val year = MutableStateFlow(LocalDate.now().year)

    val uiState: StateFlow<YearReviewUiState> = combine(
        year,
        transactionRepository.observeAllTransactions(),
        transactionRepository.observeAllSplits(),
        categoryRepository.observeCategories(),
        appPreferences.confirmedMerchantAliases,
    ) { y, transactions, splits, categories, aliases -> buildState(y, transactions, splits, categories, aliases) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearReviewUiState())

    fun goToPreviousYear() {
        year.value -= 1
    }

    fun goToNextYear() {
        if (year.value < LocalDate.now().year) year.value += 1
    }

    private fun buildState(
        y: Int,
        transactions: List<Transaction>,
        splitsByTransaction: Map<Long, List<TransactionSplit>>,
        categories: List<Category>,
        aliases: Map<String, String>,
    ): YearReviewUiState {
        val canGoToNextYear = y < LocalDate.now().year
        val thisYear = transactions.filter { it.date.year == y }
        if (thisYear.isEmpty()) {
            return YearReviewUiState(loaded = true, year = y, hasData = false, canGoToNextYear = canGoToNextYear)
        }

        val totalSpentCents = thisYear.filter { it.amount.cents < 0 }.sumOf { -it.amount.cents }
        val totalIncomeCents = thisYear.filter { it.amount.cents > 0 }.sumOf { it.amount.cents }

        val categoriesById = categories.associateBy { it.id }
        val topCategory = yearlyCategorySpend(thisYear, splitsByTransaction)
            .maxByOrNull { it.value }
            ?.let { (categoryId, cents) -> categoriesById[categoryId]?.let { TopCategoryStat(it.name, Money(cents)) } }

        val topCounterparty = yearlyCounterpartySpend(thisYear, aliases)
            .maxByOrNull { it.value.first }
            ?.let { (name, pair) -> TopCounterpartyStat(name, Money(pair.first), pair.second) }

        val busiestMonth = thisYear.filter { it.amount.cents < 0 }
            .groupBy { it.date.monthValue }
            .mapValues { (_, monthTransactions) -> monthTransactions.sumOf { -it.amount.cents } }
            .maxByOrNull { it.value }
            ?.let { (month, cents) -> MonthStat(monthLabel(month), Money(cents)) }

        val previousYearSpentCents = transactions
            .filter { it.date.year == y - 1 && it.amount.cents < 0 }
            .sumOf { -it.amount.cents }
        val comparison = comparisonTo(previousYearSpentCents, totalSpentCents, y - 1)

        return YearReviewUiState(
            loaded = true,
            year = y,
            hasData = true,
            canGoToNextYear = canGoToNextYear,
            totalSpent = Money(totalSpentCents),
            totalIncome = Money(totalIncomeCents),
            netSaved = Money(totalIncomeCents - totalSpentCents),
            transactionCount = thisYear.size,
            topCategory = topCategory,
            topCounterparty = topCounterparty,
            busiestMonth = busiestMonth,
            previousYearComparisonLabel = comparison?.first,
            previousYearComparisonIsGood = comparison?.second ?: false,
        )
    }

    /**
     * Gross per-category spend for the year, splits folded in - the same "sum of debits, not
     * netted" choice ChartsViewModel's counterparty breakdown makes, and for the same reason: a
     * whole calendar year has no existing single-query equivalent to
     * [TransactionRepository.observeCategorySpent] (that's month-scoped), so this stays a plain
     * reduction over data already loaded rather than firing 12 more queries per category.
     */
    private fun yearlyCategorySpend(thisYear: List<Transaction>, splitsByTransaction: Map<Long, List<TransactionSplit>>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        thisYear.forEach { t ->
            if (t.amount.cents >= 0) return@forEach
            val splits = splitsByTransaction[t.id]
            if (splits.isNullOrEmpty()) {
                t.categoryId?.let { result.merge(it, abs(t.amount.cents), Long::plus) }
            } else {
                splits.forEach { split -> result.merge(split.categoryId, abs(split.amount.cents), Long::plus) }
            }
        }
        return result
    }

    /** Counterparty -> (total spent, occurrences), resolved through confirmed merchant aliases first so a chain's branches count as one. */
    private fun yearlyCounterpartySpend(thisYear: List<Transaction>, aliases: Map<String, String>): Map<String, Pair<Long, Int>> {
        val result = mutableMapOf<String, Pair<Long, Int>>()
        thisYear.filter { it.amount.cents < 0 }.forEach { t ->
            val resolvedName = aliases[t.counterpartyName] ?: t.counterpartyName
            val existing = result[resolvedName] ?: (0L to 0)
            result[resolvedName] = (existing.first + abs(t.amount.cents)) to (existing.second + 1)
        }
        return result
    }

    /** (label, isGood) comparing this year's spend to [previousYear]'s - null when there's nothing from that year to compare against. */
    private fun comparisonTo(previousYearSpentCents: Long, thisYearSpentCents: Long, previousYear: Int): Pair<String, Boolean>? {
        if (previousYearSpentCents <= 0) return null
        val diff = thisYearSpentCents - previousYearSpentCents
        val percent = abs(diff * 100 / previousYearSpentCents)
        val label = if (diff >= 0) "$percent% meer uitgegeven dan in $previousYear" else "$percent% minder uitgegeven dan in $previousYear"
        return label to (diff <= 0)
    }

    private fun monthLabel(monthValue: Int): String =
        Month.of(monthValue).getDisplayName(TextStyle.FULL, Locale("nl")).replaceFirstChar { it.uppercase() }
}
