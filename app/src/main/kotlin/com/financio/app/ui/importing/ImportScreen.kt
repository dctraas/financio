package com.financio.app.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.financio.core.model.Category
import com.financio.core.usecase.UncategorizedGroup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ImportScreen(onDone: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        val fileName = uri.lastPathSegment ?: "bestand"
        if (content != null) viewModel.onFilePicked(fileName, content)
    }

    LaunchedEffect(state) {
        if (state is ImportUiState.Imported) onDone()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Importeren") }) }) { padding ->
        when (val current = state) {
            is ImportUiState.PickFile -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Kies een CSV- of MT940-export uit Mijn ING.", style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Bestand kiezen") }
            }

            is ImportUiState.Loading -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Bezig met inlezen…")
            }

            is ImportUiState.Failed -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
            }

            is ImportUiState.Ready -> ReadyContent(current, categories, padding, viewModel)

            is ImportUiState.Imported -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Geïmporteerd.")
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: ImportUiState.Ready,
    categories: List<Category>,
    padding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: ImportViewModel,
) {
    val preview = state.preview
    val groups = preview.needsCategoryGrouped

    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(state.fileName, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(
                "${preview.total} transacties gevonden — ${preview.ready.size} automatisch gecategoriseerd, " +
                    "${preview.needsCategory.size} te controleren, ${preview.duplicateCount} duplicaten overgeslagen.",
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (groups.isNotEmpty()) {
            item {
                Text(
                    "Te controleren — ${groups.size} tegenpartijen, één keuze per tegenpartij",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Gesorteerd op grootste totaalbedrag eerst — je keuze geldt voor alle transacties van " +
                        "deze tegenpartij, nu en bij toekomstige imports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(groups, key = { it.counterpartyName }) { group ->
                UncategorizedGroupRow(
                    group = group,
                    categories = categories,
                    selectedCategoryId = state.manualCategoryChoices[group.counterpartyName],
                    onSelect = { categoryId -> viewModel.assignCategory(group.counterpartyName, categoryId) },
                )
            }
        }

        item {
            Button(
                onClick = viewModel::confirm,
                enabled = preview.total > 0,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            ) { Text("${preview.total} transacties importeren") }
        }
    }
}

@Composable
private fun UncategorizedGroupRow(
    group: UncategorizedGroup,
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedCategoryId }?.name

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(group.counterpartyName, fontWeight = FontWeight.Bold)
            Text(
                groupSummary(group),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            Text(
                selectedName ?: "Kies categorie ▾",
                color = if (selectedName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onSelect(category.id)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * "3× · €45,20 · 4 – 12 sep" for a repeated merchant, or "€30,63 · 4 sep" for a one-off — the
 * context that actually helps decide a category (impact and recency), without the raw ING
 * card/transfer boilerplate (Kaartnr/Datum/Tijd/Transactie/Term) that clutters the description
 * field and rarely matters for picking a category.
 */
private fun groupSummary(group: UncategorizedGroup): String {
    val amount = if (group.count > 1 && group.minAmount != group.maxAmount) {
        "${group.minAmount.toDisplayString()} – ${group.maxAmount.toDisplayString()} (totaal ${group.totalAmount.toDisplayString()})"
    } else {
        group.totalAmount.toDisplayString()
    }
    val period = if (group.firstDate == group.lastDate) formatShortDate(group.firstDate) else {
        "${formatShortDate(group.firstDate)} – ${formatShortDate(group.lastDate)}"
    }
    val countPrefix = if (group.count > 1) "${group.count}× · " else ""
    return "$countPrefix$amount · $period"
}

private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("nl"))

private fun formatShortDate(date: LocalDate): String =
    date.format(shortDateFormatter).let { formatted ->
        // Force a lowercase month abbreviation regardless of locale data quirks ("4 Sep" -> "4 sep").
        val parts = formatted.split(" ")
        if (parts.size == 2) "${parts[0]} ${parts[1].lowercase()}" else formatted
    }
