package com.financio.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.core.backup.BackupSerializer
import com.financio.core.backup.TransactionCsvExporter
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Nested inside Meer, reached from a "Back-up & export" row — the redesign brief moves this out
 * of a first-class Settings section (it's not something most people touch often) and adds the
 * transactions CSV export that didn't exist before ([TransactionCsvExporter]).
 */
@Composable
fun BackupExportScreen(onBackClick: () -> Unit, viewModel: BackupExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val context = LocalContext.current

    // Set right before launching an export picker, read back in its callback once the user picks
    // a location — CreateDocument's contract only gives us the Uri, not a way to pass content in.
    var pendingExportContent by remember { mutableStateOf("") }
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExportContent.toByteArray()) }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExportContent.toByteArray()) }
    }
    fun exportJson(content: String, suggestedName: String) {
        pendingExportContent = content
        jsonExportLauncher.launch(suggestedName)
    }
    fun exportCsv(content: String, suggestedName: String) {
        pendingExportContent = content
        csvExportLauncher.launch(suggestedName)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        if (content != null) viewModel.importBackup(content)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Back-up & export") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Alles wordt op een stabiel kenmerk gematcht in plaats van een intern id — " +
                    "rekeningen op IBAN, categorieën op naam, transacties op datum + bedrag + " +
                    "tegenpartij. Bestaat iets al lokaal, dan blijft dat ongewijzigd staan; er " +
                    "wordt alleen toegevoegd, dus een bestand nog eens importeren is veilig.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            ExportLink("Volledige back-up exporteren") {
                exportJson(
                    BackupSerializer.exportAll(
                        accounts = state.accounts,
                        categories = state.categories,
                        rules = state.rules,
                        transactions = state.transactions,
                        splitsByTransactionId = state.splitsByTransactionId,
                        budgets = state.budgets,
                        savingsGoals = state.savingsGoals,
                    ),
                    "financio-backup.json",
                )
            }
            ExportLink("Alleen categorieën exporteren") {
                exportJson(BackupSerializer.exportCategories(state.categories), "financio-categorieen.json")
            }
            ExportLink("Alleen regels exporteren") {
                exportJson(BackupSerializer.exportRules(state.rules, state.categories.associateBy { it.id }), "financio-regels.json")
            }
            ExportLink("Transacties exporteren (CSV)") {
                exportCsv(TransactionCsvExporter.export(state.transactions, state.categories.associateBy { it.id }), "financio-transacties.csv")
            }
            ExportLink("Bestand importeren →") {
                importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
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
                    is ImportResult.Success -> buildString {
                        append("${result.accountsAdded} rekeningen, ${result.categoriesAdded} categorieën, ")
                        append("${result.rulesAdded} regels, ${result.budgetsAdded} budgetten, ")
                        append("${result.goalsAdded} spaardoelen en ${result.transactionsAdded} transacties toegevoegd.")
                        val skipped = result.accountsSkipped + result.categoriesSkipped + result.rulesSkipped +
                            result.budgetsSkipped + result.goalsSkipped + result.transactionsSkipped
                        if (skipped > 0) append(" $skipped overgeslagen — bestonden al, of verwezen naar iets onbekends.")
                    }
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
