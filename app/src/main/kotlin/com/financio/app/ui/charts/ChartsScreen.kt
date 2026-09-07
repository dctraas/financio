package com.financio.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.time.YearMonth

@Composable
fun ChartsScreen(initialCategoryId: Long? = null, viewModel: ChartsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialCategoryId) {
        initialCategoryId?.let { viewModel.selectCategory(it) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Inzicht") }) }) { padding ->
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

                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    PeriodNavigator(
                        label = state.referenceLabel,
                        canGoToNextPeriod = state.canGoToNextPeriod,
                        onPrevious = viewModel::goToPreviousPeriod,
                        onNext = viewModel::goToNextPeriod,
                    )

                    if (state.selectedCategoryId == null) {
                        OverviewSection(state.overviewSpends, state.incomeRatioLabel, onSegmentClick = viewModel::selectCategory)
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
                            onBarClick = { period -> viewModel.goToPeriod(period) },
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
private fun OverviewSection(spends: List<CategorySpend>, incomeRatioLabel: String?, onSegmentClick: (Long) -> Unit) {
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
                    Text(entry.category.name, style = MaterialTheme.typography.bodyMedium)
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
private fun BarChart(points: List<ChartPoint>, limit: Money?, average: Money?, onBarClick: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
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

    Canvas(modifier.then(tapModifier)) {
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
                point.isCurrent && isOverLimit -> overColor
                point.isCurrent -> currentColor
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
                    this.color = if (point.isCurrent) point.labelPaintColor(isOverLimit, overColor, currentColor).toArgb()
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
