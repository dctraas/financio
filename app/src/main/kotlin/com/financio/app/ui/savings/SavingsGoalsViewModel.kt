package com.financio.app.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.SubscriptionCadence
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** A small rotation, not a picker: keeps "add a category" down to just typing a name - same set CategoryManagementViewModel.addCategory uses. */
private val COLOR_ROTATION = listOf(
    "#5B7A52", "#4C6E77", "#8A4A3D", "#7A6A45", "#6B6485",
    "#4A5A8A", "#3D8A6E", "#9C7A3D", "#3D8FA3", "#A35D82",
)

data class SavingsGoalRow(
    val goal: SavingsGoal,
    val category: Category?,
    val linkedAccount: Account?,
    val progress: Money,
    /** Whole-transaction contributions (debits into this category) so far this calendar year - the "4 stortingen dit jaar" count. Split-transaction allocations aren't counted, same simplification as the progress figure itself. */
    val depositCountThisYear: Int,
    /** Fraction of the way from this goal's first real contribution to its streefdatum - the same tempostreepje input [com.financio.app.ui.budgets.BudgetsUiState.pace] is for Budget. Null without a streefdatum or before any contribution exists yet to anchor "the start" from. */
    val paceFraction: Float?,
    /** What this goal still needs per month to land on its streefdatum, spread over the months remaining. Null once achieved or without a streefdatum. */
    val requiredMonthlyContribution: Money?,
    /** [requiredMonthlyContribution] compared against the trailing average monthly leftover - "past dit binnen je ruimte". Null whenever [requiredMonthlyContribution] is. */
    val fitsWithinLeftover: Boolean?,
    /** The date this goal's progress first reached its target, derived from the same transaction history as [progress] rather than stored - null while not yet achieved. */
    val achievedDate: LocalDate?,
    /** Positive = achieved before the streefdatum, negative = after. Null without both an [achievedDate] and a streefdatum. */
    val earlyByDays: Int?,
    /** Only set for a goal whose name contains "buffer" - its progress expressed in months of detected vaste lasten covered, instead of (or alongside) euros. */
    val bufferMonthsCovered: Double?,
) {
    val percentage: Int
        get() = if (goal.targetAmount.cents <= 0) {
            0
        } else {
            ((progress.cents.toDouble() / goal.targetAmount.cents.toDouble()) * 100).toInt().coerceIn(0, 100)
        }

    val achieved: Boolean
        get() = goal.targetAmount.cents > 0 && progress.cents >= goal.targetAmount.cents
}

data class SavingsGoalsUiState(
    val activeRows: List<SavingsGoalRow> = emptyList(),
    val achievedRows: List<SavingsGoalRow> = emptyList(),
    val archivedRows: List<SavingsGoalRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** Shown next to a goal's "€X/maand nodig" - see [SavingsGoalRow.fitsWithinLeftover]. */
    val averageMonthlyLeftover: Money? = null,
)

/**
 * A savings goal's progress is [SavingsGoal.categoryId]'s net debit-minus-credit total across
 * every transaction ever, plus [SavingsGoal.manualAdjustment] - the same sign convention as
 * [com.financio.core.budget.BudgetEvaluator], just unscoped by month. That's deliberate: "how much
 * have I net moved into this category, ever" is exactly what a goal's progress means, and a later
 * withdrawal (a credit) naturally lowers it again without needing a separate contribution ledger.
 *
 * Everything derived here (deposit count, pace, achieved date) is computed straight from that same
 * transaction history rather than stored — consistent with [com.financio.core.usecase.AccountBalanceResolver]'s
 * "prefer deriving from real data over adding mutable state" approach elsewhere in the app.
 */
