package com.financio.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorizationConflictDialog
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.toSignedMagnitudeString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.usecase.SplitValidation
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private data class BulkApplyPrompt(val accountId: Long, val counterpartyName: String, val categoryId: Long, val otherCount: Int)

/** A day-group header, a counterparty-group header, or one transaction row — see [groupedItems]. */
private sealed interface TransactionListItem {
    data class DayHeader(val date: LocalDate, val netCents: Long) : TransactionListItem
    data class CounterpartyHeader(val counterpartyName: String, val count: Int) : TransactionListItem
    data class Row(val transaction: Transaction) : TransactionListItem
}

@Composable
fun TransactionsScreen(
    onImportClick: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    startWithUncategorizedFilter: Boolean = false,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // "Nu doen" on Vandaag's uncategorized tile navigates here expecting the filter already
    // applied, not a fresh, unfiltered list the user then has to filter themselves.
    LaunchedEffect(startWithUncategorizedFilter) {
        if (startWithUncategorizedFilter) viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
    }

    var categorizing by remember { mutableStateOf<Transaction?>(null) }
    var splitting by remember { mutableStateOf<Transaction?>(null) }
    var bulkApplyPrompt by remember { mutableStateOf<BulkApplyPrompt?>(null) }
    var categorySheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TransactionsHeader(sort = state.sort, onSortSelect = viewModel::setSort)
                SearchField(query = state.searchQuery, onQueryChange = viewModel::setSearchQuery)
                FilterChipsRow(
                    state = state,
                    onSelectAll = { viewModel.setCategoryFilter(CategoryFilter.All) },
                    onSelectUncategorized = { viewModel.setCategoryFilter(CategoryFilter.Uncategorized) },
                    onOpenCategorySheet = { categorySheetOpen = true },
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
                itemsIndexed(listItems, key = { _, item ->
                    when (item) {
                        is TransactionListItem.DayHeader -> "header-${item.date}"
                        is TransactionListItem.CounterpartyHeader -> "header-${item.counterpartyName}"
                        is TransactionListItem.Row -> item.transaction.id
                    }
                }) { index, item ->
                    // A divider only ever separates two rows within the same group - never right
                    // under a header (its own bottom spacing already reads as a boundary) and
                    // never between the last row of one group and the next header.
                    if (item is TransactionListItem.Row && listItems.getOrNull(index - 1) is TransactionListItem.Row) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    }
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
                                // Tap opens the detail screen; long-press (or the "Categorie
                                // kiezen" pill's own tap, for an uncategorized row) keeps the
                                // quick category-change flow that used to be behind a plain tap.
                                onClick = { onOpenDetail(transaction.id) },
                                onLongClick = { categorizing = transaction },
                            )
                        }
                    }
                }
            }
        }
    }

    if (categorySheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { categorySheetOpen = false }, sheetState = sheetState) {
            CategorySheet(state = state, viewModel = viewModel, onDismiss = { categorySheetOpen = false })
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
            },
            onSplitClick = {
                categorizing = null
                splitting = transaction
            },
        )
    }

    // Only fires once categorize() actually persisted - never after a keyword-scoped conflict
    // resolution, where bulk-applying to every same-counterparty transaction would defeat the
    // whole point of scoping the new rule down in the first place (see AppliedCategorization).
    LaunchedEffect(state.appliedCategorization) {
        val applied = state.appliedCategorization ?: return@LaunchedEffect
        // Computed from what's already loaded, not a fresh query: good enough to decide whether
        // the follow-up prompt is worth showing at all. Scoped to the same account as the
        // bulk-apply itself (see applyCategoryToCounterparty) - matters once "alle rekeningen" is
        // the active filter and other accounts are in view too.
        val otherCount = state.transactions.count {
            it.accountId == applied.transaction.accountId && it.counterpartyName == applied.transaction.counterpartyName && it.id != applied.transaction.id
        }
        if (otherCount > 0) {
            bulkApplyPrompt = BulkApplyPrompt(applied.transaction.accountId, applied.transaction.counterpartyName, applied.categoryId, otherCount)
        }
        viewModel.consumeAppliedCategorization()
    }

    state.categorizationConflict?.let { conflict ->
        CategorizationConflictDialog(
            counterpartyName = conflict.transaction.counterpartyName,
            existingCategoryName = conflict.existingCategoryName,
            previewCount = viewModel::previewConflictKeywordCount,
            onApplyToAll = viewModel::resolveConflictForAll,
            onApplyToKeyword = viewModel::resolveConflictWithKeyword,
            onDismiss = viewModel::cancelConflict,
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

/** "MA 14 SEPTEMBER" — always the full weekday+date, no "Vandaag"/"Gisteren" special-casing, per the schermontwerp spec. */
private fun LocalDate.dayHeaderLabel(): String =
    "${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("nl")).uppercase()} $dayOfMonth ${month.getDisplayName(TextStyle.FULL, Locale("nl")).uppercase()}"

@Composable
private fun DayHeaderRow(date: LocalDate, netCents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            date.dayHeaderLabel(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
        )
        Text(
            Money(netCents).toSignedMagnitudeString(),
            fontSize = 12.sp,
            color = LocalFinancioColors.current.inkFaint,
        )
    }
}

/** "Albert Heijn · 7 transacties" — the count is the whole point of this sort, so it's shown, not implied. */
@Composable
private fun CounterpartyHeaderRow(counterpartyName: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            counterpartyName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            if (count == 1) "1 transactie" else "$count transacties",
            fontSize = 12.sp,
            color = LocalFinancioColors.current.inkFaint,
        )
    }
}

