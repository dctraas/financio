package com.financio.app.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Which of the three ways [GoalDialog] can be opened - "Nieuw spaardoel", "Nieuw doel hiermee" (roll-forward from an achieved goal), or "Spaardoel bewerken" (tapping an existing goal). */
private sealed interface GoalDialogMode {
    data object Add : GoalDialogMode
    data class RollForward(val previous: SavingsGoal) : GoalDialogMode
    data class Edit(val goal: SavingsGoal) : GoalDialogMode
}

@Composable
fun SavingsGoalsScreen(viewModel: SavingsGoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var dialogMode by remember { mutableStateOf<GoalDialogMode?>(null) }
    var deleting by remember { mutableStateOf<SavingsGoal?>(null) }
    var toppingUp by remember { mutableStateOf<SavingsGoal?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val isEmpty = state.activeRows.isEmpty() && state.achievedRows.isEmpty() && state.archivedRows.isEmpty()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DoelenHeader(showAddButton = state.categories.isNotEmpty(), onAddClick = { dialogMode = GoalDialogMode.Add })
            if (isEmpty) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp)) {
                    Text("Nog geen spaardoelen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Koppel een doelbedrag aan een categorie (bijv. Sparen & beleggen) om de " +
                            "voortgang hier te volgen — gebaseerd op wat je er al naartoe hebt " +
                            "overgeboekt volgens je eigen transactiehistorie.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    items(state.activeRows, key = { it.goal.id }) { row ->
                        SavingsGoalCard(
                            row = row,
                            averageMonthlyLeftover = state.averageMonthlyLeftover,
                            onClick = { dialogMode = GoalDialogMode.Edit(row.goal) },
                            onEditTargetDateClick = { dialogMode = GoalDialogMode.Edit(row.goal) },
                        )
                    }
                    if (state.achievedRows.isNotEmpty()) {
                        items(state.achievedRows, key = { "achieved-${it.goal.id}" }) { row ->
                            AchievedGoalCard(
                                row = row,
                                onClick = { dialogMode = GoalDialogMode.Edit(row.goal) },
                                onArchiveClick = { viewModel.archiveGoal(row.goal.id) },
                                onRollForwardClick = { dialogMode = GoalDialogMode.RollForward(row.goal) },
                            )
                        }
                    }
                    state.averageMonthlyLeftover?.let { leftover ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Gemiddelde ruimte per maand", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(leftover.toDisplayString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (state.archivedRows.isNotEmpty()) {
                        item {
                            Text(
                                if (showArchived) "Gearchiveerde doelen verbergen ▴" else "${state.archivedRows.size} gearchiveerde doel(en) tonen ▾",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp).clickable { showArchived = !showArchived },
                            )
                        }
                        if (showArchived) {
                            items(state.archivedRows, key = { "archived-${it.goal.id}" }) { row ->
                                ArchivedGoalRow(
                                    row,
                                    onClick = { dialogMode = GoalDialogMode.Edit(row.goal) },
                                    onDeleteClick = { deleting = row.goal },
                                )
                            }
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    dialogMode?.let { mode ->
        GoalDialog(
            categories = state.categories,
            accounts = state.accounts,
            mode = mode,
            onDismiss = { dialogMode = null },
            onAddCategory = viewModel::addCategory,
            onSave = { name, target, categoryId, linkedAccountId, targetDate ->
                when (mode) {
                    is GoalDialogMode.Edit -> viewModel.editGoal(mode.goal.id, name, target, categoryId, linkedAccountId, targetDate)
                    else -> viewModel.addGoal(name, target, categoryId, linkedAccountId, targetDate)
                }
                dialogMode = null
            },
            onTopUpClick = (mode as? GoalDialogMode.Edit)?.let { edit -> { toppingUp = edit.goal; dialogMode = null } },
            onDeleteClick = (mode as? GoalDialogMode.Edit)?.let { edit -> { deleting = edit.goal; dialogMode = null } },
        )
    }

    toppingUp?.let { goal ->
        ManualTopUpDialog(
            goalName = goal.name,
            onDismiss = { toppingUp = null },
            onSave = { delta -> viewModel.addManualAdjustment(goal.id, delta); toppingUp = null },
        )
    }

    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Spaardoel verwijderen?") },
            text = { Text("'${goal.name}' wordt verwijderd. Je transacties en categorie blijven ongewijzigd.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(goal.id); deleting = null }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuleren") } },
        )
    }
}

