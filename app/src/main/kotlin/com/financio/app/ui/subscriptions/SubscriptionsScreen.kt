package com.financio.app.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.usecase.DetectedSubscription
import com.financio.core.usecase.SubscriptionCadence
import com.financio.core.usecase.UncertainSubscription
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SubscriptionsScreen(onBackClick: () -> Unit, viewModel: SubscriptionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vaste lasten") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        val nothingDetected = state.dueThisMonth.isEmpty() && state.upcomingLater.isEmpty() &&
            state.uncertain.isEmpty() && state.manuallyConfirmed.isEmpty()
        if (nothingDetected) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("Nog geen vaste lasten herkend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Zodra een tegenpartij minstens twee of drie keer met een vergelijkbaar bedrag " +
                        "rond dezelfde datum afschrijft, verschijnt die hier automatisch — geen " +
                        "bankkoppeling nodig, alleen je eigen transactiehistorie.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        "Deze maand nog: ${state.dueThisMonthTotal.toDisplayString()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${state.dueThisMonth.size} nog te verwachten afschrijvingen deze maand",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(state.dueThisMonth, key = { "due-${it.counterpartyName}" }) { subscription ->
                SubscriptionCard(subscription)
            }

            if (state.upcomingLater.isNotEmpty()) {
                item { SectionHeader("Later") }
                state.upcomingLater
                    .groupBy { YearMonth.from(it.estimatedNextDate) }
                    .forEach { (month, subscriptions) ->
                        item { MonthLabel(month) }
                        items(subscriptions, key = { "later-${it.counterpartyName}" }) { subscription ->
                            SubscriptionCard(subscription)
                        }
                    }
            }

            if (state.uncertain.isNotEmpty()) {
                item { SectionHeader("Twijfelgevallen") }
                items(state.uncertain, key = { "uncertain-${it.counterpartyName}" }) { candidate ->
                    UncertainCard(candidate, onConfirm = { viewModel.confirm(candidate.counterpartyName) }, onDismiss = { viewModel.dismiss(candidate.counterpartyName) })
                }
            }

            if (state.manuallyConfirmed.isNotEmpty()) {
                item { SectionHeader("Handmatig bevestigd") }
                items(state.manuallyConfirmed, key = { "manual-${it.counterpartyName}" }) { candidate ->
                    ManuallyConfirmedRow(candidate)
                }
            }
        }
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
private fun MonthLabel(month: YearMonth) {
    Text(
        (month.month.getDisplayName(TextStyle.FULL, Locale("nl")) + " " + month.year).replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubscriptionCard(subscription: DetectedSubscription) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(subscription.counterpartyName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(subscription.lastAmount.toDisplayString(), fontWeight = FontWeight.SemiBold)
        }
        Text(
            "${subscription.occurrences}× gezien · verwacht rond ${subscription.estimatedNextDate.toShortDisplayString()}" +
                if (subscription.cadence == SubscriptionCadence.YEARLY) " · jaarlijks" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        subscription.priceChange?.let { change ->
            val warningColor = LocalBudgetStatusColors.current.warning
            Text(
                "Ging van ${change.previousAmount.toDisplayString()} naar ${change.newAmount.toDisplayString()}, " +
                    "${change.yearlyDifference(subscription.cadence).toDisplayString()} extra per jaar",
                style = MaterialTheme.typography.bodyMedium,
                color = warningColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * "Twijfelgeval" — a yes/no confirmation instead of either silently listing it as a confirmed
 * subscription (it might not be one) or silently dropping it (it might well be one, just with
 * amounts or timing a bit less regular than [com.financio.core.usecase.SubscriptionDetector]'s
 * strict bar).
 */
@Composable
private fun UncertainCard(candidate: UncertainSubscription, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, LocalBudgetStatusColors.current.warning, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(candidate.counterpartyName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(candidate.lastAmount.toDisplayString(), fontWeight = FontWeight.SemiBold)
        }
        Text(
            "${candidate.occurrences}× gezien · laatst ${candidate.lastDate.toShortDisplayString()} · ${candidate.reason}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Is dit een vaste last?",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = onConfirm) { Text("Ja") }
            TextButton(onClick = onDismiss) { Text("Nee") }
        }
    }
}

@Composable
private fun ManuallyConfirmedRow(candidate: UncertainSubscription) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(candidate.counterpartyName, fontWeight = FontWeight.SemiBold)
            Text(
                "${candidate.occurrences}× gezien · laatst ${candidate.lastDate.toShortDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(candidate.lastAmount.toDisplayString(), fontWeight = FontWeight.SemiBold)
    }
}
