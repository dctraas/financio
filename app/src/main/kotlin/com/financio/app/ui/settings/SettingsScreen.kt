package com.financio.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Placeholder: budgetlimieten per categorie instellen, BiometricPrompt-toggle, en het
 * categorie-/regelbeheer uit de architectuur horen hier — allemaal fase-1 vervolgstappen op
 * dit skeleton, niet iets dit eerste doorloop al hoefde te bouwen.
 */
@Composable
fun SettingsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Instellingen") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
            Text("Nog te bouwen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Budgetlimieten per categorie, app-vergrendeling met BiometricPrompt, en " +
                    "categorisatieregels beheren.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
