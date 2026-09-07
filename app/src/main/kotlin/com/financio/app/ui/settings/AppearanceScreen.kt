package com.financio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.data.local.ThemeMode

/** Reached from Meer's "Instellingen" section — was Settings' "Weergave" section. */
@Composable
fun AppearanceScreen(onBackClick: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weergave") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Thema",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModeChip("Licht", ThemeMode.LIGHT, state.themeMode, viewModel::setThemeMode)
                ThemeModeChip("Donker", ThemeMode.DARK, state.themeMode, viewModel::setThemeMode)
                ThemeModeChip("Systeem", ThemeMode.SYSTEM, state.themeMode, viewModel::setThemeMode)
            }
        }
    }
}

@Composable
private fun ThemeModeChip(label: String, mode: ThemeMode, selectedMode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    FilterChip(
        selected = selectedMode == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}
