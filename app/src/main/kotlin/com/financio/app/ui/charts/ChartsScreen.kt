package com.financio.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.categoryColorFor
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Money
import com.financio.core.usecase.MerchantGrouper
import java.time.YearMonth

@Composable
fun ChartsScreen(
    initialCategoryId: Long? = null,
    onGoToSubscriptionsClick: () -> Unit,
    onManageMerchantsClick: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialCategoryId) {
        initialCategoryId?.let { viewModel.selectCategory(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inzicht") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (state.categories.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("Nog geen categorieën", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Zodra transacties gecategoriseerd zijn, verschijnt hier het overzicht.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(vertical = 12.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = viewModel::clearCategorySelection,
                            label = { Text("Overzicht") },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == state.selectedCategoryId,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }

                // Scrollable: the "Waarom hoger dan normaal?" card and the counterparty
                // breakdown list below the chart are variable-length and, together with the
                // Bespaartips list on the overview, can easily run past one screen's height -
                // without this the extra content used to just get clipped at the bottom edge
                // instead of being reachable at all.
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    PeriodNavigator(
                        label = state.referenceLabel,
                        canGoToNextPeriod = state.canGoToNextPeriod,
                        onPrevious = viewModel::goToPreviousPeriod,
                        onNext = viewModel::goToNextPeriod,
                    )

                    if (state.selectedCategoryId == null) {
                        OverviewSection(
                            spends = state.overviewSpends,
                            incomeRatioLabel = state.incomeRatioLabel,
                            savingsTips = state.savingsTips,
                            onSegmentClick = viewModel::selectCategory,
                            onTipClick = { tip ->
                                if (tip.categoryId != null) viewModel.selectCategory(tip.categoryId) else onGoToSubscriptionsClick()
                            },
                        )
                    } else {
                        ModeSwitch(mode = state.mode, onModeChange = viewModel::selectMode)

                        Text(
                            state.currentTotal.toDisplayString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        state.deltaLabel?.let { label ->
                            val statusColors = LocalBudgetStatusColors.current
                            Text(
                                (if (state.deltaIsGood) "▲ " else "▼ ") + label,
                                color = if (state.deltaIsGood) statusColors.ok else statusColors.over,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        BarChart(
                            points = state.points,
                            limit = state.limit,
                            average = state.average,
                            onBarClick = { period -> viewModel.selectPeriod(period) },
                            onSwipePrevious = viewModel::goToPreviousPeriod,
                            onSwipeNext = viewModel::goToNextPeriod,
                            canSwipeNext = state.canGoToNextPeriod,
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 20.dp),
                        )
                        Column(Modifier.padding(top = 8.dp)) {
                            state.average?.let {
                                Text(
                                    "Gestippelde grijze lijn = gemiddelde (${it.toDisplayString()})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            state.limit?.let {
                                Text(
                                    "Gestippelde lijn = budgetlimiet (${it.toDisplayString()})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        state.spikeInsight?.let { insight -> SpikeInsightCard(insight) }

                        state.mergeSuggestion?.let { suggestion ->
                            MergeSuggestionCard(
                                suggestion = suggestion,
                                onConfirm = { viewModel.confirmMerchantGroup(suggestion) },
                                onDismiss = { viewModel.dismissMerchantGroup(suggestion) },
                            )
                        }

                        if (state.counterpartyBreakdown.isNotEmpty()) {
                            CounterpartyBreakdownSection(state.counterpartyBreakdown, onManageMerchantsClick)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The default view once no category is picked (R5): a donut of this month's categories plus a
 * top-4 list, replacing what used to be an arbitrarily-first-selected category's trend chart.
 */
@Composable
private fun OverviewSection(
    spends: List<CategorySpend>,
    incomeRatioLabel: String?,
    savingsTips: List<SavingsTip>,
    onSegmentClick: (Long) -> Unit,
    onTipClick: (SavingsTip) -> Unit,
) {
    if (spends.isEmpty()) {
        Text(
            "Nog niets besteed deze maand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        return
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Donut(spends, onSegmentClick, modifier = Modifier.size(140.dp))
        Column(Modifier.padding(start = 20.dp).weight(1f)) {
            spends.take(4).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSegmentClick(entry.category.id) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(entry.category.name, style = MaterialTheme.typography.bodyMedium)
                        // Same signal as a Bespaartip below, but visible at a glance without
                        // reading the tips list - the two are meant to reinforce each other.
                        if (entry.isAnomaly) {
                            Text(
                                "▲ ongewoon hoog",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalBudgetStatusColors.current.over,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text(entry.spent.toDisplayString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    incomeRatioLabel?.let { label ->
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    if (savingsTips.isNotEmpty()) {
        Column(Modifier.padding(top = 24.dp)) {
            Text("Bespaartips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            savingsTips.forEach { tip -> SavingsTipRow(tip, onClick = { onTipClick(tip) }) }
        }
    }
}

/** "Boodschappen is opvallend hoog" etc — a tappable card per tip, same surfaceVariant-card look as MatchingRuleCard on the transaction detail screen. */
@Composable
private fun SavingsTipRow(tip: SavingsTip, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(tip.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            tip.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The "waarom is dit hoger dan normaal" one-liner, shown right under a selected category's chart legend. */
@Composable
private fun SpikeInsightCard(insight: String) {
    val overColor = LocalBudgetStatusColors.current.over
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(overColor.copy(alpha = 0.12f))
            .padding(12.dp),
    ) {
        Text(
            "Waarom hoger dan normaal? $insight",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The selected category's spend for the period on screen, broken down by counterparty - "waar komt dit vandaan?", independent of whether it's actually a spike. */
@Composable
private fun CounterpartyBreakdownSection(breakdown: List<CounterpartySpend>, onManageMerchantsClick: () -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        Text("Waar komt dit vandaan?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        breakdown.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(entry.counterpartyName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        entry.previousAverage?.let { "gemiddeld ${it.toDisplayString()}" } ?: "nieuw",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(entry.amount.toDisplayString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Tegenpartijen beheren →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onManageMerchantsClick),
        )
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
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text("Dit lijkt dezelfde tegenpartij", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${joinNatural(suggestion.rawNames)} → \"${suggestion.canonicalName}\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Ja, samenvoegen",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onConfirm),
            )
            Text(
                "Nee, apart houden",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}

/** "A en B" for two, "A, B en C" for more - reads better than a plain comma-joined list for a handful of names. */
private fun joinNatural(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " en " + items.last()
}

@Composable
private fun Donut(spends: List<CategorySpend>, onSegmentClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val totalCents = spends.sumOf { it.spent.cents }.coerceAtLeast(1)
    // Precomputed once per composition so the tap handler (which runs outside recomposition, in
    // its own coroutine) can look up which wedge a tap angle landed in without recalculating it.
    val sweeps = remember(spends) {
        var startAngle = -90f
        spends.map { entry ->
            val sweep = 360f * entry.spent.cents / totalCents
            val wedge = Triple(entry.category.id, startAngle, startAngle + sweep)
            startAngle += sweep
            wedge
        }
    }
    val strokeWidthDp = 20.dp

    Canvas(
        modifier
            .pointerInput(sweeps) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    var angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                    if (angle < -90f) angle += 360f
                    sweeps.firstOrNull { (_, start, end) -> angle in start..end }?.let { (categoryId, _, _) -> onSegmentClick(categoryId) }
                }
            },
    ) {
        val strokeWidth = strokeWidthDp.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        spends.forEachIndexed { index, entry ->
            val (_, start, end) = sweeps[index]
            drawArc(
                color = categoryColorFor(entry.category.name),
                startAngle = start,
                sweepAngle = end - start,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

/**
 * The bar chart used to always show a fixed trailing window ending "now", with no way to look
 * further back — this lets you shift that whole window, one month/year at a time. Text-based
 * ‹ › affordances, matching the rest of the app's plain-text link style (e.g. "Beheren →" in
 * Instellingen) rather than an icon whose availability in the trimmed icon set isn't guaranteed.
 */
@Composable
private fun PeriodNavigator(label: String, canGoToNextPeriod: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text(
            "‹",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPrevious).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (canGoToNextPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .let { if (canGoToNextPeriod) it.clickable(onClick = onNext) else it }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ModeSwitch(mode: ChartMode, onModeChange: (ChartMode) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = mode == ChartMode.MONTH_OVER_MONTH,
                onClick = { onModeChange(ChartMode.MONTH_OVER_MONTH) },
                label = { Text("Maand-op-maand") },
            )
        }
        item {
            FilterChip(
                selected = mode == ChartMode.YEAR_OVER_YEAR,
                onClick = { onModeChange(ChartMode.YEAR_OVER_YEAR) },
                label = { Text("Jaar-op-jaar") },
            )
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
    val averageLineColor = MaterialTheme.colorScheme.onSurfaceVariant
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
            // read its number the way the old chart required.
            drawContext.canvas.nativeCanvas.drawText(
                point.amount.toDisplayString(),
                x + barWidth / 2f,
                valueLabelHeight + chartHeight - barHeight - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = labelColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 9.sp.toPx()
                },
            )
            drawContext.canvas.nativeCanvas.drawText(
                point.label,
                x + barWidth / 2f,
                size.height - 6.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = if (point.isSelected) point.labelPaintColor(isOverLimit, overColor, currentColor).toArgb()
                    else labelColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 11.sp.toPx()
                },
            )
        }
    }
}

private fun ChartPoint.labelPaintColor(isOverLimit: Boolean, overColor: Color, currentColor: Color): Color =
    if (isOverLimit) overColor else currentColor
