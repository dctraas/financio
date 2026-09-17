package com.financio.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.usecase.SplitValidation

internal data class SplitRowState(val categoryId: Long?, val amountText: String)

/**
 * Lets one transaction be divided across multiple categories — a single Albert Heijn receipt
 * that was half boodschappen, half drogisterij, say. [SplitValidation] (`:core`) is the single
 * source of truth for "is this a valid split"; this dialog just keeps the "Opslaan" button
 * disabled until it agrees. Amounts are entered as positive euro values regardless of whether the
 * transaction is a debit or credit — the sign is restored from [transaction.amount] when saving.
 * Shared between TransactionsScreen (long-press flow) and TransactionDetailScreen's own
 * "Splitsen" row, so both keep the exact same editor instead of two slightly-diverging copies.
 */
@Composable
internal fun SplitDialog(
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
