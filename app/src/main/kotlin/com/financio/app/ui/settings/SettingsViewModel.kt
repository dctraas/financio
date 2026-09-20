package com.financio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.app.data.local.StartTab
import com.financio.app.data.local.TextSize
import com.financio.app.data.local.ThemeMode
import com.financio.app.data.local.TransactionDensity
import com.financio.app.data.local.WeekStartDay
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class SettingsUiState(
    val biometricLockEnabled: Boolean = true,
    val budgetThresholdNotificationsEnabled: Boolean = true,
    val weeklyDigestEnabled: Boolean = false,
    /** "Bedragen verbergen" — see [AppPreferences.hideAmountsEnabled] for why nothing downstream reads this yet. */
    val hideAmountsEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.STANDARD,
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
    val limitsByCategory: Map<Long, Money> = emptyMap(),
    val rolloverByCategory: Map<Long, Boolean> = emptyMap(),
    /** 1-28 — see [AppPreferences.monthStartDay] for why nothing downstream reads this yet. */
    val monthStartDay: Int = 1,
    val startTab: StartTab = StartTab.VANDAAG,
    val transactionDensity: TransactionDensity = TransactionDensity.COMFORTABLE,
    val weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    val showCentsEnabled: Boolean = true,
    val savingsGoalAchievedNotificationsEnabled: Boolean = true,
    val unusualTransactionNotificationsEnabled: Boolean = true,
    val sundayPlanningNotificationsEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val currentMonth = YearMonth.now()

    private val coreState: Flow<SettingsUiState> = combine(
        appPreferences.biometricLockEnabled,
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        budgetRepository.observeBudgets(currentMonth),
    ) { lockEnabled, categories, rules, budgets ->
        SettingsUiState(
            biometricLockEnabled = lockEnabled,
            categories = categories,
            rules = rules,
            limitsByCategory = budgets.associate { it.categoryId to it.limit },
            rolloverByCategory = budgets.associate { it.categoryId to it.rollover },
        )
    }

    // Grouped into its own 5-tuple rather than crammed into one giant combine() with coreState -
    // the vararg overload past 5 inputs takes a much less readable Array<T> callback, so instead
    // this feeds into the final 3-arg combine() below as a single flow.
    private val displayAndNotificationState: Flow<SettingsExtras> = combine(
        appPreferences.budgetThresholdNotificationsEnabled,
        appPreferences.weeklyDigestEnabled,
        appPreferences.hideAmountsEnabled,
        appPreferences.themeMode,
        appPreferences.textSize,
    ) { budgetThresholdEnabled, weeklyDigestEnabled, hideAmountsEnabled, themeMode, textSize ->
        SettingsExtras(budgetThresholdEnabled, weeklyDigestEnabled, hideAmountsEnabled, themeMode, textSize)
    }

    private data class SettingsExtras(
        val budgetThresholdNotificationsEnabled: Boolean,
        val weeklyDigestEnabled: Boolean,
        val hideAmountsEnabled: Boolean,
        val themeMode: ThemeMode,
        val textSize: TextSize,
    )

    // A second "extras" group, same reasoning as displayAndNotificationState above - the newer
    // Instellingen additions (startpagina, transactiedichtheid, weekstart, centen tonen).
    private val layoutState: Flow<LayoutExtras> = combine(
        appPreferences.startTab,
        appPreferences.transactionDensity,
        appPreferences.weekStartDay,
        appPreferences.showCentsEnabled,
    ) { startTab, transactionDensity, weekStartDay, showCentsEnabled ->
        LayoutExtras(startTab, transactionDensity, weekStartDay, showCentsEnabled)
    }

    private data class LayoutExtras(
        val startTab: StartTab,
        val transactionDensity: TransactionDensity,
        val weekStartDay: WeekStartDay,
        val showCentsEnabled: Boolean,
    )

    // A third "extras" group, same reasoning as the two above - the newer notification toggles
    // (spaardoel behaald, ongewone transactie, zondagavond-planning).
    private val newerNotificationState: Flow<NewerNotificationExtras> = combine(
        appPreferences.savingsGoalAchievedNotificationsEnabled,
        appPreferences.unusualTransactionNotificationsEnabled,
        appPreferences.sundayPlanningNotificationsEnabled,
    ) { goalAchieved, unusualTransaction, sundayPlanning ->
        NewerNotificationExtras(goalAchieved, unusualTransaction, sundayPlanning)
    }

    private data class NewerNotificationExtras(
        val savingsGoalAchievedNotificationsEnabled: Boolean,
        val unusualTransactionNotificationsEnabled: Boolean,
        val sundayPlanningNotificationsEnabled: Boolean,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        coreState,
        displayAndNotificationState,
        appPreferences.monthStartDay,
        layoutState,
    ) { snapshot, extras, monthStartDay, layout ->
        snapshot.copy(
            budgetThresholdNotificationsEnabled = extras.budgetThresholdNotificationsEnabled,
            weeklyDigestEnabled = extras.weeklyDigestEnabled,
            hideAmountsEnabled = extras.hideAmountsEnabled,
            themeMode = extras.themeMode,
            textSize = extras.textSize,
            monthStartDay = monthStartDay,
            startTab = layout.startTab,
            transactionDensity = layout.transactionDensity,
            weekStartDay = layout.weekStartDay,
            showCentsEnabled = layout.showCentsEnabled,
        )
    }.combine(newerNotificationState) { state, extras2 ->
        state.copy(
            savingsGoalAchievedNotificationsEnabled = extras2.savingsGoalAchievedNotificationsEnabled,
            unusualTransactionNotificationsEnabled = extras2.unusualTransactionNotificationsEnabled,
            sundayPlanningNotificationsEnabled = extras2.sundayPlanningNotificationsEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setBiometricLockEnabled(enabled: Boolean) {
        appPreferences.setBiometricLockEnabled(enabled)
    }

    /**
     * Only the local preference — the OS permission prompt itself (needed on Android 13+ before
     * a notification can actually show) is a `SettingsScreen`-level concern, since it needs an
     * Activity to launch from. This flag can end up `true` with the permission still denied (the
     * user said no, or hasn't been asked yet); [com.financio.app.notifications.NotificationHelper]
     * checks the real permission itself before ever posting, so that combination just stays silent
     * rather than crashing.
     */
    fun setBudgetThresholdNotificationsEnabled(enabled: Boolean) {
        appPreferences.setBudgetThresholdNotificationsEnabled(enabled)
    }

    /** Same permission caveat as [setBudgetThresholdNotificationsEnabled]. */
    fun setWeeklyDigestEnabled(enabled: Boolean) {
        appPreferences.setWeeklyDigestEnabled(enabled)
    }

    fun setHideAmountsEnabled(enabled: Boolean) {
        appPreferences.setHideAmountsEnabled(enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.setThemeMode(mode)
    }

    fun setTextSize(size: TextSize) {
        appPreferences.setTextSize(size)
    }

    fun setMonthStartDay(day: Int) {
        appPreferences.setMonthStartDay(day)
    }

    fun setStartTab(tab: StartTab) {
        appPreferences.setStartTab(tab)
    }

    fun setTransactionDensity(density: TransactionDensity) {
        appPreferences.setTransactionDensity(density)
    }

    fun setWeekStartDay(day: WeekStartDay) {
        appPreferences.setWeekStartDay(day)
    }

    fun setShowCentsEnabled(enabled: Boolean) {
        appPreferences.setShowCentsEnabled(enabled)
    }

    fun setSavingsGoalAchievedNotificationsEnabled(enabled: Boolean) {
        appPreferences.setSavingsGoalAchievedNotificationsEnabled(enabled)
    }

    fun setUnusualTransactionNotificationsEnabled(enabled: Boolean) {
        appPreferences.setUnusualTransactionNotificationsEnabled(enabled)
    }

    /** Same permission caveat as [setBudgetThresholdNotificationsEnabled]. */
    fun setSundayPlanningNotificationsEnabled(enabled: Boolean) {
        appPreferences.setSundayPlanningNotificationsEnabled(enabled)
    }

    /** Called once the user finishes editing a limit field — not on every keystroke. */
    fun setLimit(categoryId: Long, limit: Money) {
        viewModelScope.launch { budgetRepository.setLimit(categoryId, currentMonth, limit) }
    }

    /** Unused budget left over at the end of a month gets added as bonus headroom the next month. */
    fun setRollover(categoryId: Long, rollover: Boolean) {
        viewModelScope.launch { budgetRepository.setRollover(categoryId, currentMonth, rollover) }
    }
}