@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<SavingsGoalsUiState> = combine(
        savingsGoalRepository.observeGoals(),
        categoryRepository.observeCategories(),
        accountRepository.observeAccounts(),
        transactionRepository.observeAllTransactions(),
    ) { goals, categories, accounts, transactions ->
        buildState(goals, categories, accounts, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsGoalsUiState())

    private fun buildState(
        goals: List<SavingsGoal>,
        categories: List<Category>,
        accounts: List<Account>,
        transactions: List<Transaction>,
    ): SavingsGoalsUiState {
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val today = LocalDate.now()

        val averageMonthlyLeftover = averageMonthlyLeftover(transactions, today)
        val monthlyFixedCosts = monthlyFixedCosts(transactions)

        val rows = goals.map { goal ->
            val linkedAccount = goal.linkedAccountId?.let { accountsById[it] }
            buildRow(goal, categoriesById[goal.categoryId], linkedAccount, transactions, today, averageMonthlyLeftover, monthlyFixedCosts)
        }

        return SavingsGoalsUiState(
            activeRows = rows.filter { !it.goal.archived && !it.achieved },
            achievedRows = rows.filter { !it.goal.archived && it.achieved },
            archivedRows = rows.filter { it.goal.archived },
            categories = categories,
            accounts = accounts,
            averageMonthlyLeftover = averageMonthlyLeftover,
        )
    }

    private fun buildRow(
        goal: SavingsGoal,
        category: Category?,
        linkedAccount: Account?,
        transactions: List<Transaction>,
        today: LocalDate,
        averageMonthlyLeftover: Money?,
        monthlyFixedCosts: Money,
    ): SavingsGoalRow {
        val categoryTx = transactions.filter { it.categoryId == goal.categoryId }.sortedBy { it.date }
        val categoryNet = Money(-categoryTx.sumOf { it.amount.cents })
        val progress = categoryNet + goal.manualAdjustment
        val achieved = goal.targetAmount.cents > 0 && progress.cents >= goal.targetAmount.cents

        val contributions = categoryTx.filter { it.amount.cents < 0 }
        val depositCountThisYear = contributions.count { it.date.year == today.year }
        val firstContributionDate = contributions.firstOrNull()?.date

        val achievedDate = if (!achieved) {
            null
        } else {
            var runningCents = 0L
            contributions
                .firstOrNull { tx ->
                    runningCents -= tx.amount.cents
                    runningCents + goal.manualAdjustment.cents >= goal.targetAmount.cents
                }
                ?.date
                // A manual top-up alone (or combined with only-partial category contributions) tipped
                // this over - there's no historical transaction date to pin that moment to, so "today".
                ?: today
        }
        // Room/Kotlin cross-module note: goal.targetDate can't be smart-cast from a null check
        // alone (it's a val declared in :core, a different module from this ViewModel) - a local
        // copy sidesteps that entirely.
        val targetDate = goal.targetDate

        val earlyByDays = if (achievedDate != null && targetDate != null) {
            ChronoUnit.DAYS.between(achievedDate, targetDate).toInt()
        } else {
            null
        }

        val paceFraction = if (!achieved && targetDate != null && firstContributionDate != null && targetDate.isAfter(firstContributionDate)) {
            val totalDays = ChronoUnit.DAYS.between(firstContributionDate, targetDate).toFloat()
            val elapsedDays = ChronoUnit.DAYS.between(firstContributionDate, today).toFloat()
            (elapsedDays / totalDays).coerceIn(0f, 1f)
        } else {
            null
        }

        val requiredMonthlyContribution = if (!achieved && targetDate != null && targetDate.isAfter(today)) {
            val monthsRemaining = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)).coerceAtLeast(1)
            val remainingCents = (goal.targetAmount.cents - progress.cents).coerceAtLeast(0)
            Money(remainingCents / monthsRemaining)
        } else {
            null
        }
        val fitsWithinLeftover = if (requiredMonthlyContribution != null && averageMonthlyLeftover != null) {
            requiredMonthlyContribution.cents <= averageMonthlyLeftover.cents
        } else {
            null
        }

        val bufferMonthsCovered = if (goal.name.contains("buffer", ignoreCase = true) && monthlyFixedCosts.cents > 0) {
            progress.cents.toDouble() / monthlyFixedCosts.cents.toDouble()
        } else {
            null
        }

        return SavingsGoalRow(
            goal = goal,
            category = category,
            linkedAccount = linkedAccount,
            progress = progress,
            depositCountThisYear = depositCountThisYear,
            paceFraction = paceFraction,
            requiredMonthlyContribution = requiredMonthlyContribution,
            fitsWithinLeftover = fitsWithinLeftover,
            achievedDate = achievedDate,
            earlyByDays = earlyByDays,
            bufferMonthsCovered = bufferMonthsCovered,
        )
    }

    /** Trailing 3 full calendar months before the current one - same "recent, but not the still-incomplete current month" window [com.financio.app.ui.budgets.BudgetsViewModel.suggestedLimitsFor] uses. Null with less than a month of history to average. */
    private fun averageMonthlyLeftover(transactions: List<Transaction>, today: LocalDate): Money? {
        val currentMonth = YearMonth.from(today)
        val netByMonth = transactions
            .map { YearMonth.from(it.date) to it.amount.cents }
            .filter { (month, _) -> month.isBefore(currentMonth) && ChronoUnit.MONTHS.between(month, currentMonth) <= 3 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }
        if (netByMonth.isEmpty()) return null
        return Money(netByMonth.values.sum() / netByMonth.size)
    }

    /** "Vaste lasten" reuses the same detected-subscriptions total Meer's tile shows, amortized to a monthly-equivalent figure per subscription. */
    private fun monthlyFixedCosts(transactions: List<Transaction>): Money {
        val subscriptions = SubscriptionDetector.detect(transactions)
        val cents = subscriptions.sumOf { subscription ->
            val amount = kotlin.math.abs(subscription.averageAmount.cents)
            if (subscription.cadence == SubscriptionCadence.YEARLY) amount / 12 else amount
        }
        return Money(cents)
    }

    fun addGoal(name: String, targetAmount: Money, categoryId: Long, linkedAccountId: Long?, targetDate: LocalDate?) {
        viewModelScope.launch { savingsGoalRepository.addGoal(name, targetAmount, categoryId, linkedAccountId, targetDate) }
    }

    /** "Spaardoel bewerken" - tapping an existing goal, as opposed to [addGoal]'s "Nieuw spaardoel"/"Nieuw doel hiermee". */
    fun editGoal(goalId: Long, name: String, targetAmount: Money, categoryId: Long, linkedAccountId: Long?, targetDate: LocalDate?) {
        viewModelScope.launch { savingsGoalRepository.updateGoal(goalId, name, targetAmount, categoryId, linkedAccountId, targetDate) }
    }

    /** The "+ Nieuwe categorie" option inside "Nieuw spaardoel"'s category picker - creates the category and hands its id back so the dialog can select it immediately, without the user ever leaving the dialog. */
    fun addCategory(name: String, onCreated: (Long) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val color = COLOR_ROTATION[uiState.value.categories.size % COLOR_ROTATION.size]
            val id = categoryRepository.addCategory(trimmed, color)
            onCreated(id)
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch { savingsGoalRepository.deleteGoal(goalId) }
    }

    fun archiveGoal(goalId: Long) {
        viewModelScope.launch { savingsGoalRepository.setArchived(goalId, true) }
    }

    fun addManualAdjustment(goalId: Long, delta: Money) {
        viewModelScope.launch { savingsGoalRepository.addManualAdjustment(goalId, delta) }
    }
}
