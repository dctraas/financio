package com.financio.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorizationConflictDialog
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.categoryColorFor
import com.financio.app.ui.common.toSignedMagnitudeString
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.Transaction
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * What a tap on a transaction row now opens (R3) — a full-screen detail instead of jumping
 * straight to the category picker. That picker still exists, just moved behind a long-press on
 * the row (see [TransactionsScreen]).
 */
@Composable
fun TransactionDetailScreen(
    onBackClick: () -> Unit,
    onManageRulesClick: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val transaction = state.transaction
    // "Ook toepassen op de rest?" — the same follow-up Transacties' long-press flow shows, now
    // triggered from this screen's own quick category dropdown too (see CategoryDetailRow).
    var bulkApplyPrompt by remember { mutableStateOf<DetailBulkApplyPrompt?>(null) }
    var splitting by remember { mutableStateOf(false) }
    var labelEditorOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    Scaffold { padding ->
        if (!state.loaded || transaction == null) return@Scaffold
        val isSplit = state.splits.isNotEmpty()

        Column(Modifier.fillMaxSize().padding(padding)) {
            DetailHeader(onBackClick = onBackClick, onDeleteClick = { deleteConfirmOpen = true })
            LazyColumn(modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 20.dp)) {
                item { AmountHero(transaction, state.categoryName, isSplit, state.accountName) }
                item {
                    DetailListGroup(
                        rows = buildList {
                            // A split transaction's category lives in its parts, not one single
                            // value - edited via the "Splitsen" row below, not this one.
                            if (!isSplit) {
                                add { CategoryDetailRow(state.categoryName, state.categories, onSelect = viewModel::setCategory) }
                            }
                            add { SplitDetailRow(isSplit, state.splits.size, onClick = { splitting = true }) }
                            add { LabelDetailRow(transaction.note, onClick = { labelEditorOpen = true }) }
                        },
                    )
                }
                item { ActiveRuleCard(state.matchingRule, state.matchingRuleTransactionCount, onManageRulesClick) }
                item { FromBankCard(transaction.description) }
                state.nextTransactionId?.let { nextId ->
                    item { NextTransactionButton(onClick = { onOpenTransaction(nextId) }) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
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

    if (splitting && transaction != null) {
        SplitDialog(
            transaction = transaction,
            categories = state.categories,
            currentSplits = state.rawSplits,
            onDismiss = { splitting = false },
            onSave = { splits ->
                viewModel.saveSplits(splits, fallbackCategoryId = transaction.categoryId)
                splitting = false
            },
            onClear = {
                viewModel.saveSplits(emptyList(), fallbackCategoryId = null)
                splitting = false
            },
        )
    }

    if (labelEditorOpen && transaction != null) {
        LabelEditorDialog(
            initialValue = transaction.note ?: "",
            onDismiss = { labelEditorOpen = false },
            onSave = { note ->
                viewModel.setNote(note)
                labelEditorOpen = false
            },
        )
    }

    if (deleteConfirmOpen && transaction != null) {
        DeleteConfirmDialog(
            counterpartyName = transaction.counterpartyName,
            onConfirm = {
                viewModel.deleteTransaction()
                deleteConfirmOpen = false
                onBackClick()
            },
            onDismiss = { deleteConfirmOpen = false },
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

/** "← / Verwijderen" — the redesign's plain header convention (see ImportTopBar/CategorizeHeader), with the destructive action taking the header's other slot instead of a menu. */
@Composable
private fun DetailHeader(onBackClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text(
            "Verwijderen",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDeleteClick),
        )
    }
}

private fun LocalDate.fullDisplayString(): String {
    val weekday = dayOfWeek.getDisplayName(TextStyle.FULL, Locale("nl")).replaceFirstChar { it.uppercase() }
    val monthName = month.getDisplayName(TextStyle.FULL, Locale("nl"))
    return "$weekday $dayOfMonth $monthName $year"
}

@Composable
private fun AmountHero(transaction: Transaction, categoryName: String?, isSplit: Boolean, accountName: String?) {
    val isIncome = transaction.amount.cents > 0
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategorySquare(categoryName, isSplit = isSplit, size = 38.dp)
            Text(transaction.counterpartyName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            transaction.amount.toSignedMagnitudeString(),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            "${transaction.date.fullDisplayString()} · ${accountName ?: "onbekende rekening"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DetailListGroup(rows: List<@Composable () -> Unit>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 18.dp))
            row()
        }
    }
}

@Composable
private fun DetailListRow(label: String, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

/** A plain "›" glyph rather than a chevron icon - same reasoning as SplitDialog's "✕": no build here to verify a chevron actually ships in the trimmed icon set. */
@Composable
private fun RowScope.Chevron() {
    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CategoryDetailRow(categoryName: String?, categories: List<Category>, onSelect: (Long) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        DetailListRow(label = "Categorie", onClick = { menuOpen = true }) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColorFor(categoryName)))
            Text(categoryName ?: "Nog niet gecategoriseerd", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Chevron()
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onSelect(category.id); menuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun SplitDetailRow(isSplit: Boolean, splitCount: Int, onClick: () -> Unit) {
    DetailListRow(label = "Splitsen", onClick = onClick) {
        Text(
            if (isSplit) "Gesplitst in $splitCount" else "Niet gesplitst",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Chevron()
    }
}

/** Reuses the transaction's own free-text note field ([Transaction.note]) - the redesign's "Label" row and the app's existing "Notitie" concept are the same one editable field, just relabeled and moved into this list group instead of a big box at the bottom of the screen. */
@Composable
private fun LabelDetailRow(note: String?, onClick: () -> Unit) {
    DetailListRow(label = "Label", onClick = onClick) {
        if (note.isNullOrBlank()) {
            Text("Toevoegen", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(note, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Chevron()
        }
    }
}

@Composable
private fun LabelEditorDialog(initialValue: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Eigen label bij deze transactie") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.ifBlank { null }) }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun DeleteConfirmDialog(counterpartyName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transactie verwijderen?") },
        text = { Text("De transactie bij $counterpartyName wordt definitief verwijderd. Dit kan niet ongedaan worden gemaakt.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Verwijderen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/**
 * Shown even when there's no rule (a category picked once by hand, never repeated) — the absence
 * is itself informative, and the edit link is the same "Categorieën & regels" screen either way
 * since there's no dedicated single-rule editor yet.
 */
@Composable
private fun ActiveRuleCard(rule: CategoryRule?, transactionCount: Int, onManageRulesClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(18.dp),
    ) {
        Text(
            "ACTIEVE REGEL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            if (rule != null) ruleDescription(rule) else "Geen regel — deze categorie is handmatig gekozen.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (rule != null) {
            Text(
                if (transactionCount == 1) "Geldt voor 1 transactie" else "Geldt voor $transactionCount transacties",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            "Regel aanpassen",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = onManageRulesClick),
        )
    }
}

private fun ruleDescription(rule: CategoryRule): String = when (rule.matchType) {
    MatchType.COUNTERPARTY_EXACT -> "Tegenrekening ${rule.pattern}"
    MatchType.KEYWORD -> "Bevat \"${rule.pattern}\""
}

@Composable
private fun FromBankCard(description: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            "VAN DE BANK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            description,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(16.dp),
        )
    }
}

/** Secondary, thumb-zone button — walks the ledger via [TransactionDetailUiState.nextTransactionId], so serial cleanup doesn't mean backing out to the list after every single transaction. */
@Composable
private fun NextTransactionButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Volgende transactie", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
