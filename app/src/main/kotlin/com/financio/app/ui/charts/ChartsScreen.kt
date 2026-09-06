package com.financio.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Account
import com.financio.core.model.Money
import java.time.YearMonth

@Composable
fun ChartsScreen(initialCategoryId: Long? = null, viewModel: ChartsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialCategoryId) {
        initialCategoryId?.let { viewModel.selectCategory(it) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Grafieken") }) }) { padding ->
        if (state.categories.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("Nog geen categorieën", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Zodra transacties gecategoriseerd zijn, verschijnt hier de maand- en jaarvergelijking.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(vertical = 12.dp)) {
                // Category selection has no meaning for Saldoverloop - it's the account's whole
                // balance, not any one category's activity.
                if (state.mode != ChartMode.BALANCE_HISTORY) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.categories, key = { it.id }) { category ->
                            FilterChip(
                                selected = category.id == state.selectedCategoryId,
                                onClick = { viewModel.selectCategory(category.id) },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    ModeSwitch(mode = state.mode, onModeChange = viewModel::selectMode)

                    if (state.mode == ChartMode.BALANCE_HISTORY) {
                        if (state.accounts.size > 1) {
                            AccountPicker(
                                accounts = state.accounts,
                                selectedAccountId = state.selectedAccountId,
                                onSelect = viewModel::selectAccount,
                            )
                        }
                        BalanceHistorySection(state.balancePoints)
                    } else {
                        PeriodNavigator(
                            label = state.referenceLabel,
                            canGoToNextPeriod = state.canGoToNextPeriod,
                            onPrevious = viewModel::goToPreviousPeriod,
                            onNext = viewModel::goToNextPeriod,
                        )

                        Text(
                            state.currentTotal.toDisplayString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        state.deltaLabel?.let { label ->
                            val statusColors = LocalBudgetStatusColors.current
                            Text(
                                (if (state.deltaIsIncrease) "▲ " else "▼ ") + label,
                                color = if (state.deltaIsIncrease) statusColors.over else statusColors.ok,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        BarChart(
                            points = state.points,
                            limit = state.limit,
                            onBarClick = { period -> viewModel.goToPeriod(period) },
                            modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 20.dp),
                        )
                        state.limit?.let {
                            Text(
                                "Gestippelde lijn = budgetlimiet (${it.toDisplayString()})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
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
        item {
            FilterChip(
                selected = mode == ChartMode.BALANCE_HISTORY,
                onClick = { onModeChange(ChartMode.BALANCE_HISTORY) },
                label = { Text("Saldoverloop") },
            )
        }
    }
}

@Composable
private fun AccountPicker(accounts: List<Account>, selectedAccountId: Long?, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        items(accounts, key = { it.id }) { account ->
            FilterChip(
                selected = account.id == selectedAccountId,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
            )
        }
    }
}

@Composable
private fun BalanceHistorySection(points: List<BalancePoint>) {
    if (points.isEmpty()) {
        Text(
            "Nog geen saldogegevens beschikbaar voor de geïmporteerde transacties.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        return
    }
    Text(
        points.last().balance.toDisplayString(),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        "Huidig saldo",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BalanceLineChart(points = points, modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 20.dp))
    Text(
        "Saldo na elke dag met transacties, laatste ${points.size} dagen met activiteit.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun BalanceLineChart(points: List<BalancePoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val zeroLineColor = LocalBudgetStatusColors.current.warning

    val minValue = points.minOf { it.balance.cents }
    val maxValue = points.maxOf { it.balance.cents }.coerceAtLeast(minValue + 1)

    Canvas(modifier) {
        val labelHeight = 20.dp.toPx()
        val chartHeight = size.height - labelHeight
        val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f

        fun yFor(cents: Long): Float {
            val fraction = (cents - minValue).toFloat() / (maxValue - minValue).toFloat()
            return chartHeight - chartHeight * fraction
        }

        // Only meaningful (and drawn) when the range actually straddles zero.
        if (minValue < 0 && maxValue > 0) {
            val zeroY = yFor(0)
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = yFor(point.balance.cents)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 11.sp.toPx()
        }
        labelPaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(
            points.first().date.toShortDisplayString(), 0f, size.height - 4.dp.toPx(), labelPaint,
        )
        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
            points.last().date.toShortDisplayString(), size.width, size.height - 4.dp.toPx(), labelPaint,
        )
    }
}

@Composable
private fun BarChart(points: List<ChartPoint>, limit: Money?, onBarClick: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val statusColors = LocalBudgetStatusColors.current
    val barColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val overColor = statusColors.over
    val currentColor = statusColors.ok

    val maxValue = (points.maxOf { it.amount.cents } .coerceAtLeast(limit?.cents ?: 0L)).coerceAtLeast(1L)

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
        val chartHeight = size.height - labelHeight
        val barWidth = size.width / (points.size * 2f)
        val gap = barWidth

        limit?.let { l ->
            val y = chartHeight - (chartHeight * (l.cents.toFloat() / maxValue.toFloat()))
            drawLine(
                color = labelColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
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
                topLeft = Offset(x, chartHeight - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
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
