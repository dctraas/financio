package com.financio.app.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ImportScreen(onDone: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
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
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            when (val current = state) {
                is ImportUiState.PickFile -> Column {
                    Text(
                        "Kies een CSV- of MT940-export uit Mijn ING.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Bestand kiezen") }
                }

                is ImportUiState.Loading -> Text("Bezig met inlezen…")

                is ImportUiState.Failed -> Text(
                    current.message,
                    color = MaterialTheme.colorScheme.error,
                )

                is ImportUiState.Ready -> Column {
                    Text(current.fileName, fontWeight = FontWeight.Bold)
                    val preview = current.preview
                    Text(
                        "${preview.total} transacties gevonden — ${preview.ready.size} automatisch " +
                            "gecategoriseerd, ${preview.needsCategory.size} te controleren, " +
                            "${preview.duplicateCount} duplicaten overgeslagen.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = viewModel::confirm,
                        enabled = preview.ready.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    ) { Text("${preview.ready.size} transacties importeren") }
                }

                is ImportUiState.Imported -> Text("Geïmporteerd.")
            }
        }
    }
}
