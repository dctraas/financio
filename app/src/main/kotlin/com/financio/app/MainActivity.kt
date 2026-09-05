package com.financio.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.lock.AppLockGate
import com.financio.app.ui.nav.FinancioNavHost
import com.financio.app.ui.settings.SettingsViewModel
import com.financio.app.ui.theme.FinancioTheme
import dagger.hilt.android.AndroidEntryPoint

// BiometricPrompt needs a FragmentActivity host, hence this extends FragmentActivity rather
// than the plain ComponentActivity most Compose-only screens get away with.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinancioTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val settingsViewModel: SettingsViewModel = hiltViewModel()
                    val settingsState by settingsViewModel.uiState.collectAsState()

                    AppLockGate(
                        biometricLockEnabled = settingsState.biometricLockEnabled,
                        onRequestAuth = ::requestBiometricAuth,
                    ) {
                        FinancioNavHost()
                    }
                }
            }
        }
    }

    private fun requestBiometricAuth(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val allowedAuthenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(allowedAuthenticators)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometrics and no PIN/pattern enrolled — don't lock the user out of their own
            // app over a device configuration Financio can't fix from here.
            onSuccess()
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle("Ontgrendel om je transacties te bekijken")
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(promptInfo)
    }
}
