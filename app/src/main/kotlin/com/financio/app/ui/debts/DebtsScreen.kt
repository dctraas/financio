package com.financio.app.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Debt
import com.financio.core.model.DebtDirection
import com.financio.core.model.Money
import java.time.LocalDate

private sealed interface DebtDialogMode {
    data object Add : DebtDialogMode
    data class Edit(val debt: Debt) : DebtDialogMode
}

@Composable
fun DebtsScreen(onBackClick: () -> Unit, viewModel: DebtsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var dialogMode by remember { mutableStateOf<DebtDialogMode?>(null) }
    var payingOff by remember { mutableStateOf<Debt?>(null) }
    var deleting by remember { mutableStateOf<Debt?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    if (!state.loaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schulden & leningen") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isEmpty) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Text(
                        "Nog geen schulden of leningen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Een lening bij een familielid, een informele IOU, of geld dat iemand nog " +
                            "aan jou terug moet betalen - het saldo hou je hier zelf bij door " +
                            "betalingen te registreren, niet via je geïmporteerde transacties.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    AddDebtLink(onClick = { dialogMode = DebtDialogMode.Add })
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    item { AddDebtLink(onClick = { dialogMode = DebtDialogMode.Add }) }
                    items(state.activeDebts, key = { it.id }) { debt ->
                        DebtCard(
                            debt = debt,
                            onClick = { dialogMode = DebtDialogMode.Edit(debt) },
                            onRecordPaymentClick = { payingOff = debt },
                        )
                    }
                    if (state.settledDebts.isNotEmpty()) {
                        items(state.settledDebts, key = { "settled-${it.id}" }) { debt ->
                            SettledDebtCard(debt = debt, onArchiveClick = { viewModel.archiveDebt(debt.id) })
                        }
                    }
                    if (state.archivedDebts.isNotEmpty()) {
                        item {
                            Text(
                                if (showArchived) "Gearchiveerde schulden verbergen ▴" else "${state.archivedDebts.size} gearchiveerde schuld(en) tonen ▾",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 16.dp).clickable { showArchived = !showArchived },
                            )
                        }
                        if (showArchived) {
                            items(state.archivedDebts, key = { "archived-${it.id}" }) { debt ->
                                ArchivedDebtRow(debt = debt, onClick = { dialogMode = DebtDialogMode.Edit(debt) }, onDeleteClick = { deleting = debt })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    dialogMode?.let { mode ->
        DebtDialog(
            mode = mode,
            onDismiss = { dialogMode = null },
            onSave = { name, direction, counterpartyName, principal, interestRateBasisPoints, targetPayoffDate, notes ->
                when (mode) {
                    is DebtDialogMode.Edit -> viewModel.editDebt(mode.debt.id, name, counterpartyName, principal, interestRateBasisPoints, targetPayoffDate, notes)
                    DebtDialogMode.Add -> viewModel.addDebt(name, direction, counterpartyName, principal, interestRateBasisPoints, targetPayoffDate, notes)
                }
                dialogMode = null
            },
            onRecordPaymentClick = (mode as? DebtDialogMode.Edit)?.let { edit -> { payingOff = edit.debt; dialogMode = null } },
            onDeleteClick = (mode as? DebtDialogMode.Edit)?.let { edit -> { deleting = edit.debt; dialogMode = null } },
        )
    }

    payingOff?.let { debt ->
        RecordPaymentDialog(
            debt = debt,
            onDismiss = { payingOff = null },
            onSave = { amount -> viewModel.recordPayment(debt.id, amount); payingOff = null },
        )
    }

    deleting?.let { debt ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Schuld verwijderen?") },
            text = { Text("'${debt.name}' wordt verwijderd. Dit kan niet ongedaan worden gemaakt.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteDebt(debt.id); deleting = null }) { Text("Verwijderen") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun AddDebtLink(onClick: () -> Unit) {
    Text(
        "+ Nieuwe schuld",
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

private fun Money.magnitudeString(): String = toDisplayString().removePrefix("-").removePrefix("€")

private fun directionLabel(debt: Debt): String = when (debt.direction) {
    DebtDirection.I_OWE -> "Ik ben dit schuldig aan ${debt.counterpartyName}"
    DebtDirection.OWED_TO_ME -> "${debt.counterpartyName} is dit aan mij verschuldigd"
}

private fun progressVerb(debt: Debt): String = when (debt.direction) {
    DebtDirection.I_OWE -> "afbetaald"
    DebtDirection.OWED_TO_ME -> "terugbetaald"
}

@Composable
private fun DebtCard(debt: Debt, onClick: () -> Unit, onRecordPaymentClick: () -> Unit) {
    val barColor = LocalBudgetStatusColors.current.ok
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
        Text(debt.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            directionLabel(debt) + (debt.interestRateBasisPoints?.let { " · ${it.basisPointsToPercentString()} rente" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 14.dp)) {
            Text(debt.currentBalance.toDisplayString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                " / ${debt.principal.magnitudeString()}",
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        DebtProgressBar(percentage = debt.percentagePaidOff, color = barColor)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${debt.percentagePaidOff}% ${progressVerb(debt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Betaling registreren",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onRecordPaymentClick),
            )
        }
    }
}

@Composable
private fun DebtProgressBar(percentage: Int, color: androidx.compose.ui.graphics.Color) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp).height(12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        val fraction = percentage.coerceIn(0, 100) / 100f
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun SettledDebtCard(debt: Debt, onArchiveClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(debt.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Icon(Icons.Filled.Check, contentDescription = progressVerb(debt), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            "${debt.principal.toDisplayString()} volledig ${progressVerb(debt)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "Archiveren",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 14.dp).clickable(onClick = onArchiveClick),
        )
    }
}

@Composable
private fun ArchivedDebtRow(debt: Debt, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(debt.name, fontWeight = FontWeight.SemiBold)
            Text(debt.principal.toDisplayString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Verwijderen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDeleteClick),
        )
    }
}

/** "3,50%" from 350 basis points - always a comma decimal, consistent with [Money.toDisplayString]'s own hardcoded Dutch formatting. */
private fun Int.basisPointsToPercentString(): String {
    val tenths = this / 10
    return "${tenths / 10},${tenths % 10}%"
}

@Composable
private fun DebtDialog(
    mode: DebtDialogMode,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        direction: DebtDirection,
        counterpartyName: String,
        principal: Money,
        interestRateBasisPoints: Int?,
        targetPayoffDate: LocalDate?,
        notes: String?,
    ) -> Unit,
    onRecordPaymentClick: (() -> Unit)?,
    onDeleteClick: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(if (mode is DebtDialogMode.Edit) mode.debt.name else "") }
    var direction by remember { mutableStateOf(if (mode is DebtDialogMode.Edit) mode.debt.direction else DebtDirection.I_OWE) }
    var counterpartyName by remember { mutableStateOf(if (mode is DebtDialogMode.Edit) mode.debt.counterpartyName else "") }
    var principalText by remember { mutableStateOf(if (mode is DebtDialogMode.Edit) formatEuroInput(mode.debt.principal) else "") }
    var interestText by remember {
        mutableStateOf(if (mode is DebtDialogMode.Edit) mode.debt.interestRateBasisPoints?.let { formatBasisPointsInput(it) } ?: "" else "")
    }
    var targetDateText by remember { mutableStateOf(if (mode is DebtDialogMode.Edit) mode.debt.targetPayoffDate?.toString() ?: "" else "") }
    var notes by remember { mutableStateOf(if (mode is DebtDialogMode.Edit) mode.debt.notes ?: "" else "") }

    val principal = parseEuroInput(principalText)
    val interestBasisPoints = parseBasisPointsInput(interestText)
    val interestValid = interestText.isBlank() || interestBasisPoints != null
    val targetDate = if (targetDateText.isBlank()) null else runCatching { LocalDate.parse(targetDateText) }.getOrNull()
    val targetDateValid = targetDateText.isBlank() || targetDate != null
    val isValid = name.isNotBlank() && counterpartyName.isNotBlank() && principal != null && principal.cents > 0 && interestValid && targetDateValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode is DebtDialogMode.Edit) "Schuld bewerken" else "Nieuwe schuld") },
        text = {
            Column {
                if (mode is DebtDialogMode.Add) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        FilterChip(selected = direction == DebtDirection.I_OWE, onClick = { direction = DebtDirection.I_OWE }, label = { Text("Ik ben dit schuldig") })
                        FilterChip(selected = direction == DebtDirection.OWED_TO_ME, onClick = { direction = DebtDirection.OWED_TO_ME }, label = { Text("Aan mij verschuldigd") })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    placeholder = { Text("bijv. Lening verbouwing") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = counterpartyName,
                    onValueChange = { counterpartyName = it },
                    label = { Text(if (direction == DebtDirection.I_OWE) "Schuldeiser" else "Schuldenaar") },
                    placeholder = { Text("bijv. Jan de Vries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text("Bedrag") },
                    prefix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = interestText,
                    onValueChange = { interestText = it },
                    label = { Text("Rente per jaar (optioneel)") },
                    placeholder = { Text("bijv. 3,5") },
                    suffix = { Text("%") },
                    singleLine = true,
                    isError = !interestValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = targetDateText,
                    onValueChange = { targetDateText = it },
                    label = { Text("Streefdatum aflossing (optioneel)") },
                    placeholder = { Text("JJJJ-MM-DD") },
                    singleLine = true,
                    isError = !targetDateValid,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notities (optioneel)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Alleen rente-percentage en streefdatum zijn puur informatief - er wordt geen " +
                        "aflossingsschema berekend. Het openstaande bedrag pas je zelf aan via " +
                        "\"Betaling registreren\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (onRecordPaymentClick != null) {
                    Text(
                        "Betaling registreren →",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onRecordPaymentClick),
                    )
                }
                if (onDeleteClick != null) {
                    Text(
                        "Schuld verwijderen",
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
                onClick = {
                    onSave(name.trim(), direction, counterpartyName.trim(), principal!!, interestBasisPoints, targetDate, notes.trim().ifBlank { null })
                },
            ) { Text(if (mode is DebtDialogMode.Edit) "Opslaan" else "Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun RecordPaymentDialog(debt: Debt, onDismiss: () -> Unit, onSave: (Money) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = parseEuroInput(text)
    val isValid = parsed != null && parsed.cents > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Betaling registreren") },
        text = {
            Column {
                Text(
                    "Voor '${debt.name}' - het bedrag gaat van het openstaande saldo af " +
                        "(nu ${debt.currentBalance.toDisplayString()}).",
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
        confirmButton = { TextButton(enabled = isValid, onClick = { onSave(parsed!!) }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

private fun formatEuroInput(money: Money): String =
    "${money.cents / 100},${(money.cents % 100).toString().padStart(2, '0')}"

private fun parseEuroInput(text: String): Money? =
    if (text.isBlank()) null else runCatching { Money.parseCommaDecimal(if (text.contains(",")) text else "$text,00") }.getOrNull()

/** "3,5" -> 350 basis points. Accepts a bare integer ("3" -> 300) as well as one decimal ("3,5"). */
private fun parseBasisPointsInput(text: String): Int? {
    if (text.isBlank()) return null
    val normalized = text.trim().replace(",", ".")
    val value = normalized.toDoubleOrNull() ?: return null
    return Math.round(value * 100).toInt()
}

private fun formatBasisPointsInput(basisPoints: Int): String {
    val tenths = basisPoints / 10
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
}
