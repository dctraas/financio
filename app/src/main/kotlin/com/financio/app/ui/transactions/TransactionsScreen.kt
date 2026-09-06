package com.financio.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.CategoryColors
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction

private data class BulkApplyPrompt(val counterpartyName: String, val categoryId: Long, val otherCount: Int)

@Composable
fun TransactionsScreen(onImportClick: () -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var categorizing by remember { mutableStateOf<Transaction?>(null) }
    var bulkApplyPrompt by remember { mutableStateOf<BulkApplyPrompt?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financio", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onImportClick) { Icon(Icons.Filled.Add, contentDescription = "Importeren") }
                },
            )
        },
    ) { padding ->
        if (!state.hasUnfilteredTransactions) {
            EmptyTransactions(padding, onImportClick)
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                TransactionFilters(state = state, viewModel = viewModel)
                if (state.transactions.isEmpty()) {
                    NoFilterResults(onClearFilters = viewModel::clearFilters)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.transactions, key = { it.id }) { transaction ->
                            TransactionRow(
                                transaction = transaction,
                                categoryName = state.categoriesById[transaction.categoryId]?.name,
                                // Always editable, not just when uncategorized: an automatically
                                // assigned category can be wrong, and there was previously no way
                                // to fix that here.
                                onClick = { categorizing = transaction },
                            )
                        }
                    }
                }
            }
        }
    }

    categorizing?.let { transaction ->
        CategoryPickerDialog(
            transactionName = transaction.counterpartyName,
            categories = state.categories,
            currentCategoryId = transaction.categoryId,
            onDismiss = { categorizing = null },
            onSelect = { categoryId ->
                viewModel.categorize(transaction, categoryId)
                categorizing = null
                // Computed from what's already loaded, not a fresh query: good enough to decide
                // whether the follow-up prompt is worth showing at all.
                val otherCount = state.transactions.count {
                    it.counterpartyName == transaction.counterpartyName && it.id != transaction.id
                }
                if (otherCount > 0) {
                    bulkApplyPrompt = BulkApplyPrompt(transaction.counterpartyName, categoryId, otherCount)
                }
            },
        )
    }

    bulkApplyPrompt?.let { prompt ->
        BulkApplyDialog(
            prompt = prompt,
            onConfirm = {
                viewModel.applyCategoryToCounterparty(prompt.counterpartyName, prompt.categoryId)
                bulkApplyPrompt = null
            },
            onDismiss = { bulkApplyPrompt = null },
        )
    }
}