@Composable
private fun TransactionsHeader(sort: TransactionSort, onSortSelect: (TransactionSort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Transacties", style = MaterialTheme.typography.titleLarge)
        SortButton(sort, onSortSelect)
    }
}

@Composable
private fun SortButton(sort: TransactionSort, onSelect: (TransactionSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sorteren", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TransactionSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
        // No checkmark glyph in the trimmed icon set available here (same reasoning as the SplitDialog's plain "✕") - bolding the active option is enough of a signal in a short list.
    }
}

/** 48dp, always visible - the redesign drops the old tap-to-expand search icon in favor of a field that's just always there. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides iconColor) { SearchIcon() }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Zoek op winkel of omschrijving",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "Alles · N", "Zonder categorie · N" (always amber-tinted, whether active or not - the schermontwerp
 * reserves that tint for this one filter, matching the same convention as Vandaag's task card and
 * the import flow's "needs a category" status card) and "Categorie" (opens [CategorySheet], and
 * shows the picked category's own name+count once one is selected, instead of the generic label).
 */
@Composable
private fun FilterChipsRow(
    state: TransactionsUiState,
    onSelectAll: () -> Unit,
    onSelectUncategorized: () -> Unit,
    onOpenCategorySheet: () -> Unit,
) {
    val specific = state.categoryFilter as? CategoryFilter.Specific
    val categoryChipLabel = specific
        ?.let { filter -> state.categoriesById[filter.categoryId]?.name }
        ?.let { name -> "$name · ${state.categoryCounts[specific.categoryId] ?: 0}" }
        ?: "Categorie"

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val active = state.categoryFilter == CategoryFilter.All
            FilterPill(
                "Alles · ${state.totalCount}",
                selected = active,
                containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                borderColor = if (active) null else MaterialTheme.colorScheme.outline,
                onClick = onSelectAll,
            )
        }
        item {
            FilterPill(
                "Zonder categorie · ${state.uncategorizedCount}",
                selected = state.categoryFilter == CategoryFilter.Uncategorized,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                borderColor = MaterialTheme.colorScheme.secondary,
                onClick = onSelectUncategorized,
            )
        }
        item {
            FilterPill(
                categoryChipLabel,
                selected = specific != null,
                containerColor = if (specific != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (specific != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                borderColor = if (specific != null) null else MaterialTheme.colorScheme.outline,
                onClick = onOpenCategorySheet,
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .let { if (borderColor != null) it.border(1.dp, borderColor, RoundedCornerShape(999.dp)) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

/**
 * "Categorie" chip's bottom sheet: the full category list (spec), plus - since it isn't part of
 * the mockup's single-account scenario but is real, already-shipped functionality - the account
 * switcher that used to live in the old filter sheet, shown only once there's more than one
 * account to choose from.
 */
@Composable
private fun CategorySheet(state: TransactionsUiState, viewModel: TransactionsViewModel, onDismiss: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Categorie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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

        CategorySheetRow("Alle transacties", selected = state.categoryFilter == CategoryFilter.All, count = state.totalCount) {
            viewModel.setCategoryFilter(CategoryFilter.All)
            onDismiss()
        }
        CategorySheetRow("Zonder categorie", selected = state.categoryFilter == CategoryFilter.Uncategorized, count = state.uncategorizedCount) {
            viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
            onDismiss()
        }
        state.categories.forEach { category ->
            CategorySheetRow(
                category.name,
                selected = state.categoryFilter == CategoryFilter.Specific(category.id),
                count = state.categoryCounts[category.id] ?: 0,
            ) {
                viewModel.setCategoryFilter(CategoryFilter.Specific(category.id))
                onDismiss()
            }
        }
    }
}

@Composable
private fun CategorySheetRow(name: String, selected: Boolean, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategorySquare(categoryName, isSplit = isSplit)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    transaction.counterpartyName,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // ING's own "Tag" label from Mijn ING (e.g. "Vakantie 2024") - independent of
                // Financio's categories, so it's shown alongside rather than folded into one.
                transaction.tag?.let { tag -> TagChip(tag) }
            }
            when {
                uncategorized -> UncategorizedPill(onClick = onLongClick)
                isSplit -> Text(
                    splitSubtitle(splits, categoriesById),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    categoryName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val isIncome = transaction.amount.cents > 0
        Text(
            transaction.amount.toSignedMagnitudeString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isIncome) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The "needs attention" signal itself lives here now, not on [CategorySquare] anymore - tapping it opens the same quick categorize dialog a long-press on the row does. */
@Composable
private fun UncategorizedPill(onClick: () -> Unit) {
    Text(
        "Categorie kiezen",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
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

/** "Uit eten · gesplitst in 2" — the first part's category plus the count, not just the word "Gesplitst". */
private fun splitSubtitle(splits: List<TransactionSplit>, categoriesById: Map<Long, Category>): String {
    if (splits.isEmpty()) return "Gesplitst"
    val firstCategoryName = categoriesById[splits.first().categoryId]?.name ?: "Gesplitst"
    return "$firstCategoryName · gesplitst in ${splits.size}"
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
