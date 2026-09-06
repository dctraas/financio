package com.financio.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.core.backup.BackupSerializer
import com.financio.core.model.Category
import com.financio.core.model.Money
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun SettingsScreen(
    onManageCategoriesClick: () -> Unit,
    onSubscriptionsClick: () -> Unit,
    onSavingsGoalsClick: () -> Unit,
    onAccountsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val context = LocalContext.current

    // Set right before launching an export picker, read back in its callback once the user picks
    // a location — CreateDocument's contract only gives us the Uri, not a way to pass content in.
    var pendingExportContent by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExportContent.toByteArray()) }
    }
    fun export(content: String, suggestedName: String) {
        pendingExportContent = content
        exportLauncher.launch(suggestedName)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        if (content != null) viewModel.importBackup(content)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Instellingen") }) }) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item { SectionHeader("Vergrendeling") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ontgrendelen met biometrie", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Vraagt vingerafdruk of gezicht bij het openen van de app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.biometricLockEnabled, onCheckedChange = viewModel::setBiometricLockEnabled)
                }
            }

            item { SectionHeader("Budgetlimieten") }
            if (state.categories.isEmpty()) {
                item {
                    Text(
                        "Categorieën worden aangemaakt zodra de app voor het eerst opstart — even geduld.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.categories, key = { "limit-${it.id}" }) { category ->
                    BudgetLimitRow(
                        category = category,
                        currentLimit = state.limitsByCategory[category.id],
                        rolloverEnabled = state.rolloverByCategory[category.id] ?: false,
                        onSave = { limit -> viewModel.setLimit(category.id, limit) },
                        onRolloverChange = { enabled -> viewModel.setRollover(category.id, enabled) },
                    )
                }
            }

            item { SectionHeader("Abonnementen") }
            item {
                Text(
                    "Terugkerende afschrijvingen die Financio zelf herkent in je transactiehistorie " +
                        "— geen bankkoppeling nodig.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Bekijken →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onSubscriptionsClick),
                )
            }

            item { SectionHeader("Rekeningen") }
            item {
                Text(
                    "Meerdere rekeningen beheren — elk met een eigen CSV-/MT940-import.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Beheren →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onAccountsClick),
                )
            }

            item { SectionHeader("Spaardoelen") }
            item {
                Text(
                    "Doelbedrag koppelen aan een categorie en de voortgang volgen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Bekijken →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onSavingsGoalsClick),
                )
            }

            item { SectionHeader("Categorieën & regels") }
            item {
                Text(
                    "Categorieën toevoegen of verwijderen, en regels beheren waarmee transacties " +
                        "automatisch worden gecategoriseerd.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Beheren →",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onManageCategoriesClick),
                )
            }

            item { SectionHeader("Importeren & exporteren") }
            item {
                Text(
                    "Categorieën en regels als bestand bewaren of overzetten. Categorieën worden " +
                        "op naam gematcht, regels op categorienaam + patroon — bestaat iets al " +
                        "lokaal, dan blijft dat ongewijzigd staan; er wordt alleen toegevoegd.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                ExportLink("Alles exporteren") {
                    export(BackupSerializer.exportAll(state.categories, state.rules), "financio-alles.json")
                }
                ExportLink("Alleen categorieën exporteren") {
                    export(BackupSerializer.exportCategories(state.categories), "financio-categorieen.json")
                }
                ExportLink("Alleen regels exporteren") {
                    export(BackupSerializer.exportRules(state.rules, state.categories.associateBy { it.id }), "financio-regels.json")
                }
                ExportLink("Bestand importeren →") {
                    importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }
            }
        }
    }

    importResult?.let { result ->
        ImportResultDialog(result = result, onDismiss = viewModel::clearImportResult)
    }
}

@Composable
private fun ExportLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

@Composable
private fun ImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result is ImportResult.Failed) "Importeren mislukt" else "Importeren voltooid") },
        text = {
            Text(
                when (result) {
                    is ImportResult.Failed -> result.message
                    is ImportResult.Success -> "${result.categoriesAdded} categorieën toegevoegd " +
                        "(${result.categoriesSkipped} bestonden al), ${result.rulesAdded} regels " +
                        "toegevoegd (${result.rulesSkipped} overgeslagen — bestonden al of onbekende categorie)."
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun BudgetLimitRow(
    category: Category,
    currentLimit: Money?,
    rolloverEnabled: Boolean,
    onSave: (Money) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
) {
    // Keyed on category.id so a re-emission of the budgets flow (e.g. after saving a *different*
    // category's limit) doesn't clobber what the user is still typing in this field.
    var text by remember(category.id) { mutableStateOf(currentLimit?.toEuroInputString() ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(category.name, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("geen limiet") },
                prefix = { Text("€") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp),
            )
            IconButton(onClick = {
                parseEuroInput(text)?.let(onSave)
            }) { Icon(Icons.Filled.Check, contentDescription = "Limiet opslaan") }
        }
        // Rollover only means something once there's a limit to roll over from - hidden until then
        // rather than letting the user flip a toggle that has nothing to do yet.
        if (currentLimit != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Restant meenemen naar volgende maand",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = rolloverEnabled, onCheckedChange = onRolloverChange)
            }
        }
    }
}

private fun Money.toEuroInputString(): String {
    val absCents = kotlin.math.abs(cents)
    return "${absCents / 100},${(absCents % 100).toString().padStart(2, '0')}"
}

private fun parseEuroInput(text: String): Money? =
    if (text.isBlank()) null else runCatching { Money.parseCommaDecimal(normalizeEuroInput(text)) }.getOrNull()

/** Accepts "450" as well as "450,00" — a bare integer has no comma for [Money.parseCommaDecimal] to split on. */
private fun normalizeEuroInput(text: String): String = if (text.contains(",")) text else "$text,00"
