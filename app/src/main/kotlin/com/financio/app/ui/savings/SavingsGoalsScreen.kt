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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import java.time.LocalDate

@Composable
fun SavingsGoalsScreen(viewModel: SavingsGoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var addingGoal by remember { mutableStateOf(false) }
    var rollForwardFrom by remember { mutableStateOf<SavingsGoal?>(null) }
    var deleting by remember { mutableStateOf<SavingsGoalRow?>(null) }
    var toppingUp by remember { mutableStateOf<SavingsGoalRow?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val isEmpty = state.activeRows.isEmpty() && state.achievedRows.isEmpty() && state.archivedRows.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spaardoelen") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.categories.isNotEmpty()) {
                FloatingActionButton(onClick = { addingGoal = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Spaardoel toevoegen")
                }
            }
        },
    ) { padding ->
        if (isEmpty) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
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
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.activeRows, key = { it.goal.id }) { row ->
                    SavingsGoalCard(
                        row = row,
                        averageMonthlyLeftover = state.averageMonthlyLeftover,
                        onTopUpClick = { toppingUp = row },
                        onDeleteClick = { deleting = row },
                    )
                }
                if (state.achievedRows.isNotEmpty()) {
                    item { SectionHeader("Gehaald") }
                    items(state.achievedRows, key = { "achieved-${it.goal.id}" }) { row ->
                        AchievedGoalCard(
                            row = row,
                            onArchiveClick = { viewModel.archiveGoal(row.goal.id) },
                            onRollForwardClick = { rollForwardFrom = row.goal },
                        )
                    }
                }
                if (state.archivedRows.isNotEmpty()) {
                    item {
                        Text(
                            if (showArchived) "Gearchiveerde doelen verbergen ▴" else "${state.archivedRows.size} gearchiveerde doel(en) tonen ▾",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 20.dp).clickable { showArchived = !showArchived },
                        )
                    }
                    if (showArchived) {
                        items(state.archivedRows, key = { "archived-${it.goal.id}" }) { row ->
                            ArchivedGoalRow(row, onDeleteClick = { deleting = row })
                        }
                    }
                }
            }
        }
    }

    if (addingGoal) {
        AddGoalDialog(
            categories = state.categories,
            accounts = state.accounts,
            prefill = null,
            onDismiss = { addingGoal = false },
            onSave = { name, target, categoryId, linkedAccountId, targetDate ->
                viewModel.addGoal(name, target, categoryId, linkedAccountId, targetDate)
                addingGoal = false
            },
        )
    }

    rollForwardFrom?.let { previous ->
        AddGoalDialog(
            categories = state.categories,
            accounts = state.accounts,
            prefill = previous,
            onDismiss = { rollForwardFrom = null },
            onSave = { name, target, categoryId, linkedAccountId, targetDate ->
                viewModel.addGoal(name, target, categoryId, linkedAccountId, targetDate)
                rollForwardFrom = null
            },
        )
    }

    toppingUp?.let { row ->
        ManualTopUpDialog(
            goalName = row.goal.name,
            onDismiss = { toppingUp = null },
            onSave = { delta -> viewModel.addManualAdjustment(row.goal.id, delta); toppingUp = null },
        )
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Spaardoel verwijderen?") },
            text = { Text("'${row.goal.name}' wordt verwijderd. Je transacties en categorie blijven ongewijzigd.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGoal(row.goal.id); deleting = null }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SavingsGoalCard(
    row: SavingsGoalRow,
    averageMonthlyLeftover: Money?,
    onTopUpClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.goal.name, fontWeight = FontWeight.Bold)
                Text(
                    followLabel(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onTopUpClick) { Icon(Icons.Filled.Add, contentDescription = "Handmatige aanvulling voor ${row.goal.name}") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${row.progress.toDisplayString()} / ${row.goal.targetAmount.toDisplayString()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            row.bufferMonthsCovered?.let { months ->
                Text(
                    "${formatOneDecimal(months)} maanden vaste lasten",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            val fraction = row.percentage.coerceIn(0, 100) / 100f
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            row.paceFraction?.let { pace -> PaceTick(pace, height = 10.dp) }
        }

        row.requiredMonthlyContribution?.let { required ->
            val statusColors = LocalBudgetStatusColors.current
            val fits = row.fitsWithinLeftover
            Text(
                buildString {
                    append("${required.toDisplayString()}/maand nodig")
                    row.goal.targetDate?.let { append(" tot ${it.toShortDisplayString()}") }
                    when (fits) {
                        true -> append(" — past binnen je ruimte" + (averageMonthlyLeftover?.let { " (~${it.toDisplayString()}/maand over)" } ?: ""))
                        false -> append(" — meer dan je gemiddelde ruimte" + (averageMonthlyLeftover?.let { " (~${it.toDisplayString()}/maand over)" } ?: ""))
                        null -> {}
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (fits == false) statusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            "Verwijderen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDeleteClick).padding(top = 8.dp),
        )
    }
}

@Composable
private fun AchievedGoalCard(row: SavingsGoalRow, onArchiveClick: () -> Unit, onRollForwardClick: () -> Unit) {
    val statusColors = LocalBudgetStatusColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(statusColors.ok.copy(alpha = 0.10f))
            .border(1.dp, statusColors.ok, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.goal.name, fontWeight = FontWeight.Bold)
            Text(row.goal.targetAmount.toDisplayString(), color = statusColors.ok, fontWeight = FontWeight.Bold)
        }
        Text(
            "Gehaald" + (row.achievedDate?.let { " op ${it.toShortDisplayString()}" } ?: ""),
            color = statusColors.ok,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        row.earlyByDays?.let { days ->
            Text(
                formatEarlyLate(days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 10.dp)) {
            Text(
                "Archiveren",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onArchiveClick),
            )
            Text(
                "Nieuw doel hiermee",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onRollForwardClick),
            )
        }
    }
}

@Composable
private fun ArchivedGoalRow(row: SavingsGoalRow, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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

/** Same tempostreepje pattern as Budget's own PaceTick - a vertical mark at "how far along you should be" against the progress bar right behind it. */
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

private fun followLabel(row: SavingsGoalRow): String {
    val account = row.linkedAccount
    return if (account != null) {
        "Volgt ${account.name} ${account.ibanMasked} · ${row.depositCountThisYear} ${if (row.depositCountThisYear == 1) "storting" else "stortingen"} dit jaar"
    } else {
        "Volgt categorie: ${row.category?.name ?: "onbekend"}"
    }
}

private fun formatEarlyLate(days: Int): String = when {
    days > 6 -> "${days / 7} ${if (days / 7 == 1) "week" else "weken"} eerder dan gepland"
    days > 0 -> "$days ${if (days == 1) "dag" else "dagen"} eerder dan gepland"
    days == 0 -> "precies op schema gehaald"
    days > -7 -> "${-days} ${if (days == -1) "dag" else "dagen"} later dan gepland"
    else -> "${-days / 7} ${if (-days / 7 == 1) "week" else "weken"} later dan gepland"
}

private fun LocalDate.toShortDisplayString(): String =
    "${dayOfMonth} ${monthName()} ${year}"

private fun LocalDate.monthName(): String = listOf(
    "jan", "feb", "mrt", "apr", "mei", "jun", "jul", "aug", "sep", "okt", "nov", "dec",
)[monthValue - 1]

/** Always a comma decimal separator, regardless of device locale - consistent with [Money.toDisplayString]'s own hardcoded Dutch formatting. */
private fun formatOneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    return "${tenths / 10},${kotlin.math.abs(tenths % 10)}"
}

@Composable
private fun AddGoalDialog(
    categories: List<Category>,
    accounts: List<Account>,
    prefill: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (name: String, target: Money, categoryId: Long, linkedAccountId: Long?, targetDate: LocalDate?) -> Unit,
) {
    var name by remember { mutableStateOf(prefill?.let { "${it.name} (vervolg)" } ?: "") }
    var targetText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(prefill?.categoryId ?: categories.firstOrNull()?.id) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var linkedAccountId by remember { mutableStateOf(prefill?.linkedAccountId) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var targetDateText by remember { mutableStateOf("") }

    val target = parseEuroInput(targetText)
    val targetDate = if (targetDateText.isBlank()) null else runCatching { LocalDate.parse(targetDateText) }.getOrNull()
    val targetDateValid = targetDateText.isBlank() || targetDate != null
    val isValid = name.isNotBlank() && target != null && target.cents > 0 && categoryId != null && targetDateValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prefill != null) "Nieuw doel hiermee" else "Nieuw spaardoel") },
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onSave(name.trim(), target!!, categoryId!!, linkedAccountId, targetDate) },
            ) { Text("Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

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
