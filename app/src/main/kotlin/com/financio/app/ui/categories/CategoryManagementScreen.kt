package com.financio.app.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType

@Composable
fun CategoryManagementScreen(viewModel: CategoryManagementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var newCategoryName by remember { mutableStateOf("") }
    var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }
    var rulePendingDelete by remember { mutableStateOf<CategoryRule?>(null) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    val categoriesById = state.categories.associateBy { it.id }

    Scaffold(topBar = { TopAppBar(title = { Text("Categorieën & regels") }) }) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item { SectionHeader("Categorieën") }
            items(state.categories, key = { "cat-${it.id}" }) { category ->
                CategoryRow(category, onDelete = { categoryPendingDelete = category })
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = { Text("Nieuwe categorie") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.addCategory(newCategoryName); newCategoryName = "" },
                        enabled = newCategoryName.isNotBlank(),
                    ) { Icon(Icons.Filled.Add, contentDescription = "Categorie toevoegen") }
                }
            }

            item { SectionHeader("Regels") }
            if (state.rules.isEmpty()) {
                item {
                    Text(
                        "Nog geen handmatige regels — die kun je hieronder toevoegen, of ze ontstaan " +
                            "automatisch zodra je een transactie tijdens het importeren categoriseert.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.rules, key = { "rule-${it.id}" }) { rule ->
                    ManagedRuleRow(rule, categoriesById[rule.categoryId], onDelete = { rulePendingDelete = rule })
                }
            }
            item {
                TextButton(
                    onClick = { showAddRuleDialog = true },
                    enabled = state.categories.isNotEmpty(),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text("+ Nieuwe regel") }
            }
        }
    }

    categoryPendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryPendingDelete = null },
            title = { Text("'${category.name}' verwijderen?") },
            text = {
                Text(
                    "Transacties in deze categorie worden niet-gecategoriseerd, en regels die naar " +
                        "deze categorie wijzen worden ook verwijderd.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category.id)
                    categoryPendingDelete = null
                }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { categoryPendingDelete = null }) { Text("Annuleren") } },
        )
    }

    rulePendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { rulePendingDelete = null },
            title = { Text("Regel verwijderen?") },
            text = { Text("'${rule.pattern}' wordt niet meer automatisch gecategoriseerd op basis van deze regel.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule.id)
                    rulePendingDelete = null
                }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { rulePendingDelete = null }) { Text("Annuleren") } },
        )
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            categories = state.categories,
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { categoryId, matchType, pattern ->
                viewModel.addRule(categoryId, matchType, pattern)
                showAddRuleDialog = false
            },
        )
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
private fun CategoryRow(category: Category, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(11.dp)) {
            drawCircle(runCatching { Color(android.graphics.Color.parseColor(category.colorHex)) }.getOrDefault(Color.Gray))
        }
        Text(category.name, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Verwijder ${category.name}") }
    }
}

@Composable
private fun ManagedRuleRow(rule: CategoryRule, category: Category?, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.pattern, fontWeight = FontWeight.SemiBold)
            Text(
                "${matchTypeLabel(rule.matchType)} → ${category?.name ?: "onbekende categorie"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Verwijder regel voor ${rule.pattern}") }
    }
}

@Composable
private fun AddRuleDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: Long, matchType: MatchType, pattern: String) -> Unit,
) {
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var matchType by remember { mutableStateOf(MatchType.KEYWORD) }
    var pattern by remember { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nieuwe regel") },
        text = {
            Column {
                Text("Categorie", style = MaterialTheme.typography.labelMedium)
                Column {
                    Text(
                        categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Kies categorie ▾",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { categoryMenuOpen = true }.padding(vertical = 8.dp),
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryMenuOpen = false },
                            )
                        }
                    }
                }

                Text("Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    FilterChip(
                        selected = matchType == MatchType.KEYWORD,
                        onClick = { matchType = MatchType.KEYWORD },
                        label = { Text("Trefwoord") },
                    )
                    FilterChip(
                        selected = matchType == MatchType.COUNTERPARTY_EXACT,
                        onClick = { matchType = MatchType.COUNTERPARTY_EXACT },
                        label = { Text("Tegenrekening") },
                    )
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    placeholder = {
                        Text(if (matchType == MatchType.KEYWORD) "bijv. Albert Heijn" else "bijv. NL12INGB0001234567")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedCategoryId?.let { onConfirm(it, matchType, pattern) } },
                enabled = selectedCategoryId != null && pattern.isNotBlank(),
            ) { Text("Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

private fun matchTypeLabel(matchType: MatchType) = when (matchType) {
    MatchType.COUNTERPARTY_EXACT -> "Tegenrekening"
    MatchType.KEYWORD -> "Trefwoord"
}
