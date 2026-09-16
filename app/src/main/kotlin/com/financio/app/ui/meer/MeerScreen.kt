package com.financio.app.ui.meer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import java.time.LocalDate

/**
 * The "uit de kelder" hub — everything that used to be four taps deep in Instellingen, now one
 * screen away with its own live summary per tile so most of the time a tap isn't even needed.
 */
@Composable
fun MeerScreen(
    onSubscriptionsClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onManageCategoriesClick: () -> Unit,
    onMerchantManagementClick: () -> Unit,
    onNetWorthClick: () -> Unit,
    onYearReviewClick: () -> Unit,
    onImportClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onLockPrivacyClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onMonthStartClick: () -> Unit,
    onBackupExportClick: () -> Unit,
    viewModel: MeerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(
                "Beheer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }

        // Just four tiles, always - a plain 2x2 grid of Rows, not LazyVerticalGrid: nesting a
        // second lazy/scrollable container as an item{} inside this LazyColumn measures it with
        // an infinite height constraint and crashes ("Vertically scrollable component was
        // measured with an infinity maximum height constraints").
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MeerTile(
                    title = "Vaste lasten",
                    summary = if (state.subscriptionCount > 0) {
                        "${state.subscriptionCount} · ${state.subscriptionMonthlyTotal.toDisplayString()}/mnd"
                    } else {
                        "Nog niets herkend"
                    },
                    onClick = onSubscriptionsClick,
                    modifier = Modifier.weight(1f),
                )
                MeerTile(
                    title = "Budget",
                    summary = if (state.budgetCount > 0) {
                        "${state.budgetCount} ${if (state.budgetCount == 1) "limiet ingesteld" else "limieten ingesteld"}"
                    } else {
                        "Nog geen limieten"
                    },
                    onClick = onBudgetsClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MeerTile(
                    title = "Rekeningen",
                    summary = if (state.accountCount > 0) {
                        "${state.accountCount} · ${state.accountsTotalBalance.toDisplayString()}"
                    } else {
                        "Nog geen rekeningen"
                    },
                    onClick = onAccountsClick,
                    modifier = Modifier.weight(1f),
                )
                MeerTile(
                    title = "Categorieën",
                    summary = "${state.categoryCount} categorieën · ${state.ruleCount} regels",
                    onClick = onManageCategoriesClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable(onClick = onImportClick)
                    .padding(16.dp),
            ) {
                Text("Bestand importeren", fontWeight = FontWeight.SemiBold)
                Text(
                    freshnessCaption(state.mostRecentTransactionDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item { SectionHeader("Instellingen") }
        item { MeerRow("Vermogen", onNetWorthClick) }
        item { MeerRow("Jaaroverzicht", onYearReviewClick) }
        item { MeerRow("Tegenpartijen", onMerchantManagementClick) }
        item { MeerRow("Weergave", onAppearanceClick) }
        item { MeerRow("Vergrendeling & privacy", onLockPrivacyClick) }
        item { MeerRow("Meldingen", onNotificationsClick) }
        item { MeerRow("Maand begint op", onMonthStartClick) }
        item { MeerRow("Back-up & export", onBackupExportClick, isLast = true) }

        item {
            Text(
                "Al je gegevens staan versleuteld op dit toestel. Financio heeft geen server, geen " +
                    "account en geen internettoegang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
            )
        }
    }
}

private fun freshnessCaption(mostRecentDate: LocalDate?): String =
    if (mostRecentDate == null) "Nog geen bestand geïmporteerd" else "laatst bijgewerkt t/m ${mostRecentDate.toShortDisplayString()}"

@Composable
private fun MeerTile(title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun MeerRow(label: String, onClick: () -> Unit, isLast: Boolean = false) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(label, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
        if (!isLast) HorizontalDivider()
    }
}
