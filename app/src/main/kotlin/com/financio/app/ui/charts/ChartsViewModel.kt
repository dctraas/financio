package com.financio.app.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.repository.AccountRepository
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class ChartMode { MONTH_OVER_MONTH, YEAR_OVER_YEAR, BALANCE_HISTORY }

data class ChartPoint(val label: String, val amount: Money, val isCurrent: Boolean)

/** One day's closing balance, for the Saldoverloop line chart. */
data class BalancePoint(val date: LocalDate, val balance: Money)

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
    /** Only populated in [ChartMode.BALANCE_HISTORY] — the rest of the state above is unused there. */
    val balancePoints: List<BalancePoint> = emptyList(),
    /** Every account — only relevant (and only shown) in [ChartMode.BALANCE_HISTORY] once there's more than one. */
    val accounts: List<Account> = emptyList(),
    /** The account [balancePoints] is for — never null once accounts exist, unlike Transacties' "alle rekeningen": summing two accounts' balances into one line isn't a number that means anything. */
    val selectedAccountId: Long? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val mode = MutableStateFlow(ChartMode.MONTH_OVER_MONTH)
    // The rightmost bar's period. Defaults to "now"; goToPreviousPeriod/goToNextPeriod shift the
    // whole 6-month/4-year window, which is how you get to see a month or year further back than
    // the fixed trailing window this screen used to show with no way to move it.
    private val referenceMonth = MutableStateFlow(YearMonth.now())
    private val categories = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Only used by Saldoverloop. null = not chosen yet, resolved to the first account. */
    private val selectedAccountId = MutableStateFlow<Long?>(null)
    private val accounts = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ChartsUiState> = combine(
        categories, selectedCategoryId, mode, referenceMonth,
    ) { cats, selected, m, anchor ->
        ChartQuery(cats, selected ?: cats.firstOrNull()?.id, m, anchor)
    }.flatMapLatest { query ->
        if (query.mode == ChartMode.BALANCE_HISTORY) {
            balanceHistoryState(query.categories)
        } else {
            val categoryId = query.categoryId
            if (categoryId == null) {
                flowOf(ChartsUiState(categories = query.categories, mode = query.mode))
            } else {
                seriesFor(query.categories, categoryId, query.mode, query.anchor)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    fun selectMode(newMode: ChartMode) {
        mode.value = newMode
    }

    fun selectAccount(accountId: Long) {
        selectedAccountId.value = accountId
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
        // goToPreviousPeriod/goToNextPeriod are only wired to the ‹›-navigator, which Saldoverloop
        // doesn't show (see ChartsScreen) - referenceMonth simply never moves in that mode.
        ChartMode.BALANCE_HISTORY -> anchor
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

    /**
     * One point per day with activity, most recent 60 first. ING's CSV export carries no
     * time-of-day, only a date, so when several transactions share a date there's no reliable
     * signal for which one happened last — [TransactionRepository.observeTransactions] already
     * orders "date DESC, id DESC" though, so walking it in that order and keeping the first hit
     * per date deterministically picks the most-recently-inserted transaction for that day as an
     * approximation of its closing balance.
     *
     * One account's balance at a time, never "all accounts" combined (unlike Transacties) — two
     * accounts' balances added together isn't a meaningful line to draw. Defaults to the first
     * account ([DefaultAccount.ID] on a single-account install, since that's the only one there
     * is) until the user picks a different one.
     */
    private fun balanceHistoryState(cats: List<Category>): Flow<ChartsUiState> =
        combine(selectedAccountId, accounts) { selected, accountList ->
            (selected ?: accountList.firstOrNull()?.id ?: DefaultAccount.ID) to accountList
        }.flatMapLatest { (resolvedAccountId, accountList) ->
            transactionRepository.observeTransactions(resolvedAccountId).map { transactions ->
                val seenDates = mutableSetOf<LocalDate>()
                val points = transactions
                    .filter { it.balanceAfter != null }
                    .filter { seenDates.add(it.date) }
                    .map { BalancePoint(it.date, it.balanceAfter!!) }
                    .reversed() // back to chronological ascending for the chart
                    .takeLast(60)
                ChartsUiState(
                    categories = cats,
                    mode = ChartMode.BALANCE_HISTORY,
                    balancePoints = points,
                    currentTotal = points.lastOrNull()?.balance ?: Money.ZERO,
                    accounts = accountList,
                    selectedAccountId = resolvedAccountId,
                )
            }
        }

    private fun periodsFor(m: ChartMode, anchor: YearMonth): List<YearMonth> = when (m) {
        ChartMode.MONTH_OVER_MONTH -> (5 downTo 0).map { anchor.minusMonths(it.toLong()) }
        ChartMode.YEAR_OVER_YEAR -> (3 downTo 0).map { anchor.minusYears(it.toLong()) }
        // Only ever called from seriesFor(), which the BALANCE_HISTORY branch in uiState's
        // flatMapLatest routes around entirely (see balanceHistoryState() instead).
        ChartMode.BALANCE_HISTORY -> error("periodsFor is not used for Saldoverloop")
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
        // Only ever called from buildState(), never reached for Saldoverloop - see periodsFor().
        ChartMode.BALANCE_HISTORY -> error("referenceLabelFor is not used for Saldoverloop")
    }

    private fun labelFor(period: YearMonth, m: ChartMode): String = when (m) {
        ChartMode.MONTH_OVER_MONTH -> period.month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", "")
        ChartMode.YEAR_OVER_YEAR -> period.year.toString()
        // Only ever called from buildState(), never reached for Saldoverloop - see periodsFor().
        ChartMode.BALANCE_HISTORY -> error("labelFor is not used for Saldoverloop")
    }
}

private fun Money.absoluteDisplayString(): String = Money(kotlin.math.abs(cents)).toDisplayString()
