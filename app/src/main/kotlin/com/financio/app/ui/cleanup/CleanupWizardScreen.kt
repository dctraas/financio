package com.financio.app.ui.cleanup

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Category

private enum class CleanupStep { UNCATEGORIZED, MERCHANT_SUGGESTIONS, UNUSED_CATEGORIES }

/**
 * A guided pass through cleanup opportunities Financio can already detect on its own - reached
 * from Beheer, one step at a time instead of expecting someone to go hunting for the same signals
 * across Transacties, Tegenpartijen and Categorieën & regels separately. Steps whose count is
 * already zero are skipped entirely; when every count is zero from the start, the wizard never
 * shows a stepper at all.
 */
@Composable
fun CleanupWizardScreen(
    onBackClick: () -> Unit,
    onGoToCategorize: () -> Unit,
    onGoToMerchants: () -> Unit,
    viewModel: CleanupWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var stepIndex by remember { mutableStateOf(0) }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CleanupWizardHeader(onBackClick)

            if (!state.loaded) return@Scaffold

            val steps = buildList {
                if (state.uncategorizedCount > 0) add(CleanupStep.UNCATEGORIZED)
                if (state.merchantSuggestionCount > 0) add(CleanupStep.MERCHANT_SUGGESTIONS)
                if (state.unusedCategories.isNotEmpty()) add(CleanupStep.UNUSED_CATEGORIES)
            }

            if (steps.isEmpty()) {
                AllCleanEmptyState(Modifier.padding(horizontal = 20.dp))
            } else {
                // Acting on a step (deleting the last unused category, say) can shrink `steps`
                // out from under the current index - clamp rather than crash.
                val currentIndex = stepIndex.coerceIn(0, steps.size - 1)
                StepProgressLabel(current = currentIndex + 1, total = steps.size, modifier = Modifier.padding(horizontal = 20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    when (steps[currentIndex]) {
                        CleanupStep.UNCATEGORIZED -> UncategorizedStep(state.uncategorizedCount, onGoToCategorize)
                        CleanupStep.MERCHANT_SUGGESTIONS -> MerchantSuggestionsStep(state.merchantSuggestionCount, onGoToMerchants)
                        CleanupStep.UNUSED_CATEGORIES -> UnusedCategoriesStep(state.unusedCategories, onDelete = viewModel::deleteCategory)
                    }
                }
                val isLast = currentIndex == steps.size - 1
                WizardNavRow(isLast = isLast, onNext = { if (isLast) onBackClick() else stepIndex = currentIndex + 1 })
            }
        }
    }
}

@Composable
private fun CleanupWizardHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text("Opschoon-wizard", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StepProgressLabel(current: Int, total: Int, modifier: Modifier = Modifier) {
    Text(
        "Stap $current van $total",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun UncategorizedStep(count: Int, onGoToCategorize: () -> Unit) {
    Column {
        Text("Niet-gecategoriseerde transacties", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (count == 1) "1 transactie staat nog zonder categorie." else "$count transacties staan nog zonder categorie.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Ga naar categoriseren →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onGoToCategorize),
        )
    }
}

@Composable
private fun MerchantSuggestionsStep(count: Int, onGoToMerchants: () -> Unit) {
    Column {
        Text("Tegenpartij-suggesties", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (count == 1) "1 suggestie wacht nog op een ja of nee." else "$count suggesties wachten nog op een ja of nee.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Ga naar Tegenpartijen →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onGoToMerchants),
        )
    }
}

@Composable
private fun UnusedCategoriesStep(categories: List<Category>, onDelete: (Long) -> Unit) {
    Column {
        Text("Ongebruikte categorieën", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Deze categorieën hebben geen enkele transactie en geen regel - veilig te verwijderen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        categories.forEach { category ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Verwijderen",
                    color = LocalBudgetStatusColors.current.over,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onDelete(category.id) },
                )
            }
        }
    }
}

@Composable
private fun WizardNavRow(isLast: Boolean, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onNext) { Text(if (isLast) "Klaar" else "Volgende") }
    }
}

@Composable
private fun AllCleanEmptyState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 40.dp)) {
        Text("Alles ziet er al netjes uit! ✨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Geen niet-gecategoriseerde transacties, geen openstaande tegenpartij-suggesties en " +
                "geen ongebruikte categorieën gevonden.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
