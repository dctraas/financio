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

    companion object {
        private const val PREFS_NAME = "financio_settings"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MONTH_START_DAY = "month_start_day"
        // On by default for a finance app — matches the architecture doc's security section.
        private const val DEFAULT_BIOMETRIC_LOCK = true
        private const val DEFAULT_NOTIFICATIONS = false
        private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        private const val DEFAULT_MONTH_START_DAY = 1
    }
}
