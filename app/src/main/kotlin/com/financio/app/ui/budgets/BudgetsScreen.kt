package com.financio.app.ui.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.BudgetStatusColors
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.budget.BudgetStatus
import com.financio.core.model.Category
import com.financio.core.model.Money

@Composable
fun BudgetsScreen(onCategoryClick: (Long) -> Unit = {}, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editingCategory by remember { mutableStateOf<CategoryLimitEdit?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Budget") }) }) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                PeriodNavigator(
                    label = state.referenceLabel,
                    canGoToNextPeriod = state.canGoToNextPeriod,
                    onPrevious = viewModel::goToPreviousPeriod,
                    onNext = viewModel::goToNextPeriod,
                )
            }
            if (state.rows.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 32.dp)) {
                        Text("Nog geen budgetten ingesteld", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Tik op ‘+ limiet instellen’ bij een categorie hieronder om te beginnen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                item { MonthSummary(state) }
                items(state.rows, key = { it.budget.id }) { row ->
                    BudgetCard(
                        row = row,
                        pace = state.pace,
                        onClick = { row.category?.let { onCategoryClick(it.id) } },
                        onLongClick = { row.category?.let { editingCategory = CategoryLimitEdit(it, row.budget.limit, row.budget.rollover) } },
                    )
                }
            }
            if (state.unlimitedSpend.isNotEmpty()) {
                item {
                    Text(
                        "Zonder limiet, wel besteed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.unlimitedSpend, key = { it.category.id }) { entry ->
                            UnlimitedChip(entry, onClick = { editingCategory = CategoryLimitEdit(entry.category, null, false) })
                        }
                    }
                }
            }
        }
    }

    editingCategory?.let { edit ->
        BudgetLimitDialog(
            categoryName = edit.category.name,
            currentLimit = edit.currentLimit,
            currentRollover = edit.currentRollover,
            suggestedLimit = state.suggestedLimitByCategory[edit.category.id],
            onDismiss = { editingCategory = null },
            onSave = { limit, rollover ->
                viewModel.setLimit(edit.category.id, limit, rollover)
                editingCategory = null
            },
        )
    }
}

private data class CategoryLimitEdit(val category: Category, val currentLimit: Money?, val currentRollover: Boolean)

/**
 * Same "shift the whole trailing window" ‹ › affordance as Inzicht's own period navigator — see
 * that screen's `PeriodNavigator` for the reasoning (plain text over an icon whose availability
 * in the trimmed icon set isn't guaranteed).
 */
@Composable
private fun PeriodNavigator(label: String, canGoToNextPeriod: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Text(
            "‹",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPrevious).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (canGoToNextPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .let { if (canGoToNextPeriod) it.clickable(onClick = onNext) else it }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/**
 * "One top number + one stacked status bar summarizing the whole month instead of scrolling 6
 * cards" — each category is one segment of the bar, sized by its own share of this month's total
 * spend and tinted its own status color, so the shape of the whole month is visible at a glance.
 */
@Composable
private fun MonthSummary(state: BudgetsUiState) {
    val statusColors = LocalBudgetStatusColors.current
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(
            "${state.totalSpent.toDisplayString()} van ${state.totalLimit.toDisplayString()} besteed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        val spendingRows = state.rows.filter { it.spent.cents > 0 }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp).height(14.dp)) {
            if (spendingRows.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(99.dp)),
                ) {
                    spendingRows.forEach { row ->
                        Column(
                            modifier = Modifier
                                .weight(row.spent.cents.toFloat())
                                .fillMaxHeight()
                                .background(colorFor(row.status, statusColors)),
                        ) {}
                    }
                }
            }
            state.pace?.let { pace -> PaceTick(pace, height = 14.dp) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BudgetCard(row: BudgetRow, pace: Float?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val statusColors = LocalBudgetStatusColors.current
    val statusColor = colorFor(row.status, statusColors)
    val cardTint = if (row.status == BudgetStatus.OVER) statusColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardTint)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                row.category?.name ?: "Onbekende categorie",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${row.spent.toDisplayString()} / ${row.effectiveLimit.toDisplayString()}",
                color = if (row.status == BudgetStatus.OVER) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Only shown when rollover actually added something - a rollover budget that started the
        // month fully spent has no bonus to explain.
        val rolloverBonus = row.effectiveLimit - row.budget.limit
        if (row.budget.rollover && rolloverBonus.cents > 0) {
            Text(
                "waarvan ${rolloverBonus.toDisplayString()} meegenomen van vorige maand",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ProgressTrack(percentage = row.percentage, color = statusColor, pace = pace)
        if (row.status == BudgetStatus.OVER) {
            val over = row.spent - row.budget.limit
            Text(
                "${over.toDisplayString()} boven budget",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun UnlimitedChip(entry: UnlimitedCategorySpend, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(entry.category.name, style = MaterialTheme.typography.bodyMedium)
        Text(entry.spent.toDisplayString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("+", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

/** A vertical tick mark at the day-of-month fraction — "you're this far through the month" against "you're this far through the budget" on the bar right underneath it. */
@Composable
private fun PaceTick(pace: Float, height: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = maxWidth * pace.coerceIn(0f, 1f))
                .width(2.dp)
                .height(height)
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun ProgressTrack(percentage: Int, color: Color, pace: Float?) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        val fraction = (percentage.coerceIn(0, 100) / 100f)
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color),
            )
        }
        pace?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * it.coerceIn(0f, 1f))
                    .width(2.dp)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

private fun colorFor(status: BudgetStatus, colors: BudgetStatusColors): Color = when (status) {
    BudgetStatus.OK -> colors.ok
    BudgetStatus.WARNING -> colors.warning
    BudgetStatus.OVER -> colors.over
}

/**
 * Reused for both "set a limit on a category that has none yet" ([currentLimit] null, from an
 * unlimited chip's "+") and "edit an existing limit" (long-press a budget row) — the redesign's
 * "set a limit straight from the category chip" now lives entirely on this screen instead of in
 * Instellingen.
 */
@Composable
private fun BudgetLimitDialog(
    categoryName: String,
    currentLimit: Money?,
    currentRollover: Boolean,
    suggestedLimit: Money?,
    onDismiss: () -> Unit,
    onSave: (Money, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(currentLimit?.toEuroInputString() ?: "") }
    var rollover by remember { mutableStateOf(currentRollover) }
    val parsed = parseEuroInput(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limiet voor $categoryName") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    prefix = { Text("€") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Offered, never applied on its own - a plain average can be a bad limit (a
                // one-off big purchase skews it), so it only fills the field in when tapped, and
                // only shows while the field is still empty (already typing your own number, or
                // editing an existing limit, means there's nothing left to suggest).
                if (suggestedLimit != null && text.isBlank()) {
                    Text(
                        "Voorstel: ${suggestedLimit.toDisplayString()} (gemiddeld laatste 3 maanden) — gebruiken →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { text = suggestedLimit.toEuroInputString() },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Restant meenemen naar volgende maand",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = rollover, onCheckedChange = { rollover = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let { onSave(it, rollover) } }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

private fun Money.toEuroInputString(): String {
    val absCents = kotlin.math.abs(cents)
    return "${absCents / 100},${(absCents % 100).toString().padStart(2, '0')}"
}

private fun parseEuroInput(text: String): Money? =
    if (text.isBlank()) null else runCatching { Money.parseCommaDecimal(normalizeEuroInput(text)) }.getOrNull()

/** Accepts "450" as well as "450,00" — a bare integer has no comma for [Money.parseCommaDecimal] to split on. */
private fun normalizeEuroInput(text: String): String = if (text.contains(",")) text else "$text,00"
