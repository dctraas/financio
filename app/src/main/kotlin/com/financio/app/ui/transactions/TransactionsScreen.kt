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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.financio.app.data.local.TransactionDensity
import com.financio.app.ui.common.CategorizationConflictDialog
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.toSignedMagnitudeString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.SavedTransactionFilter
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
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
    onPlayCategorize: () -> Unit,
    startWithUncategorizedFilter: Boolean = false,
    startWithFilterId: Long? = null,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // "Nu doen" on Vandaag's uncategorized tile navigates here expecting the filter already
    // applied, not a fresh, unfiltered list the user then has to filter themselves.
    LaunchedEffect(startWithUncategorizedFilter) {
        if (startWithUncategorizedFilter) viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
    }

    // Vandaag's pinned saved-filter chips deep-link here with just an id (see FinancioNavHost's
    // `filterId` nav arg) - the actual filter fields still live in AppPreferences.
    LaunchedEffect(startWithFilterId) {
        if (startWithFilterId != null) viewModel.applySavedFilterById(startWithFilterId)
    }

    var categorizing by remember { mutableStateOf<Transaction?>(null) }
    var splitting by remember { mutableStateOf<Transaction?>(null) }
    var bulkApplyPrompt by remember { mutableStateOf<BulkApplyPrompt?>(null) }
    var categorySheetOpen by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var bulkCategoryPickerOpen by remember { mutableStateOf(false) }

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
                    onOpenFilterSheet = { filterSheetOpen = true },
                    onToggleBulkMode = viewModel::toggleBulkMode,
                )
                // Only worth the extra row while actually looking at "Zonder categorie" - the
                // swipe/suggest/confirm game (see CategorizeQueueScreen) is exactly this filter's
                // own backlog, one counterparty-groep at a time instead of one row at a time.
                if (state.categoryFilter == CategoryFilter.Uncategorized && state.uncategorizedCount > 0) {
                    Text(
                        "Speel categoriseren →",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlayCategorize).padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    )
                }
            }
        },
        bottomBar = {
            if (state.bulkModeEnabled) {
                BulkActionBar(
                    selectedCount = state.selectedTransactionIds.size,
                    onCancel = viewModel::toggleBulkMode,
                    onAssignCategory = { bulkCategoryPickerOpen = true },
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
                        is TransactionListItem.DayHeader -> DayHeaderRow(item.date, item.netCents, showCentsEnabled = state.showCentsEnabled)
                        is TransactionListItem.CounterpartyHeader -> CounterpartyHeaderRow(item.counterpartyName, item.count)
                        is TransactionListItem.Row -> {
                            val transaction = item.transaction
                            TransactionRow(
                                transaction = transaction,
                                categoryName = state.categoriesById[transaction.categoryId]?.name,
                                isSplit = transaction.id in state.splitTransactionIds,
                                splits = state.splitsByTransaction[transaction.id].orEmpty(),
                                categoriesById = state.categoriesById,
                                density = state.transactionDensity,
                                showCentsEnabled = state.showCentsEnabled,
                                bulkModeEnabled = state.bulkModeEnabled,
                                selected = transaction.id in state.selectedTransactionIds,
                                // Tap opens the detail screen; long-press (or the "Categorie
                                // kiezen" pill's own tap, for an uncategorized row) keeps the
                                // quick category-change flow that used to be behind a plain tap.
                                // In bulk mode, a tap toggles selection instead of either.
                                onClick = {
                                    if (state.bulkModeEnabled) viewModel.toggleTransactionSelected(transaction.id) else onOpenDetail(transaction.id)
                                },
                                onLongClick = { if (!state.bulkModeEnabled) categorizing = transaction },
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

    if (filterSheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }, sheetState = sheetState) {
            FilterSheet(state = state, viewModel = viewModel)
        }
    }

    if (bulkCategoryPickerOpen) {
        CategoryPickerDialog(
            transactionName = "${state.selectedTransactionIds.size} transacties",
            categories = state.categories,
            currentCategoryId = null,
            onDismiss = { bulkCategoryPickerOpen = false },
            onSelect = { categoryId ->
                viewModel.bulkCategorize(categoryId)
                bulkCategoryPickerOpen = false
            },
        )
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
private fun DayHeaderRow(date: LocalDate, netCents: Long, showCentsEnabled: Boolean) {
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
            Money(netCents).toSignedMagnitudeString(showCentsEnabled),
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
    onOpenFilterSheet: () -> Unit,
    onToggleBulkMode: () -> Unit,
) {
    val specific = state.categoryFilter as? CategoryFilter.Specific
    val categoryChipLabel = specific
        ?.let { filter -> state.categoriesById[filter.categoryId]?.name }
        ?.let { name -> "$name · ${state.categoryCounts[specific.categoryId] ?: 0}" }
        ?: "Categorie"
    val advancedFilterActive = state.minAmountCents != null || state.maxAmountCents != null || state.dateFrom != null || state.dateTo != null

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
        item {
            // Opens the amount/date/opgeslagen-filters sheet - stays highlighted while an
            // amount or date bound is active, same "selected" signal the other chips use.
            FilterPill(
                "Filters",
                selected = advancedFilterActive,
                containerColor = if (advancedFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (advancedFilterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                borderColor = if (advancedFilterActive) null else MaterialTheme.colorScheme.outline,
                onClick = onOpenFilterSheet,
            )
        }
        item {
            FilterPill(
                if (state.bulkModeEnabled) "Selecteren · ${state.selectedTransactionIds.size}" else "Selecteren",
                selected = state.bulkModeEnabled,
                containerColor = if (state.bulkModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (state.bulkModeEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                borderColor = if (state.bulkModeEnabled) null else MaterialTheme.colorScheme.outline,
                onClick = onToggleBulkMode,
            )
        }
    }
}

/** The bulk-edit mode's bottom bar - "N geselecteerd" plus the one bulk action Transacties supports today (assigning a category), mirroring MerchantManagementScreen's own multi-select bottom bar. */
@Composable
private fun BulkActionBar(selectedCount: Int, onCancel: () -> Unit, onAssignCategory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selectedCount == 1) "1 geselecteerd" else "$selectedCount geselecteerd",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Annuleren") }
            Button(onClick = onAssignCategory, enabled = selectedCount > 0) { Text("Categorie toewijzen") }
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

/**
 * "Filters" chip's bottom sheet: bedrag- en datumgrenzen, plus the "opgeslagen filters" list
 * (opslaan/toepassen/pinnen op Vandaag/verwijderen) — one shared home for #49/#50/#52/#53, since
 * they're all just different views onto the same underlying filter state.
 */
@Composable
private fun FilterSheet(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    var minText by remember(state.minAmountCents) { mutableStateOf(state.minAmountCents?.let(::formatEuroInputCents) ?: "") }
    var maxText by remember(state.maxAmountCents) { mutableStateOf(state.maxAmountCents?.let(::formatEuroInputCents) ?: "") }
    var fromText by remember(state.dateFrom) { mutableStateOf(state.dateFrom?.toString() ?: "") }
    var toText by remember(state.dateTo) { mutableStateOf(state.dateTo?.toString() ?: "") }
    var newFilterName by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text(
            "Bedrag",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it; viewModel.setMinAmountCents(parseEuroInputToCents(it)) },
                label = { Text("Min") },
                prefix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it; viewModel.setMaxAmountCents(parseEuroInputToCents(it)) },
                label = { Text("Max") },
                prefix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            "Datum",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromText,
                onValueChange = { fromText = it; viewModel.setDateFrom(parseDateInput(it)) },
                label = { Text("Vanaf") },
                placeholder = { Text("JJJJ-MM-DD") },
                singleLine = true,
                isError = fromText.isNotBlank() && parseDateInput(fromText) == null,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = toText,
                onValueChange = { toText = it; viewModel.setDateTo(parseDateInput(it)) },
                label = { Text("Tot en met") },
                placeholder = { Text("JJJJ-MM-DD") },
                singleLine = true,
                isError = toText.isNotBlank() && parseDateInput(toText) == null,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            "Wis filters",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = viewModel::clearFilters),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        Text(
            "Opgeslagen filters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (state.savedFilters.isEmpty()) {
            Text(
                "Nog geen opgeslagen filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.savedFilters.forEach { filter -> SavedFilterRow(filter, viewModel) }
        }

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newFilterName,
                onValueChange = { newFilterName = it },
                label = { Text("Filter opslaan als...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.saveCurrentFilterAsNew(newFilterName); newFilterName = "" },
                enabled = newFilterName.isNotBlank(),
            ) { Text("Opslaan") }
        }
    }
}

@Composable
private fun SavedFilterRow(filter: SavedTransactionFilter, viewModel: TransactionsViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            filter.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false).clickable { viewModel.applySavedFilter(filter) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (filter.pinnedOnVandaag) "Op Vandaag ✓" else "Pin op Vandaag",
                style = MaterialTheme.typography.labelMedium,
                color = if (filter.pinnedOnVandaag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (filter.pinnedOnVandaag) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { viewModel.setSavedFilterPinned(filter.id, !filter.pinnedOnVandaag) },
            )
            Text(
                "✕",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.deleteSavedFilter(filter.id) },
            )
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
    onSplitClick: (() -> Unit)? = null,
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
                // Splitsen only makes sense for one specific transaction - the bulk-categorize
                // picker (multiple transactions at once) omits it by passing onSplitClick = null.
                if (onSplitClick != null) {
                    Text(
                        "Splitsen over meerdere categorieën →",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSplitClick).padding(top = 8.dp, bottom = 4.dp),
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
    density: TransactionDensity = TransactionDensity.COMFORTABLE,
    showCentsEnabled: Boolean = true,
    bulkModeEnabled: Boolean = false,
    selected: Boolean = false,
) {
    // A split transaction has its own categoryId nulled (see TransactionDao.setSplits), so without
    // isSplit it would look identical to a genuinely uncategorized one here.
    val uncategorized = categoryName == null && !isSplit
    val verticalPadding = if (density == TransactionDensity.COMPACT) 6.dp else 12.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bulkModeEnabled) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
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
            transaction.amount.toSignedMagnitudeString(showCentsEnabled),
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

private fun formatEuroInputCents(cents: Long): String = "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

private fun parseEuroInputToCents(text: String): Long? =
    if (text.isBlank()) null else runCatching { Money.parseCommaDecimal(if (text.contains(",")) text else "$text,00").cents }.getOrNull()

private fun parseDateInput(text: String): LocalDate? =
    if (text.isBlank()) null else runCatching { LocalDate.parse(text) }.getOrNull()
