package com.financio.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorizationConflictDialog
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.toShortDisplayString
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.Money
import com.financio.core.model.Transaction

/**
 * What a tap on a transaction row now opens (R3) — a full-screen detail instead of jumping
 * straight to the category picker. That picker still exists, just moved behind a long-press on
 * the row (see [TransactionsScreen]).
 */
@Composable
fun TransactionDetailScreen(
    onBackClick: () -> Unit,
    onManageRulesClick: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val transaction = state.transaction
    // "Ook toepassen op de rest?" — the same follow-up Transacties' long-press flow shows, now
    // triggered from this screen's own quick category dropdown too (see AmountHeader).
    var bulkApplyPrompt by remember { mutableStateOf<DetailBulkApplyPrompt?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(transaction?.counterpartyName ?: "Transactie") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded || transaction == null) return@Scaffold

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            item {
                AmountHeader(
                    transaction = transaction,
                    categoryName = state.categoryName,
                    isSplit = state.splits.isNotEmpty(),
                    categories = state.categories,
                    onCategorySelect = { categoryId -> viewModel.setCategory(categoryId) },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            if (state.splits.isNotEmpty()) {
                item { SplitBreakdown(state.splits) }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            }
            item { DetailRow("Rekening", state.accountName ?: "—") }
            item { DetailRow("Datum", transaction.date.toShortDisplayString()) }
            transaction.tag?.let { tag -> item { DetailRow("Tag", tag) } }
            item { DetailRow("Omschrijving", transaction.description) }
            transaction.counterpartyIban?.let { iban -> item { DetailRow("Tegenrekening", iban) } }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            state.counterpartyStats?.let { stats ->
                item {
                    Text(
                        "Bij ${transaction.counterpartyName}: ${stats.count} transacties, gemiddeld ${stats.average.toDisplayString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            item { MatchingRuleCard(state.matchingRule, onManageRulesClick) }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            item { NoteEditor(transaction, onSave = viewModel::setNote) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Only fires once setCategory() actually persisted - never after a keyword-scoped conflict
    // resolution, where bulk-applying to every same-counterparty transaction would defeat the
    // whole point of scoping the new rule down in the first place (see AppliedCategorization).
    LaunchedEffect(state.appliedCategorization) {
        val applied = state.appliedCategorization ?: return@LaunchedEffect
        if (state.otherTransactionsWithSameCounterparty > 0) {
            bulkApplyPrompt = DetailBulkApplyPrompt(applied.transaction.counterpartyName, applied.categoryId, state.otherTransactionsWithSameCounterparty)
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
        DetailBulkApplyDialog(
            prompt = prompt,
            onConfirm = {
                viewModel.applyCategoryToCounterparty(prompt.categoryId)
                bulkApplyPrompt = null
            },
            onDismiss = { bulkApplyPrompt = null },
        )
    }
}

// "Detail"-prefixed to avoid colliding with TransactionsScreen.kt's own (differently-shaped)
// private BulkApplyPrompt/BulkApplyDialog: a top-level `private` class is only source-scoped to
// its file, not a separate JVM namespace, so two same-named top-level classes in the same package
// are a hard "Redeclaration" compile error, not two coexisting privates.
private data class DetailBulkApplyPrompt(val counterpartyName: String, val categoryId: Long, val otherCount: Int)

@Composable
private fun DetailBulkApplyDialog(prompt: DetailBulkApplyPrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
private fun AmountHeader(
    transaction: Transaction,
    categoryName: String?,
    isSplit: Boolean,
    categories: List<Category>,
    onCategorySelect: (Long) -> Unit,
) {
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val isIncome = transaction.amount.cents > 0

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategorySquare(categoryName, isSplit = isSplit, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                transaction.amount.toSignedDisplayString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (isSplit) {
                // A split transaction's category lives in its parts, not one single value — edited
                // by long-pressing the row back in the list (see TransactionsScreen), not here.
                Text(
                    "Gesplitst over meerdere categorieën",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Box {
                    Text(
                        (categoryName ?: "Nog niet gecategoriseerd") + " ▾",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { categoryMenuOpen = true },
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { onCategorySelect(category.id); categoryMenuOpen = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitBreakdown(splits: List<Pair<Category?, Money>>) {
    Column {
        splits.forEach { (category, amount) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(category?.name ?: "Onbekende categorie")
                Text(amount.toDisplayString(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * Shown even when there's no rule (a category picked once by hand, never repeated) — the absence
 * is itself informative, and the edit link is the same "Categorieën & regels" screen either way
 * since there's no dedicated single-rule editor yet.
 */
@Composable
private fun MatchingRuleCard(rule: CategoryRule?, onManageRulesClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text("Categoriseerregel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (rule != null) ruleDescription(rule) else "Geen regel — deze categorie is handmatig gekozen.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Text(
            "Regels bewerken →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onManageRulesClick),
        )
    }
}

private fun ruleDescription(rule: CategoryRule): String = when (rule.matchType) {
    MatchType.COUNTERPARTY_EXACT -> "Tegenrekening ${rule.pattern}"
    MatchType.KEYWORD -> "Bevat \"${rule.pattern}\""
}

/**
 * New per-transaction field from the redesign (R3). Saved explicitly via a button rather than on
 * every keystroke — the same "commit on an explicit action, not per character" choice as the
 * budget limit fields elsewhere, to avoid writing to the encrypted database on every keypress.
 */
@Composable
private fun NoteEditor(transaction: Transaction, onSave: (String?) -> Unit) {
    var text by remember(transaction.id) { mutableStateOf(transaction.note ?: "") }
    var dirty by remember(transaction.id) { mutableStateOf(false) }

    // A note loaded later than the first composition (e.g. right after opening this screen, before
    // the Flow has emitted) must still land in the field once it arrives, but only if the user
    // hasn't already started typing over it.
    LaunchedEffect(transaction.note) {
        if (!dirty) text = transaction.note ?: ""
    }

    Column {
        Text("Notitie", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; dirty = true },
            placeholder = { Text("Eigen aantekening bij deze transactie") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        if (dirty) {
            TextButton(
                onClick = { onSave(text); dirty = false },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Opslaan") }
        }
    }
}
