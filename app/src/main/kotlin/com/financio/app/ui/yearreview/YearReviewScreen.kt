package com.financio.app.ui.yearreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors

/** A once-a-year summary of the whole transaction history - what you spent, on what, and where, at a glance. */
@Composable
fun YearReviewScreen(onBackClick: () -> Unit, viewModel: YearReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jaaroverzicht") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            YearNavigator(
                year = state.year,
                canGoToNextYear = state.canGoToNextYear,
                onPrevious = viewModel::goToPreviousYear,
                onNext = viewModel::goToNextYear,
            )

            if (!state.hasData) {
                Column(Modifier.padding(top = 32.dp)) {
                    Text("Niets te zien voor ${state.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Zodra er transacties uit dit jaar zijn geïmporteerd, verschijnt hier het overzicht.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                return@Scaffold
            }

            Text(
                "Uitgegeven in ${state.year}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(state.totalSpent.toDisplayString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            state.previousYearComparisonLabel?.let { label ->
                val statusColors = LocalBudgetStatusColors.current
                Text(
                    (if (state.previousYearComparisonIsGood) "▼ " else "▲ ") + label,
                    color = if (state.previousYearComparisonIsGood) statusColors.ok else statusColors.over,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Inkomsten", state.totalIncome.toDisplayString())
                MiniStat("Netto", state.netSaved.toDisplayString())
                MiniStat("Transacties", state.transactionCount.toString())
            }

            Column(Modifier.padding(top = 24.dp)) {
                state.topCategory?.let { stat ->
                    StatCard("Meest uitgegeven aan", stat.name, stat.amount.toDisplayString())
                }
                state.topCounterparty?.let { stat ->
                    StatCard("Vaakst besteed bij", stat.name, "${stat.amount.toDisplayString()} · ${stat.occurrences}×")
                }
                state.busiestMonth?.let { stat ->
                    StatCard("Duurste maand", stat.label, stat.amount.toDisplayString())
                }
            }
        }
    }
}

@Composable
private fun YearNavigator(year: Int, canGoToNextYear: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "‹",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPrevious).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(year.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (canGoToNextYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .let { if (canGoToNextYear) it.clickable(onClick = onNext) else it }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StatCard(label: String, title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}
