package com.financio.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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

/**
 * Temporary home for budget-limit editing while it's carried over out of the old Instellingen
 * screen — reached from Meer for now. The redesign brief's real destination for this is setting a
 * limit straight from a category chip on the Budget screen itself (task #17, not started yet); once
 * that lands, this screen and its Meer entry point should go away rather than existing twice.
 */
@Composable
fun BudgetLimitsScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgetlimieten") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
    ) { padding ->
        if (state.categories.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text(
                    "Categorieën worden aangemaakt zodra de app voor het eerst opstart — even geduld.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                items(state.categories, key = { it.id }) { category ->
                    BudgetLimitRow(
                        category = category,
                        currentLimit = state.limitsByCategory[category.id],
                        rolloverEnabled = state.rolloverByCategory[category.id] ?: false,
                        onSave = { limit -> viewModel.setLimit(category.id, limit) },
                        onRolloverChange = { enabled -> viewModel.setRollover(category.id, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetLimitRow(
    category: Category,
    currentLimit: Money?,
    rolloverEnabled: Boolean,
    onSave: (Money) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
) {
    // Keyed on category.id so a re-emission of the budgets flow (e.g. after saving a *different*
    // category's limit) doesn't clobber what the user is still typing in this field.
    var text by remember(category.id) { mutableStateOf(currentLimit?.toEuroInputString() ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        // Rollover only means something once there's a limit to roll over from - hidden until then
        // rather than letting the user flip a toggle that has nothing to do yet.
        if (currentLimit != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Restant meenemen naar volgende maand",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = rolloverEnabled, onCheckedChange = onRolloverChange)
            }
        }
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
