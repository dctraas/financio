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
     * runtime permission from Android 13 onward, so the Instellingen toggle drives both this flag
     * and that permission request together (see `SettingsScreen`) — turning this on without ever
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

    companion object {
        private const val PREFS_NAME = "financio_settings"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        // On by default for a finance app — matches the architecture doc's security section.
        private const val DEFAULT_BIOMETRIC_LOCK = true
        private const val DEFAULT_NOTIFICATIONS = false
        private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    }
}
