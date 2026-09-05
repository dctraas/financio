package com.financio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.financio.app.ui.nav.FinancioNavHost
import com.financio.app.ui.theme.FinancioTheme
import dagger.hilt.android.AndroidEntryPoint

// TODO fase 1 vervolgstap: BiometricPrompt-gate vóór deze content, zoals de architectuur voorschrijft.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinancioTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FinancioNavHost()
                }
            }
        }
    }
}
