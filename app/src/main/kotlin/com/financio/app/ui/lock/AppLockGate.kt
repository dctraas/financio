package com.financio.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Gates [content] behind a biometric prompt when the setting is on. `unlocked` starts false on
 * every fresh composition of this gate — i.e. every process (re)start, which is the point: a
 * killed-and-restarted app should ask again, not silently stay open. Turning the setting off
 * takes effect immediately (falls through to content on the next recomposition); turning it on
 * takes effect the next time the gate is evaluated fresh, not mid-session.
 */
@Composable
fun AppLockGate(
    biometricLockEnabled: Boolean,
    onRequestAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }

    if (!biometricLockEnabled || unlocked) {
        content()
    } else {
        var error by remember { mutableStateOf<String?>(null) }
        val requestAuth = {
            error = null
            onRequestAuth({ unlocked = true }, { message -> error = message })
        }

        // Prompt right away — the button below exists for retrying after a cancel or error,
        // not as the only way in.
        LaunchedEffect(Unit) { requestAuth() }

        LockScreen(error = error, onUnlockClick = requestAuth)
    }
}

@Composable
private fun LockScreen(error: String?, onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Financio is vergrendeld", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Ontgrendel met je vingerafdruk, gezicht of schermbeveiliging.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
        }
        Button(onClick = onUnlockClick) { Text("Ontgrendelen") }
    }
}
