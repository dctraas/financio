package com.financio.app.ui.categories

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType

/** A small, fixed swatch grid rather than a full color wheel - picking a category color should stay a two-tap action. */
private val COLOR_SWATCHES = listOf(
    "#5B7A52", "#4C6E77", "#8A4A3D", "#7A6A45", "#6B6485",
    "#4A5A8A", "#3D8A6E", "#9C7A3D", "#3D8FA3", "#A35D82",
)

@Composable
fun CategoryManagementScreen(onBackClick: () -> Unit, viewModel: CategoryManagementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var newCategoryName by remember { mutableStateOf("") }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var rulePendingDelete by remember { mutableStateOf<CategoryRule?>(null) }
    // An id rather than a snapshot of the CategoryRule, so the edit dialog's up/down move
    // buttons re-derive their row (and its now-current priority) from live state on every
    // moveRule() call instead of acting on a stale copy taken when the dialog opened.
    var editingRuleId by remember { mutableStateOf<Long?>(null) }
    var renamingCategory by remember { mutableStateOf<Category?>(null) }
    var pickingColorFor by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(state.undoableAction) {
        val action = state.undoableAction ?: return@LaunchedEffect
        val message = when (action) {
            is UndoableAction.RulesApplied ->
                if (action.changes.size == 1) "1 transactie bijgewerkt." else "${action.changes.size} transacties bijgewerkt."
            is UndoableAction.CategoryDeleted -> "'${action.name}' verwijderd."
        }
        val result = snackbarHostState.showSnackbar(message = message, actionLabel = "Ongedaan maken", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(action) else viewModel.dismissUndoBanner()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorieën & regels") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                FilterChip(
                    selected = state.tab == CategoryManagementTab.RULES,
                    onClick = { viewModel.selectTab(CategoryManagementTab.RULES) },
                    label = { Text("Regels") },
                )
                FilterChip(
                    selected = state.tab == CategoryManagementTab.CATEGORIES,
                    onClick = { viewModel.selectTab(CategoryManagementTab.CATEGORIES) },
                    label = { Text("Categorieën") },
                )
            }

            when (state.tab) {
                CategoryManagementTab.RULES -> RulesTab(
                    state = state,
                    onAddRuleClick = { showAddRuleDialog = true },
                    onRuleClick = { editingRuleId = it.id },
                    onDeleteRuleClick = { rulePendingDelete = it },
                    onApplyRetroactivelyClick = viewModel::requestRuleApplicationPreview,
                )
                CategoryManagementTab.CATEGORIES -> CategoriesTab(
                    state = state,
                    newCategoryName = newCategoryName,
                    onNewCategoryNameChange = { newCategoryName = it },
                    onAddCategory = { viewModel.addCategory(newCategoryName); newCategoryName = "" },
                    onColorClick = { pickingColorFor = it },
                    onRenameClick = { renamingCategory = it },
                    onDeleteClick = viewModel::requestCategoryDelete,
                )
            }
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            categories = state.categories,
            previewRule = viewModel::previewRule,
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { categoryId, matchType, pattern ->
                viewModel.addRuleAndApply(categoryId, matchType, pattern)
                showAddRuleDialog = false
            },
        )
    }

    editingRuleId?.let { ruleId ->
        val index = state.ruleRows.indexOfFirst { it.rule.id == ruleId }
        if (index >= 0) {
            EditRuleDialog(
                rule = state.ruleRows[index].rule,
                categories = state.categories,
                canMoveUp = index > 0,
                canMoveDown = index < state.ruleRows.size - 1,
                onMoveUp = { viewModel.moveRule(ruleId, -1) },
                onMoveDown = { viewModel.moveRule(ruleId, 1) },
                onDismiss = { editingRuleId = null },
                onSave = { categoryId, matchType, pattern ->
                    viewModel.updateRule(ruleId, categoryId, matchType, pattern)
                    editingRuleId = null
                },
            )
        }
    }

    rulePendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { rulePendingDelete = null },
            title = { Text("Regel verwijderen?") },
            text = { Text("'${rule.pattern}' wordt niet meer automatisch gecategoriseerd op basis van deze regel.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRule(rule.id); rulePendingDelete = null }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { rulePendingDelete = null }) { Text("Annuleren") } },
        )
    }

    renamingCategory?.let { category ->
        RenameCategoryDialog(
            currentName = category.name,
            onDismiss = { renamingCategory = null },
            onSave = { newName -> viewModel.renameCategory(category.id, newName); renamingCategory = null },
        )
    }

    pickingColorFor?.let { category ->
        ColorPickerDialog(
            onDismiss = { pickingColorFor = null },
            onPick = { hex -> viewModel.setCategoryColor(category.id, hex); pickingColorFor = null },
        )
    }

    state.ruleApplicationPreview?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRuleApplicationPreview,
            title = { Text("Regels toepassen op bestaande transacties?") },
            text = {
                Text(
                    if (count == 0) {
                        "Geen enkele bestaande transactie zou hierdoor van categorie wisselen — alles staat al zoals je regels het zouden zetten."
                    } else {
                        "$count ${if (count == 1) "transactie wisselt" else "transacties wisselen"} van categorie " +
                            "op basis van je huidige regels. Gesplitste transacties worden overgeslagen."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRuleApplication, enabled = count > 0) { Text("Toepassen") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRuleApplicationPreview) { Text("Annuleren") } },
        )
    }

    state.categoryDeletePreview?.let { preview ->
        DeleteCategoryDialog(
            preview = preview,
            otherCategories = state.categories.filter { it.id != preview.category.id },
            onDismiss = viewModel::cancelCategoryDelete,
            onConfirm = { reassignTo -> viewModel.confirmCategoryDelete(reassignTo) },
        )
    }
}

