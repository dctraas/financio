package com.financio.app.ui.meer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.CategoryColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Money
import java.time.LocalDate

/**
 * The "uit de kelder" hub — everything that used to be four taps deep in Instellingen, now one
 * screen away with its own live summary per row so most of the time a tap isn't even needed.
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
    onDebtsClick: () -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCleanupWizardClick: () -> Unit,
    viewModel: MeerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Text(
                "Beheer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }
        item { ImportCard(state.mostRecentTransactionDate, onImportClick) }

        item { SectionLabel("JOUW INRICHTING") }
        item {
            ListGroup(
                rows = listOf(
                    {
                        InrichtingRow(
                            dotColor = CategoryColors.groceries,
                            title = "Categorieën & regels",
                            summary = "${state.categoryCount} categorieën · ${state.ruleCount} regels",
                            onClick = onManageCategoriesClick,
                        )
                    },
                    {
                        InrichtingRow(
                            dotColor = CategoryColors.transport,
                            title = "Winkels & tegenpartijen",
                            summary = "${state.merchantNameCount} namen · ${state.mergedMerchantCount} samengevoegd",
                            onClick = onMerchantManagementClick,
                        )
                    },
                    {
                        InrichtingRow(
                            dotColor = CategoryColors.subscriptions,
                            title = "Vaste lasten",
                            summary = subscriptionsSummary(state),
                            onClick = onSubscriptionsClick,
                        )
                    },
                    {
                        InrichtingRow(
                            dotColor = CategoryColors.housing,
                            title = "Rekeningen",
                            summary = accountsSummary(state),
                            onClick = onAccountsClick,
                        )
                    },
                ),
            )
        }
        // Not one of the four schermontwerp rows above (the spec explicitly drops the old
        // "Budgetlimieten" row - limits are set on the Budget screen itself), but Budget is a
        // real, already-redesigned screen (#54) that would otherwise have no way in at all once
        // that row is gone - a small link, not a full list row, keeps it reachable.
        item {
            Text(
                "Budget bekijken →",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp).clickable(onClick = onBudgetsClick),
            )
        }

        item { SectionLabel("TERUGBLIK & APP", topPadding = 0.dp) }
        item {
            ListGroup(
                rows = listOf(
                    { TerugblikRow("Vermogen", state.accountsTotalBalance.roundedEuroString(), onNetWorthClick) },
                    { TerugblikRow("Schulden & leningen", debtsSummary(state), onDebtsClick) },
                    { TerugblikRow("Jaaroverzicht ${LocalDate.now().year}", null, onYearReviewClick) },
                    { TerugblikRow("Opschoon-wizard", null, onCleanupWizardClick) },
                    { TerugblikRow("Instellingen", null, onSettingsClick) },
                ),
            )
        }
    }
}

private fun freshnessCaption(mostRecentDate: LocalDate?): String =
    if (mostRecentDate == null) "Nog geen bestand geïmporteerd" else "bijgewerkt t/m ${mostRecentDate.toShortDisplayString()}"

private fun subscriptionsSummary(state: MeerUiState): String =
    if (state.subscriptionCount > 0) {
        "${state.subscriptionCount} · ${state.subscriptionMonthlyTotal.toDisplayString()} per maand"
    } else {
        "Nog niets herkend"
    }

private fun accountsSummary(state: MeerUiState): String =
    if (state.accountCount > 0) "${state.accountCount} · ${state.accountsTotalBalance.toDisplayString()}" else "Nog geen rekeningen"

private fun debtsSummary(state: MeerUiState): String? =
    if (state.openDebtCount > 0) "${state.openDebtCount} openstaand" else null

/** "€8.412" - truncated to whole euros, deliberately distinct from Rekeningen's own exact-to-the-cent total right above it on the same screen. */
private fun Money.roundedEuroString(): String {
    val whole = cents / 100
    val formatted = kotlin.math.abs(whole).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (whole < 0) "-€$formatted" else "€$formatted"
}

@Composable
private fun SectionLabel(text: String, topPadding: Dp = 8.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = LocalFinancioColors.current.inkFaint,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp),
    )
}

@Composable
private fun Chevron() {
    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ImportCard(mostRecentDate: LocalDate?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Bestand importeren", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                freshnessCaption(mostRecentDate),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("→", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun ListGroup(rows: List<@Composable () -> Unit>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 18.dp))
            row()
        }
    }
}

@Composable
private fun InrichtingRow(dotColor: Color, title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Chevron()
    }
}

@Composable
private fun TerugblikRow(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trailing?.let {
                Text(it, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Chevron()
        }
    }
}
