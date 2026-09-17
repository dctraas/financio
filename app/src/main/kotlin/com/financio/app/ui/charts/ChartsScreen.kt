package com.financio.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.SegmentedBar
import com.financio.app.ui.common.categoryColorFor
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.usecase.MerchantGrouper
import java.time.YearMonth

@Composable
fun ChartsScreen(
    initialCategoryId: Long? = null,
    onGoToSubscriptionsClick: () -> Unit,
    onManageMerchantsClick: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialCategoryId) {
        initialCategoryId?.let { viewModel.selectCategory(it) }
    }

    Scaffold { padding ->
        if (state.categories.isEmpty()) {
            EmptyCharts(padding)
            return@Scaffold
        }

        val selectedCategoryName = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name

        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectedCategoryName == null) {
                Text(
                    "Inzicht",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp),
                )
            } else {
                CategoryHeader(categoryName = selectedCategoryName, onBackClick = viewModel::clearCategorySelection)
            }

            // The "Waarom hoger dan normaal?" card and the counterparty breakdown below the chart
            // are variable-length and, together with the overview's Bespaartip, can easily run
            // past one screen's height - without this the extra content just got clipped instead
            // of being reachable at all.
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                if (selectedCategoryName == null) {
                    PeriodNavigator(
                        label = state.referenceLabel,
                        canGoToNextPeriod = state.canGoToNextPeriod,
                        onPrevious = viewModel::goToPreviousPeriod,
                        onNext = viewModel::goToNextPeriod,
                    )
                    OverviewSection(
                        state = state,
                        onSegmentClick = viewModel::selectCategory,
                        onTipClick = { tip ->
                            if (tip.categoryId != null) viewModel.selectCategory(tip.categoryId) else onGoToSubscriptionsClick()
                        },
                    )
                } else {
                    ModeSwitch(mode = state.mode, onModeChange = viewModel::selectMode)
                    CategoryHero(state)
                    // No visible ‹ › here (unlike the overview) - the schermontwerp's own screen 10
                    // mockup has none, relying entirely on swiping the chart itself (see BarChart).
                    ChartCard(
                        state = state,
                        onBarClick = viewModel::selectPeriod,
                        onSwipePrevious = viewModel::goToPreviousPeriod,
                        onSwipeNext = viewModel::goToNextPeriod,
                    )
                    state.spikeInsight?.let { insight -> SpikeInsightCard(insight) }
                    if (state.counterpartyBreakdown.isNotEmpty()) {
                        CounterpartyBreakdownSection(state.counterpartyBreakdown, selectedCategoryName, onManageMerchantsClick, onOpenDetail)
                    }
                    state.mergeSuggestion?.let { suggestion ->
                        MergeSuggestionCard(
                            suggestion = suggestion,
                            onConfirm = { viewModel.confirmMerchantGroup(suggestion) },
                            onDismiss = { viewModel.dismissMerchantGroup(suggestion) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmptyCharts(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
        Text("Nog geen categorieën", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Zodra transacties gecategoriseerd zijn, verschijnt hier het overzicht.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** "← + stip + categorienaam" — the redesign's plain sub-header convention (see CategorizeHeader/ImportTopBar), replacing the old "Overzicht" filter chip as the way back. */
@Composable
private fun CategoryHeader(categoryName: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Box(Modifier.size(14.dp).clip(CircleShape).background(categoryColorFor(categoryName)))
        Text(categoryName, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A ▲/▼ direction (whether the value actually rose, independent of whether that's good news) plus
 * a signed magnitude, in the color the redesign uses for "good"/"bad" everywhere else.
 */
@Composable
private fun DeltaRow(label: String, isGood: Boolean, isIncrease: Boolean, modifier: Modifier = Modifier) {
    val color = if (isGood) MaterialTheme.colorScheme.primary else LocalBudgetStatusColors.current.over
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (isIncrease) "▲" else "▼", color = color, fontSize = 10.sp)
        Text(
            "${if (isIncrease) "+" else "−"} $label",
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Chevron() {
    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The default view once no category is picked (R5): a segmented bar of this month's categories
 * plus a top-4 list, replacing the earlier donut - a bar reads better at 412dp width and is
 * easier to label (see [com.financio.app.ui.common.SegmentedBar]'s own doc comment).
 */
@Composable
private fun OverviewSection(state: ChartsUiState, onSegmentClick: (Long) -> Unit, onTipClick: (SavingsTip) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text("Uitgegeven deze maand", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            state.currentTotal.toDisplayString(),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        state.deltaLabel?.let { label -> DeltaRow(label, state.deltaIsGood, state.deltaIsIncrease, modifier = Modifier.padding(top = 8.dp)) }
        state.incomeRatioLabel?.let { label ->
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        // Inkomsten is never a "where did my money go" row - the ratio line above already covers
        // income, and this list is specifically about spend.
        val expenseSpends = state.overviewSpends.filterNot { it.category.name.equals("Inkomsten", ignoreCase = true) }
        if (expenseSpends.isEmpty()) {
            Text(
                "Nog niets besteed deze maand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
            return
        }

        SegmentedBar(segments = segmentsFor(expenseSpends), height = 14.dp, modifier = Modifier.padding(top = 20.dp, bottom = 20.dp))

        var showAll by remember(state.overviewSpends) { mutableStateOf(false) }
        val shown = if (showAll) expenseSpends else expenseSpends.take(4)
        shown.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OverviewCategoryRow(entry, onClick = { onSegmentClick(entry.category.id) })
        }
        if (!showAll && expenseSpends.size > 4) {
            Text(
                "Alle ${expenseSpends.size} categorieën",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp).clickable { showAll = true },
            )
        }

        state.savingsTips.firstOrNull()?.let { tip ->
            HighlightCard(
                tip = tip,
                categoryName = state.categories.firstOrNull { it.id == tip.categoryId }?.name,
                onClick = { onTipClick(tip) },
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

/** The segmented bar's "rest" bucket - a fixed neutral gray per the schermontwerp tokens, not theme-dependent (same reasoning as [com.financio.app.ui.theme.CategoryColors]' own hardcoded hexes). */
private val restSegmentColor = Color(0xFFCFCBBF)

/** Top 4 categories get their own segment; everything else folds into one muted "rest" segment. */
private fun segmentsFor(spends: List<CategorySpend>): List<Pair<Color, Float>> {
    val total = spends.sumOf { it.spent.cents }.coerceAtLeast(1)
    val segments = spends.take(4).map { entry -> categoryColorFor(entry.category.name) to entry.spent.cents.toFloat() / total }.toMutableList()
    val restCents = spends.drop(4).sumOf { it.spent.cents }
    if (restCents > 0) segments += restSegmentColor to restCents.toFloat() / total
    return segments
}

@Composable
private fun OverviewCategoryRow(entry: CategorySpend, onClick: () -> Unit) {
    val percentDelta = entry.trailingAverage?.takeIf { it.cents > 0 }?.let { avg -> ((entry.spent.cents - avg.cents) * 100 / avg.cents).toInt() }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColorFor(entry.category.name)))
            Column {
                Text(entry.category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                entry.trailingAverage?.let {
                    Text("gemiddeld ${it.toDisplayString()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.spent.toDisplayString(), fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                percentDelta?.let { pct ->
                    Text(
                        "${if (pct >= 0) "+" else ""}$pct%",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (kotlin.math.abs(pct) > 25) LocalBudgetStatusColors.current.over else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Chevron()
        }
    }
}

/** "Opvallend" - at most one, the first (highest-impact) tip only; the rest wait for their turn next time this recomputes. */
@Composable
private fun HighlightCard(tip: SavingsTip, categoryName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            "OPVALLEND",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
        )
        Text(
            "${tip.title} — ${tip.detail}",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            categoryName?.let { "Bekijk $it" } ?: "Bekijk Vaste lasten",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClick),
        )
    }
}

/** The "waarom is dit hoger dan normaal" one-liner - not part of the schermontwerp mockup itself (it predates this redesign pass), reskinned to the same white-card convention as everything else here rather than dropped. */
@Composable
private fun SpikeInsightCard(insight: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            "Waarom hoger dan normaal?",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
        )
        Text(insight, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * The selected category's spend for the period on screen, broken down by counterparty - "waar
 * komt dit vandaan?", independent of whether it's actually a spike. Tapping a row expands it
 * in place to show the actual transactions behind that counterparty's total, each opening the
 * usual transaction detail screen - no separate screen for this, per the earlier scoping.
 */
@Composable
private fun CounterpartyBreakdownSection(
    breakdown: List<CounterpartySpend>,
    categoryName: String,
    onManageMerchantsClick: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    var expandedNames by remember { mutableStateOf(setOf<String>()) }
    val maxAmount = breakdown.maxOf { it.amount.cents }.coerceAtLeast(1)
    val barColor = categoryColorFor(categoryName)

    Column(Modifier.padding(top = 28.dp)) {
        Text(
            "WAAR GAAT HET HEEN?",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        breakdown.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val isExpanded = entry.counterpartyName in expandedNames
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedNames = if (isExpanded) expandedNames - entry.counterpartyName else expandedNames + entry.counterpartyName
                    }
                    .padding(vertical = 12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.counterpartyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(entry.amount.toDisplayString(), fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                        Text(
                            if (entry.transactions.size == 1) "1 keer" else "${entry.transactions.size} keer",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(fraction = (entry.amount.cents.toFloat() / maxAmount).coerceIn(0.03f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
                if (isExpanded) {
                    Column(Modifier.padding(start = 4.dp, top = 10.dp)) {
                        entry.transactions.forEach { transaction ->
                            CounterpartyTransactionRow(transaction, onClick = { onOpenDetail(transaction.id) })
                        }
                    }
                }
            }
        }
        Text(
            "Tegenpartijen beheren →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onManageMerchantsClick),
        )
    }
}

@Composable
private fun CounterpartyTransactionRow(transaction: Transaction, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            transaction.date.toShortDisplayString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(transaction.amount.toDisplayString(), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * "Deze lijken bij dezelfde tegenpartij te horen" - a suggestion to merge branches of the same
 * chain into one row below (see MerchantGrouper), never applied without this explicit yes/no:
 * a wrong merge would silently blend two unrelated payees' spend into one number, so nothing is
 * folded together until the user confirms it themselves.
 */
@Composable
private fun MergeSuggestionCard(suggestion: MerchantGrouper.MerchantGroupCandidate, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            "${joinNatural(suggestion.rawNames)} lijken dezelfde winkel. Samenvoegen onder ${suggestion.canonicalName}?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) { Text("Samenvoegen", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("Apart houden", fontWeight = FontWeight.Bold) }
        }
    }
}

/** "A en B" for two, "A, B en C" for more - reads better than a plain comma-joined list for a handful of names. */
private fun joinNatural(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " en " + items.last()
}

/**
 * The redesign's "witte balk" navigator - a bordered card instead of the previous plain-text row,
 * only ever shown on the overview: the per-category screen relies entirely on swiping the chart
 * (see [ChartCard]/[BarChart]), matching the schermontwerp's own screen 10 mockup, which has no
 * visible ‹ › there at all.
 */
@Composable
private fun PeriodNavigator(label: String, canGoToNextPeriod: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
            Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(44.dp).let { if (canGoToNextPeriod) it.clickable(onClick = onNext) else it },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "›",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (canGoToNextPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun ModeSwitch(mode: ChartMode, onModeChange: (ChartMode) -> Unit) {
    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModePill("Per maand", selected = mode == ChartMode.MONTH_OVER_MONTH, onClick = { onModeChange(ChartMode.MONTH_OVER_MONTH) })
        ModePill("Per jaar", selected = mode == ChartMode.YEAR_OVER_YEAR, onClick = { onModeChange(ChartMode.YEAR_OVER_YEAR) })
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .let { if (selected) it else it.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp)) }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryHero(state: ChartsUiState) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(state.currentTotal.toDisplayString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        state.deltaLabel?.let { label -> DeltaRow(label, state.deltaIsGood, state.deltaIsIncrease, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** The bar chart in its own white card, with the average/limit legend below a divider - the schermontwerp's "grafiekkaart". */
@Composable
private fun ChartCard(state: ChartsUiState, onBarClick: (YearMonth) -> Unit, onSwipePrevious: () -> Unit, onSwipeNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        BarChart(
            points = state.points,
            limit = state.limit,
            average = state.average,
            onBarClick = onBarClick,
            onSwipePrevious = onSwipePrevious,
            onSwipeNext = onSwipeNext,
            canSwipeNext = state.canGoToNextPeriod,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
        val legend = listOfNotNull(
            state.average?.let { "gemiddelde ${it.toDisplayString()}" },
            state.limit?.let { "limiet ${it.toDisplayString()}" },
        ).joinToString(" · ")
        if (legend.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 14.dp))
            Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(1.5.dp).background(MaterialTheme.colorScheme.outline))
                Text(legend, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun BarChart(
    points: List<ChartPoint>,
    limit: Money?,
    average: Money?,
    onBarClick: (YearMonth) -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    canSwipeNext: Boolean,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val statusColors = LocalBudgetStatusColors.current
    val barColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inkColor = MaterialTheme.colorScheme.onBackground
    val averageLineColor = MaterialTheme.colorScheme.outline
    val overColor = statusColors.over
    val currentColor = statusColors.ok

    val maxValue = (points.maxOf { it.amount.cents }.coerceAtLeast(limit?.cents ?: 0L)).coerceAtLeast(1L)

    // Bars are laid out in equal-width cells (bar + its share of the gap, see barWidth/gap
    // below), so a tap just needs its x-position divided by that cell width to land on a bar
    // index — no need to hit-test each bar's rounded rect individually.
    val tapModifier = Modifier.pointerInput(points) {
        detectTapGestures { offset ->
            val cellWidth = size.width.toFloat() / points.size
            val index = (offset.x / cellWidth).toInt().coerceIn(0, points.lastIndex)
            onBarClick(points[index].period)
        }
    }

    // A left/right swipe shifts the whole window one period, same action as the ‹ › arrows above
    // the chart - kept as its own pointerInput (touch-slop-gated drag detection) rather than
    // merged into the tap detector above: a plain tap never accumulates enough movement to
    // trigger detectHorizontalDragGestures at all, so the two coexist without conflict.
    val swipeModifier = Modifier.pointerInput(canSwipeNext) {
        var draggedPx = 0f
        detectHorizontalDragGestures(
            onDragStart = { draggedPx = 0f },
            onHorizontalDrag = { change, dragAmount -> change.consume(); draggedPx += dragAmount },
            onDragEnd = {
                val threshold = 48.dp.toPx()
                when {
                    draggedPx <= -threshold && canSwipeNext -> onSwipeNext()
                    draggedPx >= threshold -> onSwipePrevious()
                }
            },
        )
    }

    Canvas(modifier.then(tapModifier).then(swipeModifier)) {
        val labelHeight = 28.dp.toPx()
        val valueLabelHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - valueLabelHeight
        val barWidth = size.width / (points.size * 2f)
        val gap = barWidth

        fun yFor(cents: Long): Float = valueLabelHeight + chartHeight - (chartHeight * (cents.toFloat() / maxValue.toFloat()))

        average?.let { avg ->
            drawLine(
                color = averageLineColor,
                start = Offset(0f, yFor(avg.cents)),
                end = Offset(size.width, yFor(avg.cents)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
        limit?.let { l ->
            drawLine(
                color = labelColor,
                start = Offset(0f, yFor(l.cents)),
                end = Offset(size.width, yFor(l.cents)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
        }

        points.forEachIndexed { index, point ->
            val fraction = (point.amount.cents.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            val barHeight = chartHeight * fraction
            val x = gap / 2f + index * (barWidth + gap)
            val isOverLimit = limit != null && point.amount.cents > limit.cents
            val color = when {
                point.isSelected && isOverLimit -> overColor
                point.isSelected -> currentColor
                else -> barColor.copy(alpha = 0.35f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, valueLabelHeight + chartHeight - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )

            // The bar's own value, printed just above it - so you don't have to tap every bar to
            // read its number the way the old chart required. Bold + ink for the selected bar,
            // per the schermontwerp's "geselecteerde 600/ink" spec.
            drawContext.canvas.nativeCanvas.drawText(
                point.amount.toDisplayString(),
                x + barWidth / 2f,
                valueLabelHeight + chartHeight - barHeight - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = (if (point.isSelected) inkColor else labelColor).toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 9.sp.toPx()
                    if (point.isSelected) isFakeBoldText = true
                },
            )
            drawContext.canvas.nativeCanvas.drawText(
                point.label,
                x + barWidth / 2f,
                size.height - 6.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = if (point.isSelected) point.labelPaintColor(isOverLimit, overColor, currentColor).toArgb() else labelColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 11.sp.toPx()
                    if (point.isSelected) isFakeBoldText = true
                },
            )
        }
    }
}

private fun ChartPoint.labelPaintColor(isOverLimit: Boolean, overColor: Color, currentColor: Color): Color =
    if (isOverLimit) overColor else currentColor
