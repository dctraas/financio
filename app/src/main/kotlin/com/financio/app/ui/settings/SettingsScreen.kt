package com.financio.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.data.local.TextSize
import com.financio.app.data.local.ThemeMode
import com.financio.app.ui.theme.LocalFinancioColors

/**
 * Screen 17 "Instellingen" from the redesign handoff — folds what used to be five separate
 * destinations (Weergave, Vergrendeling & privacy, Meldingen, Maand begint op, Back-up & export,
 * reached via `MeerScreen`'s temporary settings sheet) into the three grouped cards the
 * schermontwerp specifies. "Maand begint op" and "Back-up & export" stay their own destinations
 * (they need a grid picker and a whole import/export flow respectively) — this screen just links
 * out to them, same as the mockup's "→" rows.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onMonthStartClick: () -> Unit,
    onBackupExportClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Both notification kinds need the same Android 13+ runtime permission before they can ever
    // actually show anything - see the doc comment on SettingsViewModel.setBudgetThresholdNotificationsEnabled.
    val budgetThresholdPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setBudgetThresholdNotificationsEnabled(granted) }
    val weeklyDigestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setWeeklyDigestEnabled(granted) }

    fun onBudgetThresholdToggle(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            budgetThresholdPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setBudgetThresholdNotificationsEnabled(enabled)
        }
    }

    fun onWeeklyDigestToggle(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            weeklyDigestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setWeeklyDigestEnabled(enabled)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsHeader(onBackClick)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            item { SectionLabel("WEERGAVE", topPadding = 0.dp) }
            item {
                SettingsGroup(
                    rows = listOf(
                        {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                                Text("Thema", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                SegmentedControl(
                                    modifier = Modifier.padding(top = 10.dp),
                                    options = listOf("Systeem" to ThemeMode.SYSTEM, "Licht" to ThemeMode.LIGHT, "Donker" to ThemeMode.DARK),
                                    selected = state.themeMode,
                                    onSelect = viewModel::setThemeMode,
                                )
                                // Not part of the schermontwerp's own pixel spec for this screen, but a
                                // working setting (#42) - kept reachable rather than dropped, right
                                // under the theme it visually belongs next to.
                                Text(
                                    "Tekstgrootte",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 20.dp),
                                )
                                SegmentedControl(
                                    modifier = Modifier.padding(top = 10.dp),
                                    options = listOf("Klein" to TextSize.SMALL, "Standaard" to TextSize.STANDARD, "Groot" to TextSize.LARGE),
                                    selected = state.textSize,
                                    onSelect = viewModel::setTextSize,
                                )
                            }
                        },
                    ),
                )
            }
            item {
                SettingsGroup(
                    rows = listOf(
                        {
                            ToggleRow(
                                title = "Bedragen verbergen",
                                subtitle = "tot je de app ontgrendelt",
                                checked = state.hideAmountsEnabled,
                                onCheckedChange = viewModel::setHideAmountsEnabled,
                            )
                        },
                        {
                            ChevronRow(
                                title = "Maand begint op",
                                trailing = "dag ${state.monthStartDay}",
                                onClick = onMonthStartClick,
                            )
                        },
                    ),
                )
            }

            item { SectionLabel("PRIVACY") }
            item {
                SettingsGroup(
                    rows = listOf(
                        {
                            ToggleRow(
                                title = "Vergrendelen met vingerafdruk",
                                subtitle = "bij openen",
                                checked = state.biometricLockEnabled,
                                onCheckedChange = viewModel::setBiometricLockEnabled,
                            )
                        },
                        { ChevronRow(title = "Back-up & export", trailing = null, onClick = onBackupExportClick) },
                    ),
                )
            }

            item { SectionLabel("MELDINGEN") }
            item {
                SettingsGroup(
                    rows = listOf(
                        {
                            ToggleRow(
                                title = "Budget bijna op",
                                subtitle = "bij 80% van een limiet",
                                checked = state.budgetThresholdNotificationsEnabled,
                                onCheckedChange = ::onBudgetThresholdToggle,
                            )
                        },
                        {
                            ToggleRow(
                                title = "Weekoverzicht",
                                subtitle = "maandagochtend",
                                checked = state.weeklyDigestEnabled,
                                onCheckedChange = ::onWeeklyDigestToggle,
                            )
                        },
                    ),
                )
            }

            item {
                Text(
                    "Al je gegevens staan versleuteld op dit toestel. Financio heeft geen server, geen " +
                        "account en geen internettoegang.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBackClick: () -> Unit) {
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
        Text("Instellingen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: Dp = 8.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = LocalFinancioColors.current.inkFaint,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp),
    )
}

@Composable
private fun SettingsGroup(rows: List<@Composable () -> Unit>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 18.dp))
            row()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChevronRow(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trailing?.let {
                Text(it, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> SegmentedControl(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            val shape = RoundedCornerShape(12.dp)
            Text(
                label,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}
