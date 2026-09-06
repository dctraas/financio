package com.financio.app.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Category
import com.financio.core.model.Money

@Composable
fun SavingsGoalsScreen(viewModel: SavingsGoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var addingGoal by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavingsGoalRow?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spaardoelen") }) },
        floatingActionButton = {
            if (state.categories.isNotEmpty()) {
                FloatingActionButton(onClick = { addingGoal = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Spaardoel toevoegen")
                }
            }
        },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("Nog geen spaardoelen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Koppel een doelbedrag aan een categorie (bijv. Sparen & beleggen) om de " +
                        "voortgang hier te volgen — gebaseerd op wat je er al naartoe hebt " +
                        "overgeboekt volgens je eigen transactiehistorie.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.rows, key = { it.goal.id }) { row ->
                    SavingsGoalCard(row, onDeleteClick = { deleting = row })
                }
            }
        }
    }

    if (addingGoal) {
        AddGoalDialog(
            categories = state.categories,
            onDismiss = { addingGoal = false },
            onSave = { name, target, categoryId ->
                viewModel.addGoal(name, target, categoryId)
                addingGoal = false
            },
        )
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Spaardoel verwijderen?") },
            text = { Text("'${row.goal.name}' wordt verwijderd. Je transacties en categorie blijven ongewijzigd.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(row.goal.id); deleting = null }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun SavingsGoalCard(row: SavingsGoalRow, onDeleteClick: () -> Unit) {
    val statusColors = LocalBudgetStatusColors.current
    val achieved = row.percentage >= 100

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.goal.name, fontWeight = FontWeight.Bold)
                row.category?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "${row.progress.toDisplayString()} / ${row.goal.targetAmount.toDisplayString()}",
                color = if (achieved) statusColors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val fraction = row.percentage.coerceIn(0, 100) / 100f
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (achieved) statusColors.ok else MaterialTheme.colorScheme.primary),
                )
            }
        }
        if (achieved) {
            Text(
                "Doel behaald! 🎉",
                color = statusColors.ok,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "Verwijderen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDeleteClick).padding(top = 8.dp),
        )
    }
}

@Composable
private fun AddGoalDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, target: Money, categoryId: Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    val target = parseEuroInput(targetText)
    val isValid = name.isNotBlank() && target != null && target.cents > 0 && categoryId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nieuw spaardoel") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    placeholder = { Text("bijv. Vakantie 2027") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Doelbedrag") },
                    prefix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        categories.firstOrNull { it.id == categoryId }?.name ?: "Kies categorie",
                        color = if (categoryId == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { categoryMenuOpen = true }.padding(vertical = 12.dp),
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { categoryId = category.id; categoryMenuOpen = false },
                            )
                        }
                    }
                }
                Text(
                    "Voortgang wordt berekend uit wat je al naar deze categorie hebt overgeboekt " +
                        "(al je transacties tot nu toe, niet alleen vanaf vandaag).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onSave(name.trim(), target!!, categoryId!!) },
            ) { Text("Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

private fun parseEuroInput(text: String): Money? {
    if (text.isBlank()) return null
    val normalized = if (text.contains(",")) text else "$text,00"
    return runCatching { Money.parseCommaDecimal(normalized) }.getOrNull()
}