/** "Doelen" + "Nieuw doel" - the redesign's plain header convention, replacing the old FloatingActionButton. */
@Composable
private fun DoelenHeader(showAddButton: Boolean, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Doelen", style = MaterialTheme.typography.titleLarge)
        if (showAddButton) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable(onClick = onAddClick)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("Nieuw doel", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun Money.magnitudeString(): String = toDisplayString().removePrefix("-").removePrefix("€")

@Composable
private fun SavingsGoalCard(
    row: SavingsGoalRow,
    averageMonthlyLeftover: Money?,
    onClick: () -> Unit,
    onEditTargetDateClick: () -> Unit,
) {
    val statusColors = LocalBudgetStatusColors.current
    val pace = row.paceFraction
    val onSchedule = pace == null || (row.percentage / 100f) >= pace
    val barColor = if (onSchedule) statusColors.ok else statusColors.warning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
                Text(row.goal.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitleFor(row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            GoalStatusPill(onSchedule)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp)) {
            Text(row.progress.toDisplayString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                " / ${row.goal.targetAmount.magnitudeString()}",
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        GoalProgressBar(percentage = row.percentage, color = barColor, pace = pace, height = 12.dp)
        row.requiredMonthlyContribution?.let { required ->
            val fits = row.fitsWithinLeftover
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${required.toDisplayString()} per maand nodig",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fits != null) {
                    Text(
                        if (fits) "past binnen je ruimte" else "meer dan je ruimte",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fits) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (!onSchedule && row.goal.targetDate != null) {
            Text(
                "Streefdatum verzetten",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onEditTargetDateClick),
            )
        }
    }
}

/** "dekt 3,4 maanden vaste lasten" for a buffer goal, "streefdatum juni 2027 · 6 stortingen" when there's a target date, or the plain account/category follow-label otherwise. */
private fun subtitleFor(row: SavingsGoalRow): String {
    row.bufferMonthsCovered?.let { months -> return "dekt ${formatOneDecimal(months)} maanden vaste lasten" }
    row.goal.targetDate?.let { date ->
        val depositWord = if (row.depositCountThisYear == 1) "storting" else "stortingen"
        return "streefdatum ${date.monthYearLabel()} · ${row.depositCountThisYear} $depositWord"
    }
    return followLabel(row)
}

@Composable
private fun GoalStatusPill(onSchedule: Boolean) {
    Text(
        if (onSchedule) "op schema" else "loopt achter",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (onSchedule) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (onSchedule) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Same tempostreepje pattern as Budget's own progress bar - a vertical mark at "how far along you should be" behind the fill. */
@Composable
private fun GoalProgressBar(percentage: Int, color: Color, pace: Float?, height: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp).height(height)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        val fraction = percentage.coerceIn(0, 100) / 100f
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(height)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
        pace?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * it.coerceIn(0f, 1f))
                    .width(2.dp)
                    .height(height)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

/** A full accentSoft card with a ✓ - the redesign's "this is the one moment the app celebrates" treatment, same spirit as the categorize-done screen. */
@Composable
private fun AchievedGoalCard(row: SavingsGoalRow, onClick: () -> Unit, onArchiveClick: () -> Unit, onRollForwardClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(row.goal.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Icon(Icons.Filled.Check, contentDescription = "Gehaald", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            "${row.goal.targetAmount.toDisplayString()} gehaald" + (row.earlyByDays?.let { " · ${earlyLateLabel(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Archiveren",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.clickable(onClick = onArchiveClick),
            )
            // Not in the schermontwerp mockup's own screenshot (only "Archiveren" is shown there),
            // but a real, already-built capability - keeping it as a second, equally quiet link
            // rather than dropping the only way to start a fresh goal from an achieved one's category/rekening.
            Text(
                "Nieuw doel hiermee",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.clickable(onClick = onRollForwardClick),
            )
        }
    }
}

@Composable
private fun ArchivedGoalRow(row: SavingsGoalRow, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(row.goal.name, fontWeight = FontWeight.SemiBold)
            Text(
                row.goal.targetAmount.toDisplayString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Verwijderen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDeleteClick),
        )
    }
}

private fun followLabel(row: SavingsGoalRow): String {
    val account = row.linkedAccount
    return if (account != null) {
        "Volgt ${account.name} ${account.ibanMasked} · ${row.depositCountThisYear} ${if (row.depositCountThisYear == 1) "storting" else "stortingen"} dit jaar"
    } else {
        "Volgt categorie: ${row.category?.name ?: "onbekend"}"
    }
}

/** "3 weken vóór je streefdatum" / "2 dagen na je streefdatum" - the achieved card's own phrasing, distinct from the plainer "eerder/later dan gepland" used elsewhere. */
private fun earlyLateLabel(days: Int): String = when {
    days > 6 -> "${days / 7} ${if (days / 7 == 1) "week" else "weken"} vóór je streefdatum"
    days > 0 -> "$days ${if (days == 1) "dag" else "dagen"} vóór je streefdatum"
    days == 0 -> "precies op je streefdatum"
    days > -7 -> "${-days} ${if (days == -1) "dag" else "dagen"} na je streefdatum"
    else -> "${-days / 7} ${if (-days / 7 == 1) "week" else "weken"} na je streefdatum"
}

/** "juni 2027" - the full Dutch month name, for a goal's streefdatum subtitle. */
private fun LocalDate.monthYearLabel(): String = "${month.getDisplayName(TextStyle.FULL, Locale("nl"))} $year"

/** Always a comma decimal separator, regardless of device locale - consistent with [Money.toDisplayString]'s own hardcoded Dutch formatting. */
private fun formatOneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    return "${tenths / 10},${kotlin.math.abs(tenths % 10)}"
}

@Composable
private fun GoalDialog(
    categories: List<Category>,
    accounts: List<Account>,
    mode: GoalDialogMode,
    onDismiss: () -> Unit,
    onAddCategory: (name: String, onCreated: (Long) -> Unit) -> Unit,
    onSave: (name: String, target: Money, categoryId: Long, linkedAccountId: Long?, targetDate: LocalDate?) -> Unit,
    onTopUpClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
) {
    // Roll-forward only carries over the categorie/rekening (a fresh goal wants its own naam,
    // doelbedrag and streefdatum) - editing carries over every field, since it's the same goal.
    var name by remember {
        mutableStateOf(
            when (mode) {
                is GoalDialogMode.RollForward -> "${mode.previous.name} (vervolg)"
                is GoalDialogMode.Edit -> mode.goal.name
                GoalDialogMode.Add -> ""
            },
        )
    }
    var targetText by remember { mutableStateOf(if (mode is GoalDialogMode.Edit) formatEuroInput(mode.goal.targetAmount) else "") }
    var categoryId by remember {
        mutableStateOf(
            when (mode) {
                is GoalDialogMode.RollForward -> mode.previous.categoryId
                is GoalDialogMode.Edit -> mode.goal.categoryId
                GoalDialogMode.Add -> categories.firstOrNull()?.id
            },
        )
    }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var linkedAccountId by remember {
        mutableStateOf(
            when (mode) {
                is GoalDialogMode.RollForward -> mode.previous.linkedAccountId
                is GoalDialogMode.Edit -> mode.goal.linkedAccountId
                GoalDialogMode.Add -> null
            },
        )
    }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var targetDateText by remember { mutableStateOf(if (mode is GoalDialogMode.Edit) mode.goal.targetDate?.toString() ?: "" else "") }

    val target = parseEuroInput(targetText)
    val targetDate = if (targetDateText.isBlank()) null else runCatching { LocalDate.parse(targetDateText) }.getOrNull()
    val targetDateValid = targetDateText.isBlank() || targetDate != null
    val isValid = name.isNotBlank() && target != null && target.cents > 0 && categoryId != null && targetDateValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    GoalDialogMode.Add -> "Nieuw spaardoel"
                    is GoalDialogMode.RollForward -> "Nieuw doel hiermee"
                    is GoalDialogMode.Edit -> "Spaardoel bewerken"
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    placeholder = { Text("bijv. Vakantie 2027") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Doelbedrag") },
                    prefix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        categories.firstOrNull { it.id == categoryId }?.name ?: "Kies categorie",
                        color = if (categoryId == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { categoryMenuOpen = true }.padding(vertical = 12.dp),
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { categoryId = category.id; categoryMenuOpen = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("+ Nieuwe categorie", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                            onClick = { categoryMenuOpen = false; addingCategory = true },
                        )
                    }
                }
                // Inline rather than a second dialog on top of this one - so "sparen voor mijn
                // bruiloft" never means closing Nieuw Spaardoel, going to Categorieën, and starting
                // this whole dialog over just to pick a category that didn't exist yet.
                if (addingCategory) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("Naam nieuwe categorie") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = newCategoryName.isNotBlank(),
                            onClick = {
                                onAddCategory(newCategoryName) { newId -> categoryId = newId }
                                newCategoryName = ""
                                addingCategory = false
                            },
                        ) { Text("Toevoegen") }
                    }
                }
                if (accounts.isNotEmpty()) {
                    Text("Rekening (optioneel)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Box {
                        Text(
                            accounts.firstOrNull { it.id == linkedAccountId }?.name ?: "Geen",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { accountMenuOpen = true }.padding(vertical = 8.dp),
                        )
                        DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Geen") }, onClick = { linkedAccountId = null; accountMenuOpen = false })
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = { linkedAccountId = account.id; accountMenuOpen = false },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = targetDateText,
                    onValueChange = { targetDateText = it },
                    label = { Text("Streefdatum (optioneel)") },
                    placeholder = { Text("JJJJ-MM-DD") },
                    singleLine = true,
                    isError = !targetDateValid,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Voortgang wordt berekend uit wat je al naar deze categorie hebt overgeboekt " +
                        "(al je transacties tot nu toe, niet alleen vanaf vandaag).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Only on an existing goal - a goal that doesn't exist yet has nothing to top up or delete.
                if (onTopUpClick != null) {
                    Text(
                        "Handmatige aanvulling toevoegen →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onTopUpClick),
                    )
                }
                if (onDeleteClick != null) {
                    Text(
                        "Doel verwijderen",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalBudgetStatusColors.current.over,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onDeleteClick),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onSave(name.trim(), target!!, categoryId!!, linkedAccountId, targetDate) },
            ) { Text(if (mode is GoalDialogMode.Edit) "Opslaan" else "Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** The reverse of [parseEuroInput] - "23,45", no thousands separators, for prefilling the doelbedrag field when editing an existing goal. */
private fun formatEuroInput(money: Money): String =
    "${money.cents / 100},${(money.cents % 100).toString().padStart(2, '0')}"

@Composable
private fun ManualTopUpDialog(goalName: String, onDismiss: () -> Unit, onSave: (Money) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = parseEuroInput(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handmatige aanvulling") },
        text = {
            Column {
                Text(
                    "Voor een bijdrage aan '$goalName' die niet als eigen transactie voorkomt. " +
                        "Typ een negatief bedrag (bijv. -15,00) om een eerdere aanvulling te corrigeren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    prefix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { onSave(parsed!!) }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

private fun parseEuroInput(text: String): Money? {
    if (text.isBlank()) return null
    val negative = text.trim().startsWith("-")
    val unsigned = text.trim().removePrefix("-")
    val normalized = if (unsigned.contains(",")) unsigned else "$unsigned,00"
    val parsed = runCatching { Money.parseCommaDecimal(normalized) }.getOrNull() ?: return null
    return if (negative) -parsed else parsed
}
