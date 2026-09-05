package com.financio.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.budget.BudgetStatus
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val monthLabel = state.yearMonth.month.getDisplayName(TextStyle.FULL, Locale("nl")) + " " + state.yearMonth.year

    Scaffold(topBar = { TopAppBar(title = { Text("Budgetten") }) }) { padding ->
        if (state.rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("Nog geen budgetten ingesteld", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Stel per categorie een maandlimiet in — de kaart kleurt rood zodra je eroverheen gaat.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text(
                        monthLabel.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                items(state.rows, key = { it.budget.id }) { row -> BudgetCard(row) }
            }
        }
    }
}

@Composable
private fun BudgetCard(row: BudgetRow) {
    val statusColors = LocalBudgetStatusColors.current
    val statusColor = when (row.status) {
        BudgetStatus.OK -> statusColors.ok
        BudgetStatus.WARNING -> statusColors.warning
        BudgetStatus.OVER -> statusColors.over
    }
    val cardTint = if (row.status == BudgetStatus.OVER) statusColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardTint)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                row.category?.name ?: "Onbekende categorie",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${row.spent.toDisplayString()} / ${row.budget.limit.toDisplayString()}",
                color = if (row.status == BudgetStatus.OVER) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ProgressTrack(percentage = row.percentage, color = statusColor)
        if (row.status == BudgetStatus.OVER) {
            val over = row.spent - row.budget.limit
            Text(
                "${over.toDisplayString()} boven budget",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ProgressTrack(percentage: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val fraction = (percentage.coerceIn(0, 100) / 100f)
        if (fraction > 0f) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color),
            )
        }
    }
}
