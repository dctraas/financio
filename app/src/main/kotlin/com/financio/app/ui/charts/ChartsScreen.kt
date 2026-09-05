package com.financio.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Money

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

                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    ModeSwitch(mode = state.mode, onModeChange = viewModel::selectMode)

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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == ChartMode.MONTH_OVER_MONTH,
            onClick = { onModeChange(ChartMode.MONTH_OVER_MONTH) },
            label = { Text("Maand-op-maand") },
        )
        FilterChip(
            selected = mode == ChartMode.YEAR_OVER_YEAR,
            onClick = { onModeChange(ChartMode.YEAR_OVER_YEAR) },
            label = { Text("Jaar-op-jaar") },
        )
    }
}

@Composable
private fun BarChart(points: List<ChartPoint>, limit: Money?, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val statusColors = LocalBudgetStatusColors.current
    val barColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val overColor = statusColors.over
    val currentColor = statusColors.ok

    val maxValue = (points.maxOf { it.amount.cents } .coerceAtLeast(limit?.cents ?: 0L)).coerceAtLeast(1L)

    Canvas(modifier) {
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
