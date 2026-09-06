package com.financio.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
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
import com.financio.core.model.TransactionSplit
import com.financio.core.usecase.SafeToSpendCalculator
import com.financio.core.usecase.SplitValidation

private data class BulkApplyPrompt(val accountId: Long, val counterpartyName: String, val categoryId: Long, val otherCount: Int)

@Composable
fun TransactionsScreen(onImportClick: () -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var categorizing by remember { mutableStateOf<Transaction?>(null) }
    var splitting by remember { mutableStateOf<Transaction?>(null) }
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
                state.safeToSpend?.let { SafeToSpendCard(it) }
                TransactionFilters(state = state, viewModel = viewModel)
                if (state.transactions.isEmpty()) {
                    NoFilterResults(onClearFilters = viewModel::clearFilters)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.transactions, key = { it.id }) { transaction ->
                            TransactionRow(
                                transaction = transaction,
                                categoryName = state.categoriesById[transaction.categoryId]?.name,
                                isSplit = transaction.id in state.splitTransactionIds,
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
                // Scoped to the same account as the bulk-apply itself (see applyCategoryToCounterparty) -
                // matters once "alle rekeningen" is the active filter and other accounts are in view too.
                val otherCount = state.transactions.count {
                    it.accountId == transaction.accountId && it.counterpartyName == transaction.counterpartyName && it.id != transaction.id
                }
                if (otherCount > 0) {
                    bulkApplyPrompt = BulkApplyPrompt(transaction.accountId, transaction.counterpartyName, categoryId, otherCount)
                }
            },
            onSplitClick = {
                categorizing = null
                splitting = transaction
            },
        )
    }

    bulkApplyPrompt?.let { prompt ->
        BulkApplyDialog(
            prompt = prompt,
            onConfirm = {
                viewModel.applyCategoryToCounterparty(prompt.accountId, prompt.counterpartyName, prompt.categoryId)
                bulkApplyPrompt = null
            },
            onDismiss = { bulkApplyPrompt = null },
        )
    }

    splitting?.let { transaction ->
        val currentSplits by viewModel.observeSplits(transaction.id).collectAsState(initial = emptyList())
        SplitDialog(
            transaction = transaction,
            categories = state.categories,
            currentSplits = currentSplits,
            onDismiss = { splitting = null },
            onSave = { splits ->
                viewModel.saveSplits(transaction.id, splits, fallbackCategoryId = transaction.categoryId)
                splitting = null
            },
            onClear = {
                viewModel.saveSplits(transaction.id, emptyList(), fallbackCategoryId = null)
                splitting = null
            },
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
    onSplitClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorie voor $transactionName") },
        text = {
            // AlertDialog doesn't scroll its content on its own — without this, a category list
            // longer than fits on screen just got cut off with no way to reach the rest.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    val isCurrent = category.id == currentCategoryId
                    Text(
                        category.name,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(category.id) }.padding(vertical = 12.dp),
                    )
                }
                Text(
                    "Splitsen over meerdere categorieën →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSplitClick).padding(top = 8.dp, bottom = 4.dp),
                )
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
 * "How much can I still safely spend this month" — current balance minus subscriptions expected
 * to bill before month-end, spread over the days left. See [SafeToSpendCalculator]. Only shown
 * once there's balance data to compute it from (an ING CSV import; MT940 or pre-migration
 * transactions carry no balance, in which case [TransactionsViewModel] omits it entirely).
 */
