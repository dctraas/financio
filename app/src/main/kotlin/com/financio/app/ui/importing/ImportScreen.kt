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
import com.financio.core.model.Transaction
import java.io.BufferedReader
import java.io.InputStreamReader

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

        if (preview.needsCategory.isNotEmpty()) {
            item {
                Text("Te controleren", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(preview.needsCategory.size) { index ->
                UncategorizedRow(
                    transaction = preview.needsCategory[index],
                    categories = categories,
                    selectedCategoryId = state.manualCategoryChoices[index],
                    onSelect = { categoryId -> viewModel.assignCategory(index, categoryId) },
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
private fun UncategorizedRow(
    transaction: Transaction,
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedCategoryId }?.name

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(transaction.counterpartyName, modifier = Modifier.weight(1f))
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
