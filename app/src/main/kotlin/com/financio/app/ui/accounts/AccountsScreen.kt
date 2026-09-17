package com.financio.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Account
import com.financio.core.model.Money
import com.financio.core.usecase.AccountBalance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AccountsScreen(onBackClick: () -> Unit, onImportClick: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    var settingBalance by remember { mutableStateOf<Account?>(null) }
    var managingAccount by remember { mutableStateOf<AccountRow?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    if (!state.loaded) return

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AccountsHeader(onBackClick)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            if (state.visibleAccounts.isEmpty()) {
                item {
                    Text(
                        "Elke rekening heeft zijn eigen transacties, geïmporteerd via een eigen CSV- of " +
                            "MT940-export. Categorieën, budgetten en spaardoelen gelden over alle " +
                            "rekeningen heen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(top = 8.dp, bottom = 20.dp)) {
                        Text("Samen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            state.total.toDisplayString(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "${state.includedCount} ${if (state.includedCount == 1) "rekening" else "rekeningen"} meegerekend",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                val includedRows = state.visibleAccounts.filter { !it.account.excludedFromTotal }
                val excludedRows = state.visibleAccounts.filter { it.account.excludedFromTotal }

                items(includedRows, key = { it.account.id }) { row ->
                    AccountCard(row = row, onManageClick = { managingAccount = row }, onImportClick = onImportClick)
                }

                if (excludedRows.isNotEmpty()) {
                    item { SectionLabel("NIET MEEGEREKEND") }
                    itemsIndexed(excludedRows, key = { _, row -> "excluded-${row.account.id}" }) { index, row ->
                        ExcludedAccountRow(row = row, onClick = { managingAccount = row })
                        if (index < excludedRows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // Outside the empty/non-empty branch above: even with every visible account hidden
            // (visibleAccounts empty), "tonen" and "toevoegen" must stay reachable - a hidden
            // account or a stalled setup is never a one-way trap.
            if (state.hiddenAccounts.isNotEmpty()) {
                item {
                    Text(
                        if (showHidden) "Verborgen rekeningen verbergen ▴" else "${state.hiddenAccounts.size} verborgen rekening(en) tonen ▾",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 20.dp).clickable { showHidden = !showHidden },
                    )
                }
                if (showHidden) {
                    items(state.hiddenAccounts, key = { "hidden-${it.account.id}" }) { row ->
                        AccountCard(row = row, onManageClick = { managingAccount = row }, onImportClick = onImportClick)
                    }
                }
            }

            item { AddAccountButton(onClick = { adding = true }, modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)) }
        }
    }

    managingAccount?.let { row ->
        AccountActionsSheet(
            row = row,
            onDismiss = { managingAccount = null },
            onRename = { managingAccount = null; renaming = row.account },
            onToggleHidden = { managingAccount = null; viewModel.setHidden(row.account.id, !row.account.hidden) },
            onToggleExcluded = { managingAccount = null; viewModel.setExcludedFromTotal(row.account.id, !row.account.excludedFromTotal) },
            onFillBalance = { managingAccount = null; settingBalance = row.account },
        )
    }

    if (adding) {
        AddAccountDialog(
            onDismiss = { adding = false },
            onSave = { name, iban ->
                viewModel.addAccount(name, iban)
                adding = false
            },
        )
    }

    renaming?.let { account ->
        RenameAccountDialog(
            currentName = account.name,
            onDismiss = { renaming = null },
            onSave = { newName ->
                viewModel.renameAccount(account.id, newName)
                renaming = null
            },
        )
    }

    settingBalance?.let { account ->
        SetManualBalanceDialog(
            accountName = account.name,
            currentBalance = account.manualBalance,
            onDismiss = { settingBalance = null },
            onSave = { balance ->
                viewModel.setManualBalance(account.id, balance)
                settingBalance = null
            },
        )
    }
}

@Composable
private fun AccountsHeader(onBackClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 12.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text("Rekeningen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = LocalFinancioColors.current.inkFaint,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

private val monthOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("nl"))

/** "jan – 14 sep 2026" - a coarse start (just the month the import history begins in) against a precise end. */
private fun coverageLabel(start: LocalDate, end: LocalDate): String =
    "${start.format(monthOnlyFormatter).lowercase()} – ${end.toShortDisplayString()} ${end.year}"

@Composable
private fun AccountCard(row: AccountRow, onManageClick: () -> Unit, onImportClick: () -> Unit) {
    val warningColor = LocalBudgetStatusColors.current.warning
    val account = row.account

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
            )
            when (val balance = row.balance) {
                is AccountBalance.Known -> Text(balance.amount.toDisplayString(), fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                AccountBalance.Unknown -> Text("saldo onbekend", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = warningColor)
            }
        }
        Text(
            buildString {
                append(account.ibanMasked)
                row.followedByGoalName?.let { append(" · gevolgd door $it") }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (row.monthsBehind > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.secondary))
                    Text(
                        "${row.monthsBehind} ${if (row.monthsBehind == 1) "maand" else "maanden"} achter — importeer een nieuw bestand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text("Nu doen", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onImportClick))
            } else if (row.coverageStart != null && row.coverageEnd != null) {
                Text(
                    "${coverageLabel(row.coverageStart, row.coverageEnd)} · ${row.transactionCount} transacties",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Beheren", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onManageClick))
            } else {
                Text("nog geen import", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Beheren", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onManageClick))
            }
        }
    }
}

/** "als compacte regels" - no card, no dekking, just naam/kenmerk + saldo + how it got excluded, since these deliberately don't count toward "Samen". */
@Composable
private fun ExcludedAccountRow(row: AccountRow, onClick: () -> Unit) {
    val account = row.account
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (account.manualBalance != null) "handmatig saldo" else account.ibanMasked,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        when (val balance = row.balance) {
            is AccountBalance.Known -> Text(balance.amount.toDisplayString(), fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccountBalance.Unknown -> Text("saldo onbekend", fontSize = 14.sp, color = LocalBudgetStatusColors.current.warning)
        }
    }
}

@Composable
private fun AddAccountButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dashColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Rekening toevoegen", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccountActionsSheet(
    row: AccountRow,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleExcluded: () -> Unit,
    onFillBalance: () -> Unit,
) {
    val account = row.account
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            AccountActionRow("Naam wijzigen", onRename)
            if (row.balance is AccountBalance.Unknown) AccountActionRow("Saldo invullen", onFillBalance)
            AccountActionRow(if (account.hidden) "Tonen" else "Verbergen", onToggleHidden)
            AccountActionRow(if (account.excludedFromTotal) "Meetellen in Samen" else "Niet meetellen in Samen", onToggleExcluded, isLast = true)
        }
    }
}

@Composable
private fun AccountActionRow(label: String, onClick: () -> Unit, isLast: Boolean = false) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        if (!isLast) HorizontalDivider()
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onSave: (name: String, ibanMasked: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var iban by remember { mutableStateOf("") }
    val isValid = name.isNotBlank() && iban.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nieuwe rekening") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    placeholder = { Text("bijv. ING Spaarrekening") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = iban,
                    onValueChange = { iban = it },
                    label = { Text("IBAN (gemaskeerd)") },
                    placeholder = { Text("bijv. NL•• INGB •••• •• 1234") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Importeer daarna een aparte CSV- of MT940-export voor deze rekening via " +
                        "Importeren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onSave(name.trim(), iban.trim()) }) { Text("Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** "Een IBAN is geen naam" - the only thing this dialog edits. */
@Composable
private fun RenameAccountDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naam wijzigen") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun SetManualBalanceDialog(accountName: String, currentBalance: Money?, onDismiss: () -> Unit, onSave: (Money?) -> Unit) {
    var text by remember { mutableStateOf(currentBalance?.toEuroInputString() ?: "") }
    val parsed = parseEuroInput(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saldo voor $accountName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                prefix = { Text("€") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { onSave(parsed) }) { Text("Opslaan") }
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
