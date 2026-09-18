package com.financio.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.categoryColorFor
import com.financio.app.ui.theme.BudgetStatusColors
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.budget.BudgetEvaluator
import com.financio.core.budget.BudgetStatus
import com.financio.core.model.Category
import com.financio.core.model.Money
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BudgetsScreen(onBackClick: () -> Unit, onCategoryClick: (Long) -> Unit = {}, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editingCategory by remember { mutableStateOf<CategoryLimitEdit?>(null) }
    val previousMonthShort = state.yearMonth.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", "")

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BudgetHeader(
                monthLabel = state.yearMonth.month.getDisplayName(TextStyle.FULL, Locale("nl")),
                onBackClick = onBackClick,
            )
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                if (state.rows.isEmpty()) {
                    item {
                        Column(Modifier.padding(vertical = 32.dp)) {
                            Text("Nog geen budgetten ingesteld", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Tik op ‘Instellen’ bij een categorie hieronder om te beginnen.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                } else {
                    item { MonthSummary(state) }

                    val attentionRows = state.rows.filter { it.status != BudgetStatus.OK }
                    val onTrackRows = state.rows.filter { it.status == BudgetStatus.OK }

                    if (attentionRows.isNotEmpty()) {
                        item { SectionLabel("VRAAGT AANDACHT") }
                        items(attentionRows, key = { it.budget.id }) { row ->
                            BudgetRowCard(
                                row = row,
                                pace = state.pace,
                                previousMonthShort = previousMonthShort,
                                onClick = { row.category?.let { onCategoryClick(it.id) } },
                                onLongClick = { row.category?.let { editingCategory = CategoryLimitEdit(it, row.budget.limit, row.budget.rollover) } },
                            )
                        }
                    }
                    if (onTrackRows.isNotEmpty()) {
                        item { SectionLabel("OP KOERS", topPadding = if (attentionRows.isNotEmpty()) 24.dp else 12.dp) }
                        items(onTrackRows, key = { it.budget.id }) { row ->
                            BudgetRowCard(
                                row = row,
                                pace = state.pace,
                                previousMonthShort = previousMonthShort,
                                onClick = { row.category?.let { onCategoryClick(it.id) } },
                                onLongClick = { row.category?.let { editingCategory = CategoryLimitEdit(it, row.budget.limit, row.budget.rollover) } },
                            )
                        }
                    }
                }
                if (state.unlimitedSpend.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) {
                            state.unlimitedSpend.forEach { entry ->
                                UnlimitedCard(
                                    entry = entry,
                                    average = state.suggestedLimitByCategory[entry.category.id],
                                    onClick = { editingCategory = CategoryLimitEdit(entry.category, null, false) },
                                )
                            }
                        }
                    }
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)) }
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

/** "← + Budget · september" — the redesign's plain sub-header convention (see CategorizeGameHeader/ImportTopBar). */
@Composable
private fun BudgetHeader(monthLabel: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text("Budget · $monthLabel", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: Dp = 12.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = LocalFinancioColors.current.inkFaint,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp),
    )
}

private fun Money.magnitudeString(): String = toDisplayString().removePrefix("-").removePrefix("€")

/**
 * One top number + one plain status bar summarizing the whole month, plus a one-line "how does
 * your pace compare" readout - replaces the old per-category-colored stacked bar (that shape lived
 * entirely in the categories below anyway, so this card is now just "where do I stand overall").
 */
@Composable
private fun MonthSummary(state: BudgetsUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Van je limieten gebruikt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.pace != null) {
                Text(
                    "dag ${LocalDate.now().dayOfMonth} van ${state.yearMonth.lengthOfMonth()}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(state.totalSpent.toDisplayString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                " / ${state.totalLimit.magnitudeString()}",
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        ProgressTrack(
            percentage = BudgetEvaluator.percentage(state.totalSpent, state.totalLimit),
            color = MaterialTheme.colorScheme.primary,
            pace = state.pace,
            topPadding = 14.dp,
            trackHeight = 12.dp,
            containerHeight = 18.dp,
        )
        paceExplanation(state.totalSpent, state.totalLimit, state.pace)?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** "Je zit 19% vóór op je tempo" - your spend-fraction vs. how far through the month you are, purely descriptive (ahead isn't automatically bad: it's just spending faster than time is passing). */
private fun paceExplanation(totalSpent: Money, totalLimit: Money, pace: Float?): String? {
    if (pace == null || totalLimit.cents <= 0) return null
    val spentFraction = totalSpent.cents.toFloat() / totalLimit.cents.toFloat()
    val deltaPercent = ((spentFraction - pace) * 100).roundToInt()
    val suffix = "op je tempo — het streepje is waar je nu zou moeten staan."
    return when {
        deltaPercent > 0 -> "Je zit $deltaPercent% vóór $suffix"
        deltaPercent < 0 -> "Je zit ${-deltaPercent}% achter $suffix"
        else -> "Je zit precies $suffix"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BudgetRowCard(row: BudgetRow, pace: Float?, previousMonthShort: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val statusColors = LocalBudgetStatusColors.current
    val color = colorFor(row.status, statusColors)
    val rolloverBonus = row.effectiveLimit - row.budget.limit

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColorFor(row.category?.name)))
                Text(row.category?.name ?: "Onbekende categorie", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                StatusBadge(row.status, rolloverBonus, previousMonthShort)
            }
            Text(
                "${row.spent.magnitudeString()} / ${row.effectiveLimit.magnitudeString()}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
        ProgressTrack(percentage = row.percentage, color = color, pace = pace)
    }
}

/** "over" / "bijna op" (status), or "+18 over van aug" when rollover actually added headroom and the category is otherwise on track - never both at once, since OVER/WARNING already say enough. */
@Composable
private fun StatusBadge(status: BudgetStatus, rolloverBonus: Money, previousMonthShort: String) {
    val (label, container, content) = when {
        status == BudgetStatus.OVER -> Triple("over", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        status == BudgetStatus.WARNING -> Triple("bijna op", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        rolloverBonus.cents > 0 -> Triple(
            "+${rolloverBonus.magnitudeString()} over van $previousMonthShort",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        else -> return
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A dashed-outline card per category with spend but no limit yet - "Instellen" pre-fills [BudgetLimitDialog] with the trailing 3-month average, same as the dialog's own inline suggestion. */
@Composable
private fun UnlimitedCard(entry: UnlimitedCategorySpend, average: Money?, onClick: () -> Unit) {
    val dashColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("${entry.category.name} heeft nog geen limiet", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "gemiddeld ${(average ?: entry.spent).magnitudeString()} per maand",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Instellen", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** A vertical tick mark at the day-of-month fraction — "you're this far through the month" against "you're this far through the budget" on the bar right underneath it. */
@Composable
private fun ProgressTrack(
    percentage: Int,
    color: Color,
    pace: Float?,
    topPadding: Dp = 8.dp,
    trackHeight: Dp = 6.dp,
    containerHeight: Dp = 10.dp,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = topPadding).height(containerHeight)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        val fraction = (percentage.coerceIn(0, 100) / 100f)
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(trackHeight)
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
                    .height(containerHeight)
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
 * unlimited card's "Instellen") and "edit an existing limit" (long-press a budget row) — the
 * redesign's "set a limit straight from the category card" now lives entirely on this screen
 * instead of in Instellingen.
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
