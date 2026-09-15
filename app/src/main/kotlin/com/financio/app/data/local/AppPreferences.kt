package com.financio.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light/dark follows the device by default (SYSTEM); LIGHT/DARK pin it regardless of the device setting. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

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
     * Off by default, unlike the biometric lock: showing a notification needs the POST_NOTIFICATIONS
     * runtime permission from Android 13 onward, so the Meer > Meldingen toggle drives both this
     * flag and that permission request together (see `NotificationsScreen`) — turning this on without ever
     * asking the user would either crash (pre-13's `NotificationManagerCompat.notify` is fine, but
     * the permission check in `NotificationHelper` would just silently no-op) or, done wrong, skip
     * the OS prompt entirely.
     */
    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, DEFAULT_NOTIFICATIONS))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notificationsEnabled.value = enabled
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

    /** "Ja, dit is dezelfde onderneming" — every name in [rawNames] resolves to [canonicalName] from now on, everywhere a counterparty is grouped. */
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

    /** "Nee, dit zijn verschillende ondernemingen" — stops suggesting [canonicalName] as a merge again. */
    fun dismissMerchantGroup(canonicalName: String) {
        val updated = _dismissedMerchantGroups.value + canonicalName
        prefs.edit().putStringSet(KEY_DISMISSED_MERCHANT_GROUPS, updated).apply()
        _dismissedMerchantGroups.value = updated
    }

    /** Un-aliases just [rawName], leaving any other names still mapped to the same canonical untouched — the Ondernemingen screen's per-member "verwijderen" action, distinct from [dismissMerchantGroup]'s whole-suggestion "nee". */
    fun removeMerchantAlias(rawName: String) {
        val updated = _confirmedMerchantAliases.value - rawName
        persistMerchantAliases(updated)
        _confirmedMerchantAliases.value = updated
    }

    companion object {
        private const val PREFS_NAME = "financio_settings"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MONTH_START_DAY = "month_start_day"
        private const val KEY_CONFIRMED_SUBSCRIPTIONS = "confirmed_subscription_names"
        private const val KEY_DISMISSED_SUBSCRIPTIONS = "dismissed_subscription_names"
        private const val KEY_CONFIRMED_MERCHANT_ALIASES = "confirmed_merchant_aliases"
        private const val KEY_DISMISSED_MERCHANT_GROUPS = "dismissed_merchant_groups"
        private const val MERCHANT_ALIAS_SEPARATOR = "\u0001"
        // On by default for a finance app — matches the architecture doc's security section.
        private const val DEFAULT_BIOMETRIC_LOCK = true
        private const val DEFAULT_NOTIFICATIONS = false
        private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        private const val DEFAULT_MONTH_START_DAY = 1
    }
}
