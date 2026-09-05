package com.financio.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.core.model.Category
import com.financio.core.model.Money

@Composable
fun SettingsScreen(onManageCategoriesClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Instellingen") }) }) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item { SectionHeader("Vergrendeling") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ontgrendelen met biometrie", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Vraagt vingerafdruk of gezicht bij het openen van de app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.biometricLockEnabled, onCheckedChange = viewModel::setBiometricLockEnabled)
                }
            }

            item { SectionHeader("Budgetlimieten") }
            if (state.categories.isEmpty()) {
                item {
                    Text(
                        "Categorieën worden aangemaakt zodra de app voor het eerst opstart — even geduld.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.categories, key = { "limit-${it.id}" }) { category ->
                    BudgetLimitRow(
                        category = category,
                        currentLimit = state.limitsByCategory[category.id],
                        onSave = { limit -> viewModel.setLimit(category.id, limit) },
                    )
                }
            }

            item { SectionHeader("Categorieën & regels") }
            item {
                Text(
                    "Categorieën toevoegen of verwijderen, en regels beheren waarmee transacties " +
                        "automatisch worden gecategoriseerd.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Beheren →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onManageCategoriesClick),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun BudgetLimitRow(category: Category, currentLimit: Money?, onSave: (Money) -> Unit) {
    // Keyed on category.id so a re-emission of the budgets flow (e.g. after saving a *different*
    // category's limit) doesn't clobber what the user is still typing in this field.
    var text by remember(category.id) { mutableStateOf(currentLimit?.toEuroInputString() ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category.name, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("geen limiet") },
            prefix = { Text("€") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(120.dp),
        )
        IconButton(onClick = {
            parseEuroInput(text)?.let(onSave)
        }) { Icon(Icons.Filled.Check, contentDescription = "Limiet opslaan") }
    }
}

private fun Money.toEuroInputString(): String {
    val absCents = kotlin.math.abs(cents)
    return "${absCents / 100},${(absCents % 100).toString().padStart(2, '0')}"
}

private fun parseEuroInput(text: String): Money? =
    if (text.isBlank()) null else runCatching { Money.parseCommaDecimal(normalizeEuroInput(text)) }.getOrNull()

/** Accepts "450" as well as "450,00" — a bare integer has no comma for [Money.parseCommaDecimal] to split on. */
private fun normalizeEuroInput(text: String): String = if (text.contains(",")) text else "$text,00"
