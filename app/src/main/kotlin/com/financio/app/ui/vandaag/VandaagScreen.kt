package com.financio.app.ui.vandaag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.common.toSignedMagnitudeString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Transaction
import com.financio.core.usecase.BalanceForecastCalculator
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun VandaagScreen(
    onSeeAllClick: () -> Unit,
    onCategorizeClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    viewModel: VandaagViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    if (!state.loaded) return

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item { Header(state.mostRecentTransactionDate, state.dataIsStale, onImportClick) }
        item {
            if (state.safeToSpend != null) {
                SafeToSpendHero(state.safeToSpend!!, showCentsEnabled = state.showCentsEnabled)
            } else {
                MissingBalanceNotice(onImportClick)
            }
        }
        if (state.uncategorizedCount > 0) {
            item { TaskCard(state.uncategorizedCount, state.uncategorizedGroupCount, onCategorizeClick) }
        }
        state.forecast?.let { forecast -> item { ForecastCard(forecast) } }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DEZE WEEK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = LocalFinancioColors.current.inkFaint,
                )
                Text(
                    "Alles",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onSeeAllClick),
                )
            }
        }
        if (state.thisWeekTransactions.isEmpty()) {
            item {
                Text(
                    "Nog geen transacties deze week.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        } else {
            items(state.thisWeekTransactions, key = { it.id }) { transaction ->
                WeekRow(transaction, state.categoriesById[transaction.categoryId]?.name, onClick = { onOpenDetail(transaction.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Header(mostRecentDate: LocalDate?, isStale: Boolean, onImportClick: () -> Unit) {
    val today = LocalDate.now()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("nl")).replaceFirstChar { it.uppercase() } +
                    " " + today.toShortDisplayString(),
                style = MaterialTheme.typography.titleLarge,
            )
            // A stale import becomes a tappable nudge instead of a caption nobody reads - the biggest
            // risk in an import-only app is not noticing you're looking at old data.
            if (mostRecentDate != null) {
                Text(
                    if (isStale) "Data loopt achter — importeer een nieuw bestand" else "bijgewerkt t/m ${mostRecentDate.toShortDisplayString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isStale) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isStale) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .let { if (isStale) it.clickable(onClick = onImportClick) else it },
                )
            }
        }
        ImportButton(onClick = onImportClick)
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Importeren", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SafeToSpendHero(result: com.financio.core.usecase.SafeToSpendCalculator.Result, showCentsEnabled: Boolean) {
    val nextMonthFirst = LocalDate.now().plusMonths(1).withDayOfMonth(1)
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(
            "Vrij te besteden tot ${nextMonthFirst.dayOfMonth} ${nextMonthFirst.month.getDisplayName(TextStyle.FULL, Locale("nl"))}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            result.safeToSpendTotal.toDisplayString(showCentsEnabled),
            fontSize = 44.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "${result.safeToSpendPerDay.toDisplayString(showCentsEnabled)} per dag · ${result.daysRemaining} dagen te gaan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MissingBalanceNotice(onImportClick: () -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(
            "Vrij te besteden kan nog niet berekend worden",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Saldo van je betaalrekening ontbreekt — importeer een export met een saldokolom.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Text(
            "Importeren →",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onImportClick),
        )
    }
}

/** Only shown while there's actually something to do - the redesign's "één taak per scherm" rule dropped the second "vaste lasten" tile that used to sit next to this. */
@Composable
private fun TaskCard(uncategorizedCount: Int, groupCount: Int, onClick: () -> Unit) {
    val minutes = ((groupCount * 7 + 59) / 60).coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            uncategorizedCount.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                "transacties zonder categorie",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "$groupCount groep${if (groupCount == 1) "" else "en"} · ± $minutes minu${if (minutes == 1) "uut" else "ten"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ForecastCard(forecast: BalanceForecastCalculator.Result) {
    val endOfMonthLabel = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale("nl"))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Verwacht eind $endOfMonthLabel", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(forecast.points.last().balance.toDisplayString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        ForecastChart(forecast, modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 16.dp))
        forecast.tightDate?.let { tightDate ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 14.dp))
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LocalBudgetStatusColors.current.warning),
                )
                Text(
                    "Wordt krap rond ${tightDate.toShortDisplayString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Buckets [BalanceForecastCalculator.Result.points] down to at most [maxBars] bars for the
 * 96dp-tall chart. The real design shows a few realized days before "vandaag" too, but the
 * calculator only ever projects forward from today - there's no historical-balance series to
 * draw bars from, so this shows "vandaag" as the first bar rather than fabricating history.
 */
private fun sampledForecastBars(points: List<BalanceForecastCalculator.ForecastPoint>, maxBars: Int): List<BalanceForecastCalculator.ForecastPoint> {
    if (points.size <= maxBars) return points
    val today = points.first()
    val rest = points.drop(1)
    val bucketCount = maxBars - 1
    val step = rest.size.toDouble() / bucketCount
    val sampled = (1..bucketCount).map { i -> rest[((i * step).toInt() - 1).coerceIn(0, rest.size - 1)] }
    return listOf(today) + sampled
}

@Composable
private fun ForecastChart(forecast: BalanceForecastCalculator.Result, modifier: Modifier = Modifier) {
    val bars = sampledForecastBars(forecast.points, maxBars = 8)
    if (bars.size < 2) return
    val realizedColor = MaterialTheme.colorScheme.primary
    val projectedColor = MaterialTheme.colorScheme.outlineVariant
    val dashedColor = LocalBudgetStatusColors.current.warning
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minValue = bars.minOf { it.balance.cents }.coerceAtMost(0L)
    val maxValue = bars.maxOf { it.balance.cents }.coerceAtLeast(minValue + 1)

    Canvas(modifier) {
        val labelHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        bars.forEachIndexed { index, point ->
            val fraction = ((point.balance.cents - minValue).toFloat() / (maxValue - minValue).toFloat()).coerceIn(0.04f, 1f)
            val barHeight = chartHeight * fraction
            val left = index * (barWidth + gap)
            val top = chartHeight - barHeight

            drawRoundRect(
                color = if (point.isProjected) projectedColor else realizedColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
            if (point.isProjected) {
                drawLine(
                    dashedColor,
                    Offset(left, top),
                    Offset(left + barWidth, top),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
        }

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 12.sp.toPx()
        }
        labelPaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("vandaag", 0f, size.height, labelPaint)
        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(bars.last().date.toShortDisplayString(), size.width, size.height, labelPaint)
    }
}

@Composable
private fun WeekRow(transaction: Transaction, categoryName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategorySquare(categoryName, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(transaction.counterpartyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${categoryName ?: "Nog te categoriseren"} · ${transaction.date.toShortDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val isIncome = transaction.amount.cents > 0
        Text(
            transaction.amount.toSignedMagnitudeString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isIncome) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
