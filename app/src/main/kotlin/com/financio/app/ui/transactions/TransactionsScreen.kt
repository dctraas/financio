package com.financio.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.CategoryColors
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction

@Composable
fun TransactionsScreen(onImportClick: () -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var categorizing by remember { mutableStateOf<Transaction?>(null) }

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
        if (state.transactions.isEmpty()) {
            EmptyTransactions(padding, onImportClick)
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                items(state.transactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        categoryName = state.categoriesById[transaction.categoryId]?.name,
                        onClick = { if (transaction.categoryId == null) categorizing = transaction },
                    )
                }
            }
        }
    }

    categorizing?.let { transaction ->
        CategoryPickerDialog(
            transactionName = transaction.counterpartyName,
            categories = state.categories,
            onDismiss = { categorizing = null },
            onSelect = { categoryId ->
                viewModel.categorize(transaction, categoryId)
                categorizing = null
            },
        )
    }
}

@Composable
private fun CategoryPickerDialog(
    transactionName: String,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorie voor $transactionName") },
        text = {
            Column {
                categories.forEach { category ->
                    Text(
                        category.name,
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

@Composable
private fun TransactionRow(transaction: Transaction, categoryName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryDot(categoryName)
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.counterpartyName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(
                categoryName ?: "Te categoriseren",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun CategoryDot(categoryName: String?) {
    val color = categoryColorFor(categoryName)
    androidx.compose.foundation.Canvas(Modifier.size(11.dp)) { drawCircle(color) }
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
