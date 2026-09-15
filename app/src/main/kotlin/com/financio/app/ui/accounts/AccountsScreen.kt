package com.financio.app.ui.accounts

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Account
import com.financio.core.model.Money
import com.financio.core.usecase.AccountBalance

@Composable
fun AccountsScreen(onBackClick: () -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Account?>(null) }
    var settingBalance by remember { mutableStateOf<Account?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var dismissedUnknownWarnings by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rekeningen") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Rekening toevoegen")
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
                    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                        Text(state.total.toDisplayString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append("${state.includedCount} ${if (state.includedCount == 1) "rekening" else "rekeningen"} meegerekend")
                                if (state.excludedCount > 0) append(" · ${state.excludedCount} buiten het totaal")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.visibleAccounts, key = { it.account.id }) { row ->
                AccountCard(
                    row = row,
                    unknownWarningDismissed = row.account.id in dismissedUnknownWarnings,
                    onDismissUnknownWarning = { dismissedUnknownWarnings = dismissedUnknownWarnings + row.account.id },
                    onFillBalance = { settingBalance = row.account },
                    onRename = { renaming = row.account },
                    onToggleHidden = { viewModel.setHidden(row.account.id, true) },
                    onToggleExcluded = { viewModel.setExcludedFromTotal(row.account.id, !row.account.excludedFromTotal) },
                )
            }

            if (state.hiddenAccounts.isNotEmpty()) {
                item {
                    Text(
                        if (showHidden) "Verborgen rekeningen verbergen ▴" else "${state.hiddenAccounts.size} verborgen rekening(en) tonen ▾",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp).clickable { showHidden = !showHidden },
                    )
                }
                if (showHidden) {
                    items(state.hiddenAccounts, key = { "hidden-${it.account.id}" }) { row ->
                        AccountCard(
                            row = row,
                            unknownWarningDismissed = true,
                            onDismissUnknownWarning = {},
                            onFillBalance = { settingBalance = row.account },
                            onRename = { renaming = row.account },
                            onToggleHidden = { viewModel.setHidden(row.account.id, false) },
                            onToggleExcluded = { viewModel.setExcludedFromTotal(row.account.id, !row.account.excludedFromTotal) },
                        )
                    }
                }
            }
        }
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
private fun AccountCard(
    row: AccountRow,
    unknownWarningDismissed: Boolean,
    onDismissUnknownWarning: () -> Unit,
    onFillBalance: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleExcluded: () -> Unit,
) {
    val account = row.account
    val isUnknown = row.balance is AccountBalance.Unknown
    val warningColor = LocalBudgetStatusColors.current.warning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isUnknown && !unknownWarningDismissed) warningColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isUnknown && !unknownWarningDismissed) warningColor else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(account.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            when (val balance = row.balance) {
                is AccountBalance.Known -> Text(balance.amount.toDisplayString(), fontWeight = FontWeight.SemiBold)
                AccountBalance.Unknown -> Text("onbekend", fontWeight = FontWeight.SemiBold, color = warningColor)
            }
        }
        Text(
            buildString {
                append(account.ibanMasked)
                append(" · ${row.transactionCount} transacties")
                if (row.followedByGoal) append(" · volgt doel")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (row.coverageStart != null && row.coverageEnd != null) {
            CoverageBar(monthsBehind = row.monthsBehind, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            Text(
                buildString {
                    append("gegevens ${row.coverageStart.toShortDisplayString()} – ${row.coverageEnd.toShortDisplayString()}")
                    if (row.monthsBehind > 0) append(" · ${row.monthsBehind} ${if (row.monthsBehind == 1) "maand" else "maanden"} achter")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isUnknown && !unknownWarningDismissed) {
            Text(
                "Deze rekening heeft geen eindsaldo in de import, dus telt niet mee in je totaal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Saldo invullen", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onFillBalance))
                Text("Negeren", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onDismissUnknownWarning))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 12.dp)) {
            ActionLink("Naam wijzigen", onRename)
            ActionLink(if (account.hidden) "Tonen" else "Verbergen", onToggleHidden)
            ActionLink(if (account.excludedFromTotal) "Meetellen" else "Niet meetellen", onToggleExcluded)
            // Only here (not duplicated) once the warning block above is dismissed or hidden -
            // otherwise this stays reachable from the warning itself.
            if (isUnknown && unknownWarningDismissed) ActionLink("Saldo invullen", onFillBalance)
        }
    }
}

@Composable
private fun ActionLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Fully green when this account's data reaches today; increasingly grey the further "achter" it is - capped at 6 months so a very stale account still reads as "mostly empty" rather than negative. */
@Composable
private fun CoverageBar(monthsBehind: Int, modifier: Modifier = Modifier) {
    val fraction = (1f - monthsBehind / 6f).coerceIn(0f, 1f)
    val statusColors = LocalBudgetStatusColors.current
    val color = if (monthsBehind == 0) statusColors.ok else statusColors.warning
    BoxWithConstraints(modifier.height(6.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
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
