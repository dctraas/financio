package com.financio.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.categorize.CategorySuggestion
import com.financio.core.model.Category
import com.financio.core.usecase.UncategorizedGroup

/**
 * The "one tegenpartij-groep at a time" swipe/suggest/confirm card shared by the import-time
 * categorize flow ([com.financio.app.ui.importing.CategorizeScreen]) and the standalone
 * categorize-queue game over the already-imported backlog
 * ([com.financio.app.ui.categorizequeue.CategorizeQueueScreen]) — both present the exact same
 * question ("waar hoort dit bij?") over the exact same [UncategorizedGroup] shape, so this is the
 * one place that mechanic is built and tested against pixel drift between the two.
 */
@Composable
internal fun CategorizeGameHeader(index: Int, total: Int, onExit: () -> Unit) {
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

/**
 * [streak] is how many suggested-category confirmations in a row the player just got right - a
 * skip or a manual-alternative pick resets it to 0 (see the two screens' own onAssign/onSkip
 * wiring). Shown as a small badge that pops in once it's actually worth bragging about (2+),
 * rather than at 1 where it would just read as noise on every single first pick.
 */
@Composable
internal fun CategorizeProgressBar(doneCount: Int, total: Int, streak: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        AnimatedVisibility(visible = streak >= 2, enter = fadeIn() + scaleIn(initialScale = 0.7f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "$streak op rij",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
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
}

/**
 * [onSplitByKeyword] null hides "Splitsen op trefwoord" entirely - the categorize-queue game
 * leaves this out for now (a rare need for an already-persisted backlog, still reachable via that
 * transaction's own detail screen), while the import flow keeps it.
 */
@Composable
internal fun CategorizeCard(
    group: UncategorizedGroup,
    categories: List<Category>,
    suggestions: List<CategorySuggestion>,
    onAssign: (categoryId: Long, learnRule: Boolean) -> Unit,
    onSkip: () -> Unit,
    onSplitByKeyword: ((keyword: String, categoryId: Long) -> Unit)? = null,
) {
    var expanded by remember(group.counterpartyName) { mutableStateOf(false) }
    var showAllCategories by remember(group.counterpartyName) { mutableStateOf(false) }
    var splitting by remember(group.counterpartyName, group.count) { mutableStateOf(false) }
    var keyword by remember(group.counterpartyName, group.count) { mutableStateOf("") }
    var rememberRule by remember(group.counterpartyName) { mutableStateOf(true) }

    // Suggested categories first (best signal first - see CategorySuggester), then every other
    // category in its own list order - so there's always a suggestion plus up to 4 alternatives
    // even for a fresh install with no categorized history yet.
    val orderedCategoryIds = remember(categories, suggestions) {
        val ranked = suggestions.map { it.categoryId }
        ranked + categories.map { it.id }.filterNot { it in ranked }
    }
    val suggestedCategory = categories.firstOrNull { it.id == orderedCategoryIds.firstOrNull() }
    val alternativeCategories = orderedCategoryIds.drop(1).take(4).mapNotNull { id -> categories.firstOrNull { it.id == id } }
    val topSuggestion = suggestions.firstOrNull()

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
                    if (onSplitByKeyword != null && group.count > 1) {
                        Text(
                            "Splitsen op trefwoord",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { splitting = true },
                        )
                    }
                }
            } else if (onSplitByKeyword != null) {
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 20.dp)) {
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
                            if (topSuggestion != null && topSuggestion.matchingTransactionCount > 0) {
                                Text(
                                    if (topSuggestion.isSimilarityBased) "${topSuggestion.matchingTransactionCount}× vergelijkbaar" else "${topSuggestion.matchingTransactionCount}× eerder",
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

/** "Splitsen op trefwoord" - the same shape as the pre-redesign inline split UI. */
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
