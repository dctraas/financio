package com.financio.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.usecase.SplitValidation
import java.time.LocalDate

private data class BulkApplyPrompt(val accountId: Long, val counterpartyName: String, val categoryId: Long, val otherCount: Int)

/** A day-group header, a counterparty-group header, or one transaction row — see [groupedItems]. */
private sealed interface TransactionListItem {
    data class DayHeader(val date: LocalDate, val netCents: Long) : TransactionListItem
    data class CounterpartyHeader(val counterpartyName: String, val count: Int) : TransactionListItem
    data class Row(val transaction: Transaction) : TransactionListItem
}

@Composable
fun TransactionsScreen(onImportClick: () -> Unit, onOpenDetail: (Long) -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var categorizing by remember { mutableStateOf<Transaction?>(null) }
    var splitting by remember { mutableStateOf<Transaction?>(null) }
    var bulkApplyPrompt by remember { mutableStateOf<BulkApplyPrompt?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }

    // Whether the compact filter icon needs its "something is active" dot - the whole point of
    // hiding the controls behind an icon is that the icon itself still tells you when they're
    // doing something.
    val filtersActive = state.categoryFilter != CategoryFilter.All ||
        state.sort != TransactionSort.DATE_DESC ||
        state.selectedAccountId != null

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Financio", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onImportClick) { Icon(Icons.Filled.Add, contentDescription = "Importeren") }
                    },
                )
                CompactChrome(
                    searchExpanded = searchExpanded,
                    searchQuery = state.searchQuery,
                    filtersActive = filtersActive,
                    onSearchIconClick = { searchExpanded = true },
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSearchClose = { searchExpanded = false; viewModel.setSearchQuery("") },
                    onFilterIconClick = { filterSheetOpen = true },
                )
            }
        },
    ) { padding ->
        if (!state.hasUnfilteredTransactions) {
            EmptyTransactions(padding, onImportClick)
        } else if (state.transactions.isEmpty()) {
            NoFilterResults(padding, onClearFilters = viewModel::clearFilters)
        } else {
            val listItems = groupedItems(state.transactions, state.sort)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(listItems, key = { item ->
                    when (item) {
                        is TransactionListItem.DayHeader -> "header-${item.date}"
                        is TransactionListItem.CounterpartyHeader -> "header-${item.counterpartyName}"
                        is TransactionListItem.Row -> item.transaction.id
                    }
                }) { item ->
                    when (item) {
                        is TransactionListItem.DayHeader -> DayHeaderRow(item.date, item.netCents)
                        is TransactionListItem.CounterpartyHeader -> CounterpartyHeaderRow(item.counterpartyName, item.count)
                        is TransactionListItem.Row -> {
                            val transaction = item.transaction
                            TransactionRow(
                                transaction = transaction,
                                categoryName = state.categoriesById[transaction.categoryId]?.name,
                                isSplit = transaction.id in state.splitTransactionIds,
                                splits = state.splitsByTransaction[transaction.id].orEmpty(),
                                categoriesById = state.categoriesById,
                                // Tap opens the detail screen; long-press keeps the quick
                                // category-change flow that used to be behind a plain tap.
                                onClick = { onOpenDetail(transaction.id) },
                                onLongClick = { categorizing = transaction },
                            )
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }, sheetState = sheetState) {
            TransactionFilterSheet(state = state, viewModel = viewModel)
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

/**
 * Groups consecutive same-key transactions under one header — a plain `groupBy` on an
 * already-sorted list, in order (both sorts this groups for keep same-key transactions
 * contiguous, so `groupBy`'s encounter-order grouping reproduces the sort's own ranking). Only
 * attempted for the two date sorts and the counterparty-frequency sort: grouping an amount-sorted
 * list by date, say, would scatter one day's transactions into many tiny groups instead of one,
 * which is worse than no headers at all.
 */
private fun groupedItems(transactions: List<Transaction>, sort: TransactionSort): List<TransactionListItem> = when (sort) {
    TransactionSort.DATE_DESC, TransactionSort.DATE_ASC ->
        transactions.groupBy { it.date }.flatMap { (date, dayTransactions) ->
            listOf(TransactionListItem.DayHeader(date, dayTransactions.sumOf { it.amount.cents })) +
                dayTransactions.map { TransactionListItem.Row(it) }
        }
    TransactionSort.COUNTERPARTY_FREQUENCY_DESC ->
        transactions.groupBy { it.counterpartyName }.flatMap { (counterpartyName, group) ->
            listOf(TransactionListItem.CounterpartyHeader(counterpartyName, group.size)) +
                group.map { TransactionListItem.Row(it) }
        }
    else -> transactions.map { TransactionListItem.Row(it) }
}

@Composable
private fun DayHeaderRow(date: LocalDate, netCents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            date.dayHeaderLabel(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Money(netCents).toSignedDisplayString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Albert Heijn · 7 transacties" — the count is the whole point of this sort, so it's shown, not implied. */
@Composable
private fun CounterpartyHeaderRow(counterpartyName: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            counterpartyName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            if (count == 1) "1 transactie" else "$count transacties",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LocalDate.dayHeaderLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Vandaag"
        today.minusDays(1) -> "Gisteren"
        else -> toShortDisplayString()
    }
}

/**
 * The redesign's "chrome shrunk from 160px to 40px": two round icon buttons instead of an
 * always-visible search field, filter-chip row and sort text. Tapping search swaps this same
 * 40dp-tall row for an inline text field instead of pushing content down further.
 */
@Composable
private fun CompactChrome(
    searchExpanded: Boolean,
    searchQuery: String,
    filtersActive: Boolean,
    onSearchIconClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onFilterIconClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Zoeken op naam, omschrijving of tag") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onSearchClose) { Icon(Icons.Filled.Close, contentDescription = "Zoeken sluiten") }
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            RoundIconButton(onClick = onSearchIconClick, contentDescription = "Zoeken") { SearchIcon() }
            Box {
                RoundIconButton(onClick = onFilterIconClick, contentDescription = "Filteren") { FilterIcon() }
                if (filtersActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(LocalBudgetStatusColors.current.warning),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, contentDescription: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * The account filter, category filter chips and sort choice — previously always visible above the
 * list, now tucked behind the filter icon's bottom sheet.
 */
@Composable
private fun TransactionFilterSheet(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    // ModalBottomSheet doesn't scroll its content on its own (same lesson as AlertDialog above) -
    // on a small screen with several categories, the sheet would otherwise just cut off.
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Filteren en sorteren", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (state.accounts.size > 1) {
            Text(
                "Rekening",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Text(
            "Categorie",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Text(
            "Sorteren",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        Box {
            Text(
                "${state.sort.label} ▾",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
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

        if (state.categoryFilter != CategoryFilter.All || state.sort != TransactionSort.DATE_DESC) {
            Text(
                "Filters wissen",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = viewModel::clearFilters).padding(top = 20.dp),
            )
        }
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

@Composable
private fun NoFilterResults(padding: PaddingValues, onClearFilters: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(padding).padding(32.dp)) {
        Text("Geen transacties voor dit filter.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Wis filters →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClearFilters),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    transaction: Transaction,
    categoryName: String?,
    isSplit: Boolean,
    splits: List<TransactionSplit>,
    categoriesById: Map<Long, Category>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // A split transaction has its own categoryId nulled (see TransactionDao.setSplits), so without
    // isSplit it would look identical to a genuinely uncategorized one here.
    val uncategorized = categoryName == null && !isSplit
    val warningColor = LocalBudgetStatusColors.current.warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategorySquare(categoryName, isSplit = isSplit)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                if (isSplit) splitSubtitle(splits, categoriesById, transaction) else subtitleFor(categoryName, transaction),
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

/** "Verzorging €14,95 · Vakantie €10,00 · 4 sep" — the actual parts, not just the word "Gesplitst". */
private fun splitSubtitle(splits: List<TransactionSplit>, categoriesById: Map<Long, Category>, transaction: Transaction): String {
    if (splits.isEmpty()) return "Gesplitst · ${transaction.date.toShortDisplayString()}"
    val parts = splits.joinToString(" · ") { split ->
        val name = categoriesById[split.categoryId]?.name ?: "Onbekend"
        "$name ${split.amount.toDisplayString()}"
    }
    return "$parts · ${transaction.date.toShortDisplayString()}"
}

/**
 * A leading "+" for income is a UI-layer convention (see the schermontwerp mockup), not
 * something [Money] itself should know about — its own [Money.toDisplayString] only ever
 * signs negative amounts.
 */
internal fun Money.toSignedDisplayString(): String =
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
        verticalAlignment = Alignment.CenterVertically,
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
            // Plain text, not an icon: same reasoning as the ‹›-period navigator in Inzicht —
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
