package com.financio.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local app settings — not synced, not part of the encrypted transaction database.
 * SharedPreferences is fine here: there's exactly one boolean, and it's read once at startup.
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _biometricLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_LOCK, DEFAULT_BIOMETRIC_LOCK))
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        _biometricLockEnabled.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "financio_settings"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        // On by default for a finance app — matches the architecture doc's security section.
        private const val DEFAULT_BIOMETRIC_LOCK = true
    }
}