@Composable
private fun CategoryPickerDialog(
    transactionName: String,
    categories: List<Category>,
    currentCategoryId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorie voor $transactionName") },
        text = {
            Column {
                categories.forEach { category ->
                    val isCurrent = category.id == currentCategoryId
                    Text(
                        category.name,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(category.id) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun BulkApplyDialog(prompt: BulkApplyPrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ook toepassen op de rest?") },
        text = {
            Text(
                "${prompt.otherCount} andere transacties van '${prompt.counterpartyName}' krijgen dan " +
                    "dezelfde categorie.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Toepassen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Nee, alleen deze") } },
    )
}

@Composable
private fun EmptyTransactions(padding: PaddingValues, onImportClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nog geen transacties", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Importeer een CSV- of MT940-export uit Mijn ING om te beginnen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Importeren →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onImportClick),
        )
    }
}

/**
 * Search, category filter chips and a sort choice — the same three controls competing budgeting
 * apps (bunq, YNAB, Buddy) put above their transaction list. Filtering/sorting happens in the
 * ViewModel over the already-loaded list rather than in SQL: a personal account's history is
 * small enough that this is simpler than pushing every filter combination into a query.
 */
@Composable
private fun TransactionFilters(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text("Zoeken op naam, omschrijving of tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyRow(
            contentPadding = PaddingValues(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.categoryFilter == CategoryFilter.All,
                    onClick = { viewModel.setCategoryFilter(CategoryFilter.All) },
                    label = { Text("Alle") },
                )
            }
            item {
                FilterChip(
                    selected = state.categoryFilter == CategoryFilter.Uncategorized,
                    onClick = { viewModel.setCategoryFilter(CategoryFilter.Uncategorized) },
                    label = { Text("Niet gecategoriseerd") },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                FilterChip(
                    selected = state.categoryFilter == CategoryFilter.Specific(category.id),
                    onClick = { viewModel.setCategoryFilter(CategoryFilter.Specific(category.id)) },
                    label = { Text(category.name) },
                )
            }
        }

        Box {
            Text(
                "Sorteren: ${state.sort.label} ▾",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { sortMenuOpen = true }.padding(vertical = 4.dp),
            )
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                TransactionSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { viewModel.setSort(option); sortMenuOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoFilterResults(onClearFilters: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp)) {
        Text("Geen transacties voor dit filter.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Wis filters →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClearFilters),
        )
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, categoryName: String?, onClick: () -> Unit) {
    val uncategorized = categoryName == null
    val warningColor = LocalBudgetStatusColors.current.warning

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryDot(categoryName, warningColor)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    transaction.counterpartyName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // ING's own "Tag" label from Mijn ING (e.g. "Vakantie 2024") - independent of
                // Financio's categories, so it's shown alongside rather than folded into one.
                transaction.tag?.let { tag -> TagChip(tag) }
            }
            Text(
                subtitleFor(categoryName, transaction),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (uncategorized) FontWeight.SemiBold else FontWeight.Normal,
                color = if (uncategorized) warningColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val isIncome = transaction.amount.cents > 0
        Text(
            transaction.amount.toSignedDisplayString(),
            fontWeight = FontWeight.SemiBold,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    Text(
        tag,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "Boodschappen · 4 sep" when categorized, or an unmissable "Tik om te categoriseren · 4 sep" when not. */
private fun subtitleFor(categoryName: String?, transaction: Transaction): String {
    val label = categoryName ?: "Tik om te categoriseren"
    return "$label · ${transaction.date.toShortDisplayString()}"
}

/**
 * A filled dot for an actual category (including "Overig" in its own neutral gray) versus an
 * *outlined* dot in the budget-warning amber for "no category yet" — a plain gray fill would be
 * ambiguous with "Overig", which is a real, deliberately chosen category, not a missing one.
 */
@Composable
private fun CategoryDot(categoryName: String?, warningColor: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
        if (categoryName == null) {
            drawCircle(warningColor, style = Stroke(width = 1.5.dp.toPx()))
        } else {
            drawCircle(categoryColorFor(categoryName))
        }
    }
}

private fun categoryColorFor(categoryName: String?): Color = when (categoryName?.lowercase()) {
    "boodschappen" -> CategoryColors.groceries
    "abonnementen" -> CategoryColors.subscriptions
    "uit eten" -> CategoryColors.dining
    "vervoer" -> CategoryColors.transport
    "kleding & verzorging" -> CategoryColors.clothing
    "wonen & vaste lasten" -> CategoryColors.housing
    "gezondheid & verzekering" -> CategoryColors.health
    "vrije tijd & hobby's" -> CategoryColors.leisure
    "vakantie & reizen" -> CategoryColors.travel
    "cadeaus & giften" -> CategoryColors.gifts
    "sparen & beleggen" -> CategoryColors.savings
    "inkomsten" -> CategoryColors.income
    // "Overig" and anything user-created falls through to the neutral dot on purpose.
    else -> CategoryColors.fallback
}

/**
 * A leading "+" for income is a UI-layer convention (see the schermontwerp mockup), not
 * something [Money] itself should know about — its own [Money.toDisplayString] only ever
 * signs negative amounts.
 */
private fun Money.toSignedDisplayString(): String =
    if (cents > 0) "+${toDisplayString()}" else toDisplayString()
