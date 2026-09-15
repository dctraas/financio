package com.financio.app.ui.merchants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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

/**
 * The management screen behind Inzicht's inline "Dit lijkt dezelfde onderneming" prompts (R7):
 * every confirmed onderneming with its members, every automatic suggestion not yet answered, and
 * a way to build a new onderneming by hand from any counterparty name that isn't grouped yet.
 */
@Composable
fun MerchantManagementScreen(onBackClick: () -> Unit, viewModel: MerchantManagementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var groupPendingAddition by remember { mutableStateOf<MerchantGroup?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ondernemingen") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                Text(
                    "Rekeninghouders die bij dezelfde winkelketen of onderneming horen — bijv. \"Albert " +
                        "Heijn 2200 Gorinchem\" en \"Albert Heijn 1359 Gouda\" — kun je hier samenvoegen, " +
                        "zodat Inzicht ze als één rij telt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }

            if (state.suggestedGroups.isNotEmpty()) {
                item { SectionHeader("Suggesties") }
                items(state.suggestedGroups, key = { "suggested-${it.canonicalName}" }) { group ->
                    SuggestionCard(
                        group = group,
                        onConfirm = { viewModel.confirmSuggestion(group) },
                        onDismiss = { viewModel.dismissSuggestion(group) },
                    )
                }
            }

            item { SectionHeader("Ondernemingen") }
            if (state.confirmedGroups.isEmpty()) {
                item {
                    Text(
                        "Nog geen ondernemingen samengesteld.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.confirmedGroups, key = { "confirmed-${it.canonicalName}" }) { group ->
                    ConfirmedGroupCard(
                        group = group,
                        onAddName = { groupPendingAddition = group },
                        onRemoveName = { name -> viewModel.removeNameFromGroup(name) },
                    )
                }
            }

            item {
                TextButton(
                    onClick = { showCreateDialog = true },
                    enabled = state.unassignedNames.isNotEmpty(),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) { Text("+ Nieuwe onderneming samenstellen") }
            }
        }
    }

    groupPendingAddition?.let { group ->
        NamePickerDialog(
            title = "Naam toevoegen aan \"${group.canonicalName}\"",
            candidates = state.unassignedNames,
            onDismiss = { groupPendingAddition = null },
            onPick = { name ->
                viewModel.addNameToGroup(group.canonicalName, name)
                groupPendingAddition = null
            },
        )
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            candidates = state.unassignedNames,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, members ->
                viewModel.createGroup(name, members)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SuggestionCard(group: MerchantGroup, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(group.canonicalName, fontWeight = FontWeight.Bold)
        Text(
            group.memberNames.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text(
                "Ja, samenvoegen",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onConfirm),
            )
            Text(
                "Nee, apart houden",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}

@Composable
private fun ConfirmedGroupCard(group: MerchantGroup, onAddName: () -> Unit, onRemoveName: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(group.canonicalName, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = 8.dp)) {
            group.memberNames.forEachIndexed { index, name ->
                MemberRow(name, onRemove = { onRemoveName(name) })
                if (index != group.memberNames.lastIndex) HorizontalDivider()
            }
        }
        Text(
            "+ Naam toevoegen",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = onAddName),
        )
    }
}

@Composable
private fun MemberRow(name: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Verwijder $name")
        }
    }
}

/** A searchable, tap-to-pick list of counterparty names not part of any onderneming yet — used both to add one name to an existing onderneming and, filtered live, inside [CreateGroupDialog]. */
@Composable
private fun NamePickerDialog(title: String, candidates: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(candidates, query) { candidates.filter { it.contains(query, ignoreCase = true) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zoeken…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Niets gevonden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp).padding(top = 8.dp)) {
                        items(filtered, key = { it }) { name ->
                            Text(
                                name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(name) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** Name field plus a checklist of unassigned counterparty names — building a brand-new onderneming from scratch. */
@Composable
private fun CreateGroupDialog(candidates: List<String>, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(candidates, query) { candidates.filter { it.contains(query, ignoreCase = true) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nieuwe onderneming") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Naam van de onderneming") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zoeken…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp).padding(top = 4.dp)) {
                    items(filtered, key = { it }) { candidateName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (candidateName in selected) selected - candidateName else selected + candidateName
                                },
                        ) {
                            Checkbox(
                                checked = candidateName in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + candidateName else selected - candidateName
                                },
                            )
                            Text(candidateName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    "${selected.size} geselecteerd",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onCreate(name, selected.toList()) },
            ) { Text("Aanmaken") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