@Composable
private fun RulesTab(
    state: CategoryManagementUiState,
    onAddRuleClick: () -> Unit,
    onRuleClick: (CategoryRule) -> Unit,
    onDeleteRuleClick: (CategoryRule) -> Unit,
    onApplyRetroactivelyClick: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Priority still decides which rule wins (see RuleMatcher) and drives the up/down move
    // buttons in the edit dialog, but browsing/searching the list is easier A-Z than in whatever
    // order rules happened to be created/reordered in.
    val sortedRows = remember(state.ruleRows) { state.ruleRows.sortedBy { it.rule.pattern.lowercase() } }
    val visibleRows = if (query.isBlank()) {
        sortedRows
    } else {
        sortedRows.filter { row ->
            row.rule.pattern.contains(query, ignoreCase = true) || row.categoryName?.contains(query, ignoreCase = true) == true
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (state.ruleRows.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zoek op patroon of categorie") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
        if (state.ruleRows.isEmpty()) {
            item {
                Text(
                    "Nog geen handmatige regels — die kun je hieronder toevoegen, of ze ontstaan " +
                        "automatisch zodra je een transactie tijdens het importeren categoriseert.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else if (visibleRows.isEmpty()) {
            item {
                Text(
                    "Geen regels gevonden voor \"$query\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(visibleRows, key = { it.rule.id }) { row ->
                RuleCard(
                    row = row,
                    onClick = { onRuleClick(row.rule) },
                    onDelete = { onDeleteRuleClick(row.rule) },
                )
            }
        }
        item {
            TextButton(onClick = onAddRuleClick, enabled = state.categories.isNotEmpty(), modifier = Modifier.padding(vertical = 8.dp)) {
                Text("+ Nieuwe regel")
            }
        }
        item {
            // A rule normally only affects future imports; this is the explicit "and also fix
            // what's already there" action - a new/aangepaste regel toepassen op transacties die al
            // bestonden voordat de regel er was.
            TextButton(
                onClick = onApplyRetroactivelyClick,
                enabled = state.rules.isNotEmpty(),
                modifier = Modifier.padding(bottom = 24.dp),
            ) { Text("Regels met terugwerkende kracht toepassen →") }
        }
    }
}

@Composable
private fun RuleCard(row: RuleRow, onClick: () -> Unit, onDelete: () -> Unit) {
    val warningColor = LocalBudgetStatusColors.current.warning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (row.conflict != null) warningColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.rule.pattern, fontWeight = FontWeight.SemiBold)
                Text(
                    "${matchTypeLabel(row.rule.matchType)} → ${row.categoryName ?: "onbekende categorie"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "raakt ${row.actualMatchCount} ${if (row.actualMatchCount == 1) "transactie" else "transacties"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Verwijder regel voor ${row.rule.pattern}") }
        }
        row.conflict?.let { conflict ->
            Text(
                "botst met regel '${conflict.otherRulePattern}' — ${conflict.overlapCount} " +
                    "${if (conflict.overlapCount == 1) "transactie overlapt" else "transacties overlappen"}",
                style = MaterialTheme.typography.bodySmall,
                color = warningColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EditRuleDialog(
    rule: CategoryRule,
    categories: List<Category>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, matchType: MatchType, pattern: String) -> Unit,
) {
    var selectedCategoryId by remember { mutableStateOf<Long?>(rule.categoryId) }
    var matchType by remember { mutableStateOf(rule.matchType) }
    var pattern by remember { mutableStateOf(rule.pattern) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Regel bewerken") },
        text = {
            Column {
                Text("Categorie", style = MaterialTheme.typography.labelMedium)
                Column {
                    Text(
                        categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Kies categorie ▾",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { categoryMenuOpen = true }.padding(vertical = 8.dp),
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryMenuOpen = false },
                            )
                        }
                    }
                }

                Text("Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    FilterChip(
                        selected = matchType == MatchType.KEYWORD,
                        onClick = { matchType = MatchType.KEYWORD },
                        label = { Text("Trefwoord") },
                    )
                    FilterChip(
                        selected = matchType == MatchType.COUNTERPARTY_EXACT,
                        onClick = { matchType = MatchType.COUNTERPARTY_EXACT },
                        label = { Text("Tegenrekening") },
                    )
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Volgorde", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Bepaalt welke regel wint als meerdere regels dezelfde transactie raken.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Regel omhoog")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Regel omlaag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedCategoryId?.let { onSave(it, matchType, pattern) } },
                enabled = selectedCategoryId != null && pattern.isNotBlank(),
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun CategoriesTab(
    state: CategoryManagementUiState,
    newCategoryName: String,
    onNewCategoryNameChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onColorClick: (Category) -> Unit,
    onRenameClick: (Category) -> Unit,
    onDeleteClick: (Category) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        items(state.categoryRows, key = { it.category.id }) { row ->
            CategoryCard(row = row, onColorClick = { onColorClick(row.category) }, onRenameClick = { onRenameClick(row.category) }, onDeleteClick = { onDeleteClick(row.category) })
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = onNewCategoryNameChange,
                    placeholder = { Text("Nieuwe categorie") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAddCategory, enabled = newCategoryName.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "Categorie toevoegen")
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(row: CategoryRow, onColorClick: () -> Unit, onRenameClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(parseCategoryColor(row.category.colorHex))
                .clickable(onClick = onColorClick),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.category.name, fontWeight = FontWeight.SemiBold)
            Text(
                "${row.spentThisMonth.toDisplayString()} deze maand",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionLink("Hernoemen", onRenameClick)
            ActionLink("Verwijderen", onDeleteClick)
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

private fun parseCategoryColor(colorHex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray)

@Composable
private fun RenameCategoryDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naam wijzigen") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun ColorPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kies een kleur") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COLOR_SWATCHES.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseCategoryColor(hex))
                            .clickable { onPick(hex) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Sluiten") } },
    )
}

@Composable
private fun DeleteCategoryDialog(
    preview: CategoryDeletePreview,
    otherCategories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (reassignTo: Long?) -> Unit,
) {
    var reassignTo by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'${preview.category.name}' verwijderen?") },
        text = {
            Column {
                Text(
                    buildString {
                        val parts = mutableListOf<String>()
                        parts += "${preview.transactionCount} ${if (preview.transactionCount == 1) "transactie" else "transacties"}"
                        if (preview.budgetCount > 0) parts += "${preview.budgetCount} ${if (preview.budgetCount == 1) "budgetlimiet" else "budgetlimieten"}"
                        if (preview.ruleCount > 0) parts += "${preview.ruleCount} ${if (preview.ruleCount == 1) "regel" else "regels"}"
                        append(parts.joinToString(", "))
                        append(" hangen hieraan.")
                    },
                )
                if (preview.transactionCount > 0) {
                    Text(
                        "Verplaats de transacties naar:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Column {
                        Text(
                            otherCategories.firstOrNull { it.id == reassignTo }?.name ?: "Niet categoriseren ▾",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(vertical = 8.dp),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Niet categoriseren") }, onClick = { reassignTo = null; menuOpen = false })
                            otherCategories.forEach { category ->
                                DropdownMenuItem(text = { Text(category.name) }, onClick = { reassignTo = category.id; menuOpen = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reassignTo) }) { Text("Verwijderen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun AddRuleDialog(
    categories: List<Category>,
    previewRule: (MatchType, String, Long?) -> RulePreview?,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: Long, matchType: MatchType, pattern: String) -> Unit,
) {
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var matchType by remember { mutableStateOf(MatchType.KEYWORD) }
    var pattern by remember { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    // Called straight from the composable body on every keystroke, deliberately not routed
    // through a suspend/Flow round-trip - see CategoryManagementViewModel.previewRule.
    val preview = previewRule(matchType, pattern, selectedCategoryId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nieuwe regel") },
        text = {
            Column {
                Text("Categorie", style = MaterialTheme.typography.labelMedium)
                Column {
                    Text(
                        categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Kies categorie ▾",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { categoryMenuOpen = true }.padding(vertical = 8.dp),
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryMenuOpen = false },
                            )
                        }
                    }
                }

                Text("Type", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    FilterChip(
                        selected = matchType == MatchType.KEYWORD,
                        onClick = { matchType = MatchType.KEYWORD },
                        label = { Text("Trefwoord") },
                    )
                    FilterChip(
                        selected = matchType == MatchType.COUNTERPARTY_EXACT,
                        onClick = { matchType = MatchType.COUNTERPARTY_EXACT },
                        label = { Text("Tegenrekening") },
                    )
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    placeholder = {
                        Text(if (matchType == MatchType.KEYWORD) "bijv. Albert Heijn" else "bijv. NL12INGB0001234567")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                preview?.let {
                    Text(
                        formatRulePreview(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedCategoryId?.let { onConfirm(it, matchType, pattern) } },
                enabled = selectedCategoryId != null && pattern.isNotBlank(),
            ) { Text("Regel opslaan en toepassen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

private fun formatRulePreview(preview: RulePreview): String {
    if (preview.matchCount == 0) return "Raakt nu geen enkele transactie."
    return buildString {
        append("Raakt nu ${preview.matchCount} ${if (preview.matchCount == 1) "transactie" else "transacties"}")
        if (preview.topCounterparties.isNotEmpty()) {
            append(": ")
            append(preview.topCounterparties.joinToString(", ") { (name, count) -> "$name ($count×)" })
        }
        append(" — samen ${preview.totalAmount.toDisplayString()}.")
        val nowParts = mutableListOf<String>()
        if (preview.currentlyUncategorizedCount > 0) nowParts += "${preview.currentlyUncategorizedCount}× niet gecategoriseerd"
        if (preview.currentlyOtherCategoryCount > 0) nowParts += "${preview.currentlyOtherCategoryCount}× in een andere categorie"
        if (nowParts.isNotEmpty()) {
            append(" Nu: ")
            append(nowParts.joinToString(", "))
            append(".")
        }
    }
}

private fun matchTypeLabel(matchType: MatchType) = when (matchType) {
    MatchType.COUNTERPARTY_EXACT -> "Tegenrekening"
    MatchType.KEYWORD -> "Trefwoord"
}
