package com.financio.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light/dark follows the device by default (SYSTEM); LIGHT/DARK pin it regardless of the device setting. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** [fontScale] multiplies every sp-based text size app-wide - see [com.financio.app.ui.theme.FinancioTheme]. */
enum class TextSize(val fontScale: Float) {
    SMALL(0.9f),
    STANDARD(1.0f),
    LARGE(1.15f),
}

/** Which bottom-nav tab [com.financio.app.ui.nav.FinancioNavHost] opens on - "Instellingen"'s startpagina picker. */
enum class StartTab { VANDAAG, TRANSACTIES, INZICHT, DOELEN, MEER }

/** Row height/padding for the Transacties list - COMPACT trades whitespace for more rows on screen at once. */
enum class TransactionDensity { COMFORTABLE, COMPACT }

/** Which weekday Vandaag's "deze week"-venster (and any other calendar-week window) starts counting from. */
enum class WeekStartDay(val isoDayOfWeek: java.time.DayOfWeek) {
    MONDAY(java.time.DayOfWeek.MONDAY),
    SUNDAY(java.time.DayOfWeek.SUNDAY),
}

/**
 * Device-local app settings — not synced, not part of the encrypted transaction database.
 * SharedPreferences is fine here: there's a handful of booleans, all read once at startup.
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _biometricLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_LOCK, DEFAULT_BIOMETRIC_LOCK))
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        _biometricLockEnabled.value = enabled
    }

    /**
     * Split from one combined "Meldingen" flag into its own "Budget bijna op" toggle (Instellingen,
     * schermontwerp #17) - each still needs the POST_NOTIFICATIONS runtime permission from Android
     * 13 onward, so `SettingsScreen` drives this flag and that permission request together, the
     * same way the old combined toggle did. Falls back to the legacy combined key the first time
     * it's read, so upgrading doesn't silently turn this off for someone who'd already opted in.
     */
    private val _budgetThresholdNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BUDGET_THRESHOLD_NOTIFICATIONS, prefs.getBoolean(KEY_NOTIFICATIONS, DEFAULT_BUDGET_THRESHOLD_NOTIFICATIONS)),
    )
    val budgetThresholdNotificationsEnabled: StateFlow<Boolean> = _budgetThresholdNotificationsEnabled.asStateFlow()

    fun setBudgetThresholdNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_THRESHOLD_NOTIFICATIONS, enabled).apply()
        _budgetThresholdNotificationsEnabled.value = enabled
    }

    /** The other half of the old combined "Meldingen" flag - see [budgetThresholdNotificationsEnabled]. */
    private val _weeklyDigestEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_WEEKLY_DIGEST, prefs.getBoolean(KEY_NOTIFICATIONS, DEFAULT_WEEKLY_DIGEST)),
    )
    val weeklyDigestEnabled: StateFlow<Boolean> = _weeklyDigestEnabled.asStateFlow()

    fun setWeeklyDigestEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY_DIGEST, enabled).apply()
        _weeklyDigestEnabled.value = enabled
    }

    private val _savingsGoalAchievedNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SAVINGS_GOAL_ACHIEVED_NOTIFICATIONS, DEFAULT_SAVINGS_GOAL_ACHIEVED_NOTIFICATIONS),
    )
    val savingsGoalAchievedNotificationsEnabled: StateFlow<Boolean> = _savingsGoalAchievedNotificationsEnabled.asStateFlow()

    fun setSavingsGoalAchievedNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVINGS_GOAL_ACHIEVED_NOTIFICATIONS, enabled).apply()
        _savingsGoalAchievedNotificationsEnabled.value = enabled
    }

    private val _unusualTransactionNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_UNUSUAL_TRANSACTION_NOTIFICATIONS, DEFAULT_UNUSUAL_TRANSACTION_NOTIFICATIONS),
    )
    val unusualTransactionNotificationsEnabled: StateFlow<Boolean> = _unusualTransactionNotificationsEnabled.asStateFlow()

    fun setUnusualTransactionNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UNUSUAL_TRANSACTION_NOTIFICATIONS, enabled).apply()
        _unusualTransactionNotificationsEnabled.value = enabled
    }

    /** "Plan je week" - zondagavond, uit per default net als het weekoverzicht. */
    private val _sundayPlanningNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SUNDAY_PLANNING_NOTIFICATIONS, DEFAULT_SUNDAY_PLANNING_NOTIFICATIONS),
    )
    val sundayPlanningNotificationsEnabled: StateFlow<Boolean> = _sundayPlanningNotificationsEnabled.asStateFlow()

    fun setSundayPlanningNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUNDAY_PLANNING_NOTIFICATIONS, enabled).apply()
        _sundayPlanningNotificationsEnabled.value = enabled
    }

    /** Categories that never trigger [com.financio.app.notifications.BudgetThresholdNotifier], stored as string-encoded ids same as [confirmedSubscriptionNames]'s string-set approach. */
    private val _mutedBudgetCategoryIds = MutableStateFlow(
        prefs.getStringSet(KEY_MUTED_BUDGET_CATEGORY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )
    val mutedBudgetCategoryIds: StateFlow<Set<Long>> = _mutedBudgetCategoryIds.asStateFlow()

    fun setBudgetCategoryMuted(categoryId: Long, muted: Boolean) {
        val updated = if (muted) _mutedBudgetCategoryIds.value + categoryId else _mutedBudgetCategoryIds.value - categoryId
        prefs.edit().putStringSet(KEY_MUTED_BUDGET_CATEGORY_IDS, updated.map { it.toString() }.toSet()).apply()
        _mutedBudgetCategoryIds.value = updated
    }

    /**
     * "Bedragen verbergen tot je de app ontgrendelt" (Instellingen, schermontwerp #17) - stored and
     * toggleable now, like [monthStartDay] before the screens that actually compute month
     * boundaries were updated to use it. Actually masking every amount across Vandaag, Transacties,
     * Budget etc. is a real cross-cutting change of its own, worth its own pass rather than folded
     * blind into this one.
     */
    private val _hideAmountsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HIDE_AMOUNTS, DEFAULT_HIDE_AMOUNTS))
    val hideAmountsEnabled: StateFlow<Boolean> = _hideAmountsEnabled.asStateFlow()

    fun setHideAmountsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_AMOUNTS, enabled).apply()
        _hideAmountsEnabled.value = enabled
    }

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, null)?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: DEFAULT_THEME_MODE,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private val _textSize = MutableStateFlow(
        prefs.getString(KEY_TEXT_SIZE, null)?.let { stored ->
            runCatching { TextSize.valueOf(stored) }.getOrNull()
        } ?: DEFAULT_TEXT_SIZE,
    )
    val textSize: StateFlow<TextSize> = _textSize.asStateFlow()

    fun setTextSize(size: TextSize) {
        prefs.edit().putString(KEY_TEXT_SIZE, size.name).apply()
        _textSize.value = size
    }

    /**
     * Stored, and shown in Meer, but NOT YET wired into how a "month" is computed anywhere else
     * (Budget/Inzicht/Vaste lasten still use calendar months via `YearMonth`/SQL `date LIKE
     * 'yyyy-MM-%'`). Doing that properly means replacing those string-match queries with real
     * date-range comparisons everywhere a month boundary is used — a change worth doing carefully,
     * with its own review, rather than folded blind into this redesign pass. The preference exists
     * now so the setting itself doesn't have to be re-added later.
     */
    private val _monthStartDay = MutableStateFlow(prefs.getInt(KEY_MONTH_START_DAY, DEFAULT_MONTH_START_DAY))
    val monthStartDay: StateFlow<Int> = _monthStartDay.asStateFlow()

    fun setMonthStartDay(day: Int) {
        val clamped = day.coerceIn(1, 28) // 28 so it's a valid day in every month, including February
        prefs.edit().putInt(KEY_MONTH_START_DAY, clamped).apply()
        _monthStartDay.value = clamped
    }

    private val _startTab = MutableStateFlow(
        prefs.getString(KEY_START_TAB, null)?.let { stored -> runCatching { StartTab.valueOf(stored) }.getOrNull() } ?: DEFAULT_START_TAB,
    )
    val startTab: StateFlow<StartTab> = _startTab.asStateFlow()

    fun setStartTab(tab: StartTab) {
        prefs.edit().putString(KEY_START_TAB, tab.name).apply()
        _startTab.value = tab
    }

    private val _transactionDensity = MutableStateFlow(
        prefs.getString(KEY_TRANSACTION_DENSITY, null)?.let { stored -> runCatching { TransactionDensity.valueOf(stored) }.getOrNull() }
            ?: DEFAULT_TRANSACTION_DENSITY,
    )
    val transactionDensity: StateFlow<TransactionDensity> = _transactionDensity.asStateFlow()

    fun setTransactionDensity(density: TransactionDensity) {
        prefs.edit().putString(KEY_TRANSACTION_DENSITY, density.name).apply()
        _transactionDensity.value = density
    }

    private val _weekStartDay = MutableStateFlow(
        prefs.getString(KEY_WEEK_START_DAY, null)?.let { stored -> runCatching { WeekStartDay.valueOf(stored) }.getOrNull() } ?: DEFAULT_WEEK_START_DAY,
    )
    val weekStartDay: StateFlow<WeekStartDay> = _weekStartDay.asStateFlow()

    fun setWeekStartDay(day: WeekStartDay) {
        prefs.edit().putString(KEY_WEEK_START_DAY, day.name).apply()
        _weekStartDay.value = day
    }

    /** "Toon centen" - off rounds every prominent amount down to whole euros via [com.financio.core.model.Money.toDisplayString]'s own showCents param. */
    private val _showCentsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CENTS, DEFAULT_SHOW_CENTS))
    val showCentsEnabled: StateFlow<Boolean> = _showCentsEnabled.asStateFlow()

    fun setShowCentsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CENTS, enabled).apply()
        _showCentsEnabled.value = enabled
    }

    /**
     * The Vaste lasten screen's "twijfelgeval" yes/no answers, keyed by counterparty name — kept
     * here as two plain string sets rather than a new Room table/migration, since this is a
     * lightweight per-device preference (not transaction data) closer in spirit to
     * [themeMode] than to anything in the encrypted database.
     */
    private val _confirmedSubscriptionNames = MutableStateFlow(prefs.getStringSet(KEY_CONFIRMED_SUBSCRIPTIONS, emptySet()).orEmpty())
    val confirmedSubscriptionNames: StateFlow<Set<String>> = _confirmedSubscriptionNames.asStateFlow()

    private val _dismissedSubscriptionNames = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED_SUBSCRIPTIONS, emptySet()).orEmpty())
    val dismissedSubscriptionNames: StateFlow<Set<String>> = _dismissedSubscriptionNames.asStateFlow()

    /** "Ja, dit is een vast lastje" — moves [counterpartyName] out of "twijfelgeval" for good. */
    fun confirmSubscription(counterpartyName: String) {
        val updated = _confirmedSubscriptionNames.value + counterpartyName
        prefs.edit().putStringSet(KEY_CONFIRMED_SUBSCRIPTIONS, updated).apply()
        _confirmedSubscriptionNames.value = updated
        if (counterpartyName in _dismissedSubscriptionNames.value) {
            val updatedDismissed = _dismissedSubscriptionNames.value - counterpartyName
            prefs.edit().putStringSet(KEY_DISMISSED_SUBSCRIPTIONS, updatedDismissed).apply()
            _dismissedSubscriptionNames.value = updatedDismissed
        }
    }

    /** "Nee, dit is geen abonnement" — stops asking about [counterpartyName] again. */
    fun dismissSubscription(counterpartyName: String) {
        val updated = _dismissedSubscriptionNames.value + counterpartyName
        prefs.edit().putStringSet(KEY_DISMISSED_SUBSCRIPTIONS, updated).apply()
        _dismissedSubscriptionNames.value = updated
    }

    /**
     * Inzicht's "waar komt dit vandaan?" merchant-grouping confirmations (see MerchantGrouper in
     * :core) - a raw counterparty name -> the canonical chain name the user agreed it belongs
     * under, e.g. "Albert Heijn 2200 Gorinchem NLD" -> "Albert Heijn". A SharedPreferences string
     * set can't store a map directly, so each entry is packed as "raw<sep>canonical" using
     * [MERCHANT_ALIAS_SEPARATOR] - an unprintable control character no real counterparty name is
     * remotely likely to contain, unlike a plain comma or pipe a shop name might genuinely use.
     */
    private val _confirmedMerchantAliases = MutableStateFlow(loadMerchantAliases())
    val confirmedMerchantAliases: StateFlow<Map<String, String>> = _confirmedMerchantAliases.asStateFlow()

    /** Canonical names the user said "nee" to grouping under - keyed by canonical name, not by the raw names involved, so the suggestion doesn't reappear even if a new branch of the same chain shows up later. */
    private val _dismissedMerchantGroups = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED_MERCHANT_GROUPS, emptySet()).orEmpty())
    val dismissedMerchantGroups: StateFlow<Set<String>> = _dismissedMerchantGroups.asStateFlow()

    private fun loadMerchantAliases(): Map<String, String> =
        prefs.getStringSet(KEY_CONFIRMED_MERCHANT_ALIASES, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(MERCHANT_ALIAS_SEPARATOR, limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

    private fun persistMerchantAliases(aliases: Map<String, String>) {
        val encoded = aliases.map { (raw, canonical) -> "$raw$MERCHANT_ALIAS_SEPARATOR$canonical" }.toSet()
        prefs.edit().putStringSet(KEY_CONFIRMED_MERCHANT_ALIASES, encoded).apply()
    }

    /** "Ja, dit is dezelfde tegenpartij" — every name in [rawNames] resolves to [canonicalName] from now on, everywhere a counterparty is grouped. */
    fun confirmMerchantGroup(canonicalName: String, rawNames: List<String>) {
        val updated = _confirmedMerchantAliases.value + rawNames.associateWith { canonicalName }
        persistMerchantAliases(updated)
        _confirmedMerchantAliases.value = updated
        if (canonicalName in _dismissedMerchantGroups.value) {
            val updatedDismissed = _dismissedMerchantGroups.value - canonicalName
            prefs.edit().putStringSet(KEY_DISMISSED_MERCHANT_GROUPS, updatedDismissed).apply()
            _dismissedMerchantGroups.value = updatedDismissed
        }
    }

    /** "Nee, dit zijn verschillende tegenpartijen" — stops suggesting [canonicalName] as a merge again. */
    fun dismissMerchantGroup(canonicalName: String) {
        val updated = _dismissedMerchantGroups.value + canonicalName
        prefs.edit().putStringSet(KEY_DISMISSED_MERCHANT_GROUPS, updated).apply()
        _dismissedMerchantGroups.value = updated
    }

    /** Un-aliases just [rawName], leaving any other names still mapped to the same canonical untouched — the Tegenpartijen screen's per-member "verwijderen" action, distinct from [dismissMerchantGroup]'s whole-suggestion "nee". */
    fun removeMerchantAlias(rawName: String) {
        val updated = _confirmedMerchantAliases.value - rawName
        persistMerchantAliases(updated)
        _confirmedMerchantAliases.value = updated
    }

    companion object {
        private const val PREFS_NAME = "financio_settings"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        /** Legacy combined flag, kept only as a one-time upgrade fallback - see [budgetThresholdNotificationsEnabled]/[weeklyDigestEnabled]. */
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_BUDGET_THRESHOLD_NOTIFICATIONS = "budget_threshold_notifications_enabled"
        private const val KEY_WEEKLY_DIGEST = "weekly_digest_enabled"
        private const val KEY_HIDE_AMOUNTS = "hide_amounts_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_MONTH_START_DAY = "month_start_day"
        private const val KEY_START_TAB = "start_tab"
        private const val KEY_TRANSACTION_DENSITY = "transaction_density"
        private const val KEY_WEEK_START_DAY = "week_start_day"
        private const val KEY_SHOW_CENTS = "show_cents_enabled"
        private const val KEY_SAVINGS_GOAL_ACHIEVED_NOTIFICATIONS = "savings_goal_achieved_notifications_enabled"
        private const val KEY_UNUSUAL_TRANSACTION_NOTIFICATIONS = "unusual_transaction_notifications_enabled"
        private const val KEY_SUNDAY_PLANNING_NOTIFICATIONS = "sunday_planning_notifications_enabled"
        private const val KEY_MUTED_BUDGET_CATEGORY_IDS = "muted_budget_category_ids"
        private const val KEY_CONFIRMED_SUBSCRIPTIONS = "confirmed_subscription_names"
        private const val KEY_DISMISSED_SUBSCRIPTIONS = "dismissed_subscription_names"
        private const val KEY_CONFIRMED_MERCHANT_ALIASES = "confirmed_merchant_aliases"
        private const val KEY_DISMISSED_MERCHANT_GROUPS = "dismissed_merchant_groups"
        private const val MERCHANT_ALIAS_SEPARATOR = "\u0001"
        // On by default for a finance app — matches the architecture doc's security section.
        private const val DEFAULT_BIOMETRIC_LOCK = true
        // Matches the schermontwerp: "Budget bijna op" on, "Weekoverzicht" off by default.
        private const val DEFAULT_BUDGET_THRESHOLD_NOTIFICATIONS = true
        private const val DEFAULT_WEEKLY_DIGEST = false
        private const val DEFAULT_HIDE_AMOUNTS = false
        private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        private val DEFAULT_TEXT_SIZE = TextSize.STANDARD
        private const val DEFAULT_MONTH_START_DAY = 1
        private val DEFAULT_START_TAB = StartTab.VANDAAG
        private val DEFAULT_TRANSACTION_DENSITY = TransactionDensity.COMFORTABLE
        private val DEFAULT_WEEK_START_DAY = WeekStartDay.MONDAY
        // Matches every existing screen's own hardcoded behavior today - opting in to rounder
        // numbers is something someone turns on, not a change sprung on existing installs.
        private const val DEFAULT_SHOW_CENTS = true
        private const val DEFAULT_SAVINGS_GOAL_ACHIEVED_NOTIFICATIONS = true
        private const val DEFAULT_UNUSUAL_TRANSACTION_NOTIFICATIONS = true
        private const val DEFAULT_SUNDAY_PLANNING_NOTIFICATIONS = false
    }
}
