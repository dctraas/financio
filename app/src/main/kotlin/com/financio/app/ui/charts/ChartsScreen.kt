package com.financio.app.ui.charts

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
 * Placeholder: the schermontwerp artifact's month/year comparison chart (per category, with the
 * budget limit drawn into the chart) is real UI work that deserves its own pass rather than a
 * rushed Canvas chart bolted onto the skeleton. Wiring — [com.financio.core.repository.TransactionRepository]
 * already exposes per-category, per-month spend — is in place; this screen is the fase-1 follow-up.
 */
@Composable
fun ChartsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Grafieken") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
            Text("Nog te bouwen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "De maand- en jaarvergelijking per categorie uit het schermontwerp — de databasekant " +
                    "staat al klaar (observeSpent per categorie en maand), dit scherm nog niet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
