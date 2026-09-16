package com.financio.app.ui.importing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financio.app.ui.common.categoryColorFor
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Category
import com.financio.core.usecase.UncategorizedGroup
import kotlinx.coroutines.launch

/**
 * Screen 03 "Categoriseren" from the September 2026 layout-redesign handoff — one tegenpartij-
 * groep at a time, the suggested category as the largest button in the thumb zone. Lives inside
 * the same [ImportViewModel]/[ImportScreen] as every other import-flow state rather than as its
 * own nav-graph route: the flow's data (remaining groups, choices, undo history) already lives in
 * [ImportUiState.Ready], and a genuinely separate route would need to share that same ViewModel
 * instance across a back-stack boundary for no real benefit.
 */
@Composable
fun CategorizeContent(
    state: ImportUiState.Ready,
    categories: List<Category>,
    padding: PaddingValues,
    viewModel: ImportViewModel,
    onExit: () -> Unit,
) {
    val groups = state.preview.needsCategoryGrouped
    val remainingGroups = computeRemainingGroups(groups, state.manualCategoryChoices, state.skippedGroups)
    val totalGroups = groups.size
    val doneCount = totalGroups - remainingGroups.size

    // The last group was just resolved (assigned or skipped) - finish the import exactly like the
    // "N transacties importeren" button would, then let ImportScreen's own Imported effect exit.
    LaunchedEffect(remainingGroups.isEmpty()) {
        if (remainingGroups.isEmpty()) viewModel.confirm()
    }
    if (remainingGroups.isEmpty()) return

    val current = remainingGroups.first()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun assignAndNotify(categoryId: Long, learnRule: Boolean) {
        viewModel.assignCategory(current.counterpartyName, categoryId, learnRule)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Categorie gekozen voor ${current.counterpartyName}",
                actionLabel = "Ongedaan maken",
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    fun skipAndNotify() {
        viewModel.skip(current.counterpartyName)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${current.counterpartyName} overgeslagen",
                actionLabel = "Ongedaan maken",
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            CategorizeHeader(index = doneCount + 1, total = totalGroups, onExit = onExit)
            CategorizeProgressBar(doneCount = doneCount, total = totalGroups)

            AnimatedContent(
                targetState = current,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeOut(tween(220)))
                },
                label = "categorize-card",
            ) { group ->
                CategorizeBody(
                    group = group,
                    categories = categories,
                    categoryUsageFrequency = state.categoryUsageFrequency,
                    onAssign = ::assignAndNotify,
                    onSkip = ::skipAndNotify,
                    onSplitByKeyword = { keyword, categoryId -> viewModel.assignCategoryToKeyword(group.counterpartyName, keyword, categoryId) },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
private fun CategorizeHeader(index: Int, total: Int, onExit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text(
            "groep $index van $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Pauze",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onExit).padding(12.dp),
        )
    }
}

@Composable
private fun CategorizeProgressBar(doneCount: Int, total: Int) {
    val progress by animateFloatAsState(
        targetValue = if (total <= 0) 0f else doneCount.toFloat() / total,
        animationSpec = tween(300),
        label = "categorize-progress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CategorizeBody(
    group: UncategorizedGroup,
    categories: List<Category>,
    categoryUsageFrequency: Map<Long, Int>,
    onAssign: (categoryId: Long, learnRule: Boolean) -> Unit,
    onSkip: () -> Unit,
    onSplitByKeyword: (keyword: String, categoryId: Long) -> Unit,
) {
    var expanded by remember(group.counterpartyName) { mutableStateOf(false) }
    var showAllCategories by remember(group.counterpartyName) { mutableStateOf(false) }
    var splitting by remember(group.counterpartyName, group.count) { mutableStateOf(false) }
    var keyword by remember(group.counterpartyName, group.count) { mutableStateOf("") }
    var rememberRule by remember(group.counterpartyName) { mutableStateOf(true) }

    // Highest categoryUsageFrequency first (the "hoogste onder de al bekende regels" heuristic),
    // then every other category in its own list order - so there's always a suggestion plus up
    // to 4 alternatives even for a fresh install with no usage history yet.
    val orderedCategoryIds = remember(categories, categoryUsageFrequency) {
        val usageRanked = categoryUsageFrequency.entries.sortedByDescending { it.value }.map { it.key }
        usageRanked + categories.map { it.id }.filterNot { it in usageRanked }
    }
    val suggestedCategory = categories.firstOrNull { it.id == orderedCategoryIds.firstOrNull() }
    val alternativeCategories = orderedCategoryIds.drop(1).take(4).mapNotNull { id -> categories.firstOrNull { it.id == id } }
    val suggestedCount = suggestedCategory?.let { categoryUsageFrequency[it.id] } ?: 0

    // "Swipe naar links = overslaan" - a plain local var, not remembered Compose state: it's only
    // ever read/written from within this same pointerInput gesture-detection coroutine, same
    // pattern as ChartsScreen's own BarChart swipe modifier.
    val swipeModifier = Modifier.pointerInput(group.counterpartyName) {
        var draggedPx = 0f
        detectHorizontalDragGestures(
            onDragStart = { draggedPx = 0f },
            onHorizontalDrag = { change, dragAmount -> change.consume(); draggedPx += dragAmount },
            onDragEnd = { if (draggedPx <= -100.dp.toPx()) onSkip() },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(swipeModifier)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .padding(22.dp),
            ) {
                Text(
                    "WAAR HOORT DIT BIJ?",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = LocalFinancioColors.current.inkFaint,
                )
                Text(
                    group.counterpartyName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "${group.count} transacties · ${group.totalAmount.toDisplayString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )

                val shown = if (expanded) group.transactions else group.transactions.take(3)
                shown.forEachIndexed { index, transaction ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${transaction.date.toShortDisplayString()} · ${transaction.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Text(transaction.amount.toDisplayString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!expanded && group.count > 3) {
                    Text(
                        "Alle ${group.count} bekijken",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { expanded = true }.padding(top = 8.dp),
                    )
                }
            }

            if (!splitting) {
                Text(
                    "OF KIES EEN ANDERE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = LocalFinancioColors.current.inkFaint,
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                )
                val displayedAlternatives = if (showAllCategories) categories.filterNot { it.id == suggestedCategory?.id } else alternativeCategories
                displayedAlternatives.chunked(2).forEach { rowCategories ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCategories.forEach { category ->
                            CategoryGridButton(category, modifier = Modifier.weight(1f), onClick = { onAssign(category.id, true) })
                        }
                        if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Row(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    if (!showAllCategories && categories.size > alternativeCategories.size + 1) {
                        Text(
                            "Alle categorieën",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showAllCategories = true }.padding(end = 20.dp),
                        )
                    }
                    // Only worth offering once there's more than one transaction to actually split -
                    // splitting a single-transaction group would just be a roundabout way to
                    // categorize it, not a real "meerdere soorten transacties" situation.
                    if (group.count > 1) {
                        Text(
                            "Splitsen op trefwoord",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { splitting = true },
                        )
                    }
                }
            } else {
                CategorizeKeywordSplit(
                    group = group,
                    categories = categories,
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    onPick = { categoryId -> onSplitByKeyword(keyword, categoryId) },
                    onBack = { splitting = false },
                )
            }
        }

        if (!splitting) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, top = 14.dp, bottom = 20.dp)) {
                if (suggestedCategory != null) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Onthoud ${group.counterpartyName} → ${suggestedCategory.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Switch(checked = rememberRule, onCheckedChange = { rememberRule = it })
                    }
                    Button(
                        onClick = { onAssign(suggestedCategory.id, rememberRule) },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColorFor(suggestedCategory.name)))
                            Text(
                                suggestedCategory.name,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                            if (suggestedCount > 0) {
                                Text(
                                    "${suggestedCount}× eerder",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    "Overslaan",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onSkip).padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryGridButton(category: Category, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColorFor(category.name)))
        Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
    }
}

/** "Splitsen op trefwoord" - the same shape as the pre-redesign inline split UI, ported as-is into the new full-screen flow. */
@Composable
private fun CategorizeKeywordSplit(
    group: UncategorizedGroup,
    categories: List<Category>,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onPick: (categoryId: Long) -> Unit,
    onBack: () -> Unit,
) {
    val matchCount = if (keyword.isBlank()) 0 else group.transactions.count { "${it.counterpartyName} ${it.description}".contains(keyword, ignoreCase = true) }

    Column(Modifier.padding(top = 20.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text("Woord in de omschrijving") },
            placeholder = { Text("bijv. motorrijtuigenbelasting") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        if (keyword.isNotBlank()) {
            Text(
                "Raakt nu $matchCount van de ${group.count} transacties.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "Kies een categorie voor deze transacties:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )
        categories.chunked(2).forEach { rowCategories ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCategories.forEach { category ->
                    CategoryGridButton(
                        category,
                        modifier = Modifier.weight(1f),
                        onClick = { if (keyword.isNotBlank()) onPick(category.id) },
                    )
                }
                if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "Terug",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onBack).padding(top = 8.dp),
        )
    }
}
