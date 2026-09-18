package com.financio.app.ui.categorizequeue

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorizeCard
import com.financio.app.ui.common.CategorizeGameHeader
import com.financio.app.ui.common.CategorizeProgressBar
import kotlinx.coroutines.launch

/**
 * The "spelletje" categorize screen over the already-imported backlog — reached from Vandaag's
 * "Nu doen" tile or Transacties' "Zonder categorie" filter, both of which just mean "there's a
 * queue of counterparty groups to work through"; see [CategorizeQueueViewModel]'s own doc comment
 * for how this differs from the import flow's identically-shaped [com.financio.app.ui.importing.CategorizeScreen].
 */
@Composable
fun CategorizeQueueScreen(onDone: () -> Unit, viewModel: CategorizeQueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        if (!state.loaded) return@Scaffold

        if (state.totalGroups == 0) {
            NothingToCategorize(padding, onDone)
        } else if (state.remainingGroups.isEmpty()) {
            CategorizeQueueDone(state = state, padding = padding, onDone = onDone)
        } else {
            CategorizeQueueBody(state = state, padding = padding, viewModel = viewModel, onExit = onDone)
        }
    }
}

@Composable
private fun CategorizeQueueBody(
    state: CategorizeQueueUiState,
    padding: PaddingValues,
    viewModel: CategorizeQueueViewModel,
    onExit: () -> Unit,
) {
    val current = state.remainingGroups.first()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Session-local streak, same "spelletje" mechanic as the import flow's own card game - see
    // CategorizeScreen.CategorizeContent for why this doesn't need to be persisted.
    var streak by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            CategorizeGameHeader(index = state.doneCount + 1, total = state.totalGroups, onExit = onExit)
            CategorizeProgressBar(doneCount = state.doneCount, total = state.totalGroups, streak = streak)

            AnimatedContent(
                targetState = current,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeOut(tween(220)))
                },
                label = "categorize-queue-card",
            ) { group ->
                val suggestions = remember(group.counterpartyName) { viewModel.suggestCategories(group.counterpartyName) }
                CategorizeCard(
                    group = group,
                    categories = state.categories,
                    suggestions = suggestions,
                    onAssign = { categoryId, learnRule ->
                        streak = if (categoryId == suggestions.firstOrNull()?.categoryId) streak + 1 else 0
                        viewModel.assign(group, categoryId, learnRule)
                        scope.launch {
                            // Dismiss whatever's still showing first - showSnackbar() queues
                            // behind it otherwise, and playing through several groups quickly
                            // (the whole point of this screen) would pile up a backlog of stale
                            // toasts that then plays out for several more seconds after the
                            // player's already moved on, reading as "the popup never goes away".
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = "Categorie gekozen voor ${group.counterpartyName}",
                                actionLabel = "Ongedaan maken",
                            )
                            // Undo here would mean un-persisting an already-saved category change,
                            // not just popping a staged choice like the import flow's own undo -
                            // simplest and safest is to send the player back to Transacties, where
                            // that specific transaction can be corrected the normal way.
                            if (result == SnackbarResult.ActionPerformed) onExit()
                        }
                    },
                    onSkip = {
                        streak = 0
                        viewModel.skip(group)
                    },
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
private fun CategorizeQueueDone(state: CategorizeQueueUiState, padding: PaddingValues, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Text(
            "Klaar voor nu!",
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            buildString {
                append("${state.categorizedCount} ${if (state.categorizedCount == 1) "transactiegroep" else "transactiegroepen"} gecategoriseerd")
                if (state.skippedCount > 0) {
                    append(", ${state.skippedCount} overgeslagen — die vind je terug via \"Zonder categorie\".")
                } else {
                    append(".")
                }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Terug naar Transacties", fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NothingToCategorize(padding: PaddingValues, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            "Niets om te categoriseren",
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Alles staat al in een categorie.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Terug") }
        Spacer(Modifier.height(20.dp))
    }
}
