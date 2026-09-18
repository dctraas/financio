package com.financio.app.ui.importing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financio.app.ui.common.CategorizeCard
import com.financio.app.ui.common.CategorizeGameHeader
import com.financio.app.ui.common.CategorizeProgressBar
import com.financio.app.ui.common.SegmentedBar
import com.financio.app.ui.common.categoryColorFor
import com.financio.core.model.Category
import com.financio.core.model.Money
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
    onConfirmGoToInsights: () -> Unit,
    onConfirmGoToday: () -> Unit,
) {
    val groups = state.preview.needsCategoryGrouped
    val remainingGroups = computeRemainingGroups(groups, state.manualCategoryChoices, state.skippedGroups)
    val totalGroups = groups.size
    val doneCount = totalGroups - remainingGroups.size

    // Every group resolved (assigned or skipped) - screen 04 "Klaar" (not yet imported: confirm()
    // only runs once the user taps one of its two buttons, per ImportScreen's own Imported effect
    // which is what actually decides where to navigate next).
    if (remainingGroups.isEmpty()) {
        CategorizeDoneContent(
            state = state,
            categories = categories,
            padding = padding,
            onGoToInsights = onConfirmGoToInsights,
            onBackToToday = onConfirmGoToday,
        )
        return
    }

    val current = remainingGroups.first()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Session-local "spelletje" streak - how many suggested-category confirmations in a row, reset
    // by a skip or a manual-alternative pick (see the onAssign/onSkip wiring below). Not persisted:
    // it resets naturally whenever this composable leaves composition (categorizing=false in
    // ImportScreen), which is exactly "per play session", not "per app install".
    var streak by remember { mutableStateOf(0) }

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
        streak = 0
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
            CategorizeGameHeader(index = doneCount + 1, total = totalGroups, onExit = onExit)
            CategorizeProgressBar(doneCount = doneCount, total = totalGroups, streak = streak)

            AnimatedContent(
                targetState = current,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeOut(tween(220)))
                },
                label = "categorize-card",
            ) { group ->
                val suggestions = remember(group.counterpartyName) { viewModel.suggestCategories(group.counterpartyName) }
                CategorizeCard(
                    group = group,
                    categories = categories,
                    suggestions = suggestions,
                    onAssign = { categoryId, learnRule ->
                        streak = if (categoryId == suggestions.firstOrNull()?.categoryId) streak + 1 else 0
                        assignAndNotify(categoryId, learnRule)
                    },
                    onSkip = ::skipAndNotify,
                    onSplitByKeyword = { keyword, categoryId -> viewModel.assignCategoryToKeyword(group.counterpartyName, keyword, categoryId) },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

/**
 * Screen 04 "Categoriseren — klaar" from the redesign handoff — every group from this import is
 * now either assigned or skipped, but nothing is persisted yet (see [CategorizeContent]): that
 * only happens once [onGoToInsights] or [onBackToToday] actually runs, so a user who backs out
 * with the system back button before tapping either loses nothing and can resume where they left
 * off. The category breakdown previews this import's own transactions, not "this month" broadly -
 * deliberately simpler than pulling in ChartsViewModel's month-boundary logic for one summary card,
 * and arguably more relevant right after an import than an unrelated calendar-month figure would be.
 */
@Composable
private fun CategorizeDoneContent(
    state: ImportUiState.Ready,
    categories: List<Category>,
    padding: PaddingValues,
    onGoToInsights: () -> Unit,
    onBackToToday: () -> Unit,
) {
    val toImport = remember(state) {
        state.preview.ready + state.preview.needsCategory.map { transaction ->
            state.manualCategoryChoices.firstOrNull { it.matches(transaction) }
                ?.let { choice -> transaction.copy(categoryId = choice.categoryId) }
                ?: transaction
        }
    }
    val rulesLearnedCount = state.manualCategoryChoices.count { it.learnRule }
    val totalAmount = Money(toImport.sumOf { kotlin.math.abs(it.amount.cents) })

    val breakdown = remember(toImport) {
        toImport
            .mapNotNull { transaction -> transaction.categoryId?.let { it to transaction } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, transactions) -> transactions.sumOf { kotlin.math.abs(it.amount.cents) } }
            .entries
            .sortedByDescending { it.value }
    }
    val totalBreakdownCents = breakdown.sumOf { it.value }.coerceAtLeast(1)
    val topBreakdown = breakdown.take(4)
    val restCents = totalBreakdownCents - topBreakdown.sumOf { it.value }
    val categoriesById = categories.associateBy { it.id }
    val barSegments = topBreakdown.map { (categoryId, cents) ->
        categoryColorFor(categoriesById[categoryId]?.name) to cents.toFloat() / totalBreakdownCents
    } + if (restCents > 0) listOf(Color(0xFFCFCBBF) to restCents.toFloat() / totalBreakdownCents) else emptyList()

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Text(
            "Alles gecategoriseerd",
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            buildString {
                append("${toImport.size} transacties staan erin.")
                if (rulesLearnedCount > 0) {
                    append(" Je hebt $rulesLearnedCount nieuwe regel${if (rulesLearnedCount == 1) "" else "s"} geleerd")
                    append(" — die ${if (rulesLearnedCount == 1) "groep hoef" else "groepen hoef"} je nooit meer met de hand te doen.")
                }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (barSegments.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Deze import", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(totalAmount.toDisplayString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                SegmentedBar(segments = barSegments, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp), animate = true)
                topBreakdown.chunked(2).forEach { rowEntries ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowEntries.forEach { (categoryId, cents) ->
                            val name = categoriesById[categoryId]?.name ?: "Overig"
                            val percent = (cents * 100 / totalBreakdownCents).toInt()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(categoryColorFor(name)))
                                Text("$name $percent%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGoToInsights,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Naar Inzicht", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onBackToToday, modifier = Modifier.fillMaxWidth()) { Text("Terug naar Vandaag") }

        Spacer(Modifier.height(20.dp))
    }
}