@Composable
private fun SafeToSpendCard(result: SafeToSpendCalculator.Result) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
    ) {
        Text(
            "Veilig te besteden deze maand",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            result.safeToSpendTotal.toDisplayString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            "${result.safeToSpendPerDay.toDisplayString()} / dag · nog ${result.daysRemaining} dagen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
        // Only shown once a second account actually exists - a single-account install (still the
        // common case) never sees this row at all.
        if (state.accounts.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedAccountId == null,
                        onClick = { viewModel.selectAccount(null) },
                        label = { Text("Alle rekeningen") },
                    )
                }
                items(state.accounts, key = { it.id }) { account ->
                    FilterChip(
                        selected = state.selectedAccountId == account.id,
                        onClick = { viewModel.selectAccount(account.id) },
                        label = { Text(account.name) },
                    )
                }
            }
        }

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
                    label = { Text("Alle (${state.totalCount})") },
                )
            }
            item {
                FilterChip(
                    selected = state.categoryFilter == CategoryFilter.Uncategorized,
                    onClick = { viewModel.setCategoryFilter(CategoryFilter.Uncategorized) },
                    label = { Text("Niet gecategoriseerd (${state.uncategorizedCount})") },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                FilterChip(
                    selected = state.categoryFilter == CategoryFilter.Specific(category.id),
                    onClick = { viewModel.setCategoryFilter(CategoryFilter.Specific(category.id)) },
                    label = { Text("${category.name} (${state.categoryCounts[category.id] ?: 0})") },
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
private fun TransactionRow(transaction: Transaction, categoryName: String?, isSplit: Boolean, onClick: () -> Unit) {
    // A split transaction has its own categoryId nulled (see TransactionDao.setSplits), so without
    // isSplit it would look identical to a genuinely uncategorized one here.
    val uncategorized = categoryName == null && !isSplit
    val warningColor = LocalBudgetStatusColors.current.warning

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryDot(if (isSplit) "gesplitst" else categoryName, warningColor)
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
                if (isSplit) "Gesplitst · ${transaction.date.toShortDisplayString()}" else subtitleFor(categoryName, transaction),
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

private data class SplitRowState(val categoryId: Long?, val amountText: String)

/**
 * Lets one transaction be divided across multiple categories — a single Albert Heijn receipt
 * that was half boodschappen, half drogisterij, say. [SplitValidation] (`:core`) is the single
 * source of truth for "is this a valid split"; this dialog just keeps the "Opslaan" button
 * disabled until it agrees. Amounts are entered as positive euro values regardless of whether the
 * transaction is a debit or credit — the sign is restored from [transaction.amount] when saving.
 */
@Composable
private fun SplitDialog(
    transaction: Transaction,
    categories: List<Category>,
    currentSplits: List<TransactionSplit>,
    onDismiss: () -> Unit,
    onSave: (List<TransactionSplit>) -> Unit,
    onClear: () -> Unit,
) {
    // Seeded once from whatever's already stored (or two blank rows for a fresh split) - a `Flow`
    // re-emission while the dialog is open (there shouldn't be one from elsewhere, but just in
    // case) must never clobber what the user is mid-typing.
    var rows by remember(transaction.id) {
        mutableStateOf(
            currentSplits.takeIf { it.isNotEmpty() }
                ?.map { SplitRowState(it.categoryId, centsToEuroInput(kotlin.math.abs(it.amount.cents))) }
                ?: listOf(SplitRowState(null, ""), SplitRowState(null, "")),
        )
    }

    val totalCents = kotlin.math.abs(transaction.amount.cents)
    val parsedAmounts = rows.map { euroInputToCents(it.amountText) }
    val sumCents = parsedAmounts.filterNotNull().sum()
    val validation = SplitValidation.validate(Money(totalCents), parsedAmounts.map { Money(it ?: 0) })
    val allAmountsParsed = parsedAmounts.all { it != null }
    val allCategoriesChosen = rows.all { it.categoryId != null }
    val isValid = validation.isValid && allAmountsParsed && allCategoriesChosen

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Splitsen: ${transaction.counterpartyName}") },
        text = {
            Column {
                Text(
                    "Totaal ${transaction.amount.toDisplayString()} verdelen over meerdere categorieën.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                rows.forEachIndexed { index, row ->
                    SplitRowEditor(
                        row = row,
                        categories = categories,
                        onChange = { updated -> rows = rows.toMutableList().also { it[index] = updated } },
                        onRemove = if (rows.size > 2) {
                            { rows = rows.toMutableList().also { it.removeAt(index) } }
                        } else {
                            null
                        },
                    )
                }
                Text(
                    "+ Rij toevoegen",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { rows = rows + SplitRowState(null, "") }.padding(vertical = 8.dp),
                )
                val remaining = totalCents - sumCents
                val statusColors = LocalBudgetStatusColors.current
                Text(
                    if (allAmountsParsed && remaining == 0L) "Klopt precies" else "Nog te verdelen: ${Money(remaining).toDisplayString()}",
                    color = if (allAmountsParsed && remaining == 0L) statusColors.ok else statusColors.warning,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (currentSplits.isNotEmpty()) {
                    Text(
                        "Splitsing verwijderen →",
                        color = statusColors.over,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onClear).padding(top = 16.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val sign = if (transaction.amount.cents < 0) -1 else 1
                    val splits = rows.map { row ->
                        TransactionSplit(
                            transactionId = transaction.id,
                            categoryId = row.categoryId!!,
                            amount = Money(sign * (euroInputToCents(row.amountText) ?: 0)),
                        )
                    }
                    onSave(splits)
                },
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun SplitRowEditor(
    row: SplitRowState,
    categories: List<Category>,
    onChange: (SplitRowState) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val categoryName = categories.firstOrNull { it.id == row.categoryId }?.name ?: "Kies categorie"

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                categoryName,
                color = if (row.categoryId == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable { categoryMenuOpen = true }.padding(vertical = 12.dp),
            )
            DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onChange(row.copy(categoryId = category.id))
                            categoryMenuOpen = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = row.amountText,
            onValueChange = { onChange(row.copy(amountText = it)) },
            singleLine = true,
            prefix = { Text("€") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.width(110.dp),
        )
        if (onRemove != null) {
            // Plain text, not an icon: same reasoning as the ‹›-period navigator in Grafieken —
            // no build available here to verify an icon actually ships in the trimmed icon set.
            Text(
                "✕",
                color = LocalBudgetStatusColors.current.over,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

private fun centsToEuroInput(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    return "${absCents / 100},${(absCents % 100).toString().padStart(2, '0')}"
}

private fun euroInputToCents(text: String): Long? {
    if (text.isBlank()) return null
    val normalized = if (text.contains(",")) text else "$text,00"
    return runCatching { Money.parseCommaDecimal(normalized).cents }.getOrNull()
}
