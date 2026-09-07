package com.financio.app.ui.vandaag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.CategorySquare
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Transaction
import com.financio.core.usecase.BalanceForecastCalculator
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun VandaagScreen(
    onSeeAllClick: () -> Unit,
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
                SafeToSpendHero(state.safeToSpend!!)
            } else {
                MissingBalanceNotice(onImportClick)
            }
        }
        state.forecast?.let { forecast -> item { ForecastCard(forecast) } }
        item { TaskTiles(state, onSeeAllClick) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Deze week op je rekening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Alles",
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
            item { androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Header(mostRecentDate: LocalDate?, isStale: Boolean, onImportClick: () -> Unit) {
    val today = LocalDate.now()
    Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
        Text(
            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("nl")).replaceFirstChar { it.uppercase() } +
                " ${today.dayOfMonth} " + today.month.getDisplayName(TextStyle.FULL, Locale("nl")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        // A stale import becomes a tappable nudge instead of a caption nobody reads - the biggest
        // risk in an import-only app is not noticing you're looking at old data.
        if (mostRecentDate != null) {
            Text(
                if (isStale) "Data loopt achter — importeer een nieuw bestand →" else "t/m ${mostRecentDate.toShortDisplayString()} bijgewerkt",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isStale) LocalBudgetStatusColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isStale) FontWeight.SemiBold else FontWeight.Normal,
                modifier = if (isStale) Modifier.clickable(onClick = onImportClick) else Modifier,
            )
        }
    }
}

@Composable
private fun SafeToSpendHero(result: com.financio.core.usecase.SafeToSpendCalculator.Result) {
    val nextMonthFirst = LocalDate.now().plusMonths(1).withDayOfMonth(1)
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(
            "Vrij te besteden tot ${nextMonthFirst.dayOfMonth} ${nextMonthFirst.month.getDisplayName(TextStyle.FULL, Locale("nl"))}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            result.safeToSpendTotal.toDisplayString(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${result.safeToSpendPerDay.toDisplayString()} per dag · ${result.daysRemaining} dagen te gaan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Verwacht saldo eind $endOfMonthLabel", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(forecast.points.last().balance.toDisplayString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        ForecastChart(forecast, modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp))
        forecast.tightDate?.let { tightDate ->
            Text(
                "Wordt krap rond ${tightDate.toShortDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalBudgetStatusColors.current.warning,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ForecastChart(forecast: BalanceForecastCalculator.Result, modifier: Modifier = Modifier) {
    val points = forecast.points
    if (points.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val projectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tightColor = LocalBudgetStatusColors.current.warning
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minValue = points.minOf { it.balance.cents }.coerceAtMost(0L)
    val maxValue = points.maxOf { it.balance.cents }.coerceAtLeast(minValue + 1)

    Canvas(modifier) {
        val labelHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight
        val stepX = size.width / (points.size - 1)

        fun yFor(cents: Long): Float {
            val fraction = (cents - minValue).toFloat() / (maxValue - minValue).toFloat()
            return chartHeight - chartHeight * fraction
        }

        forecast.tightDate?.let { tightDate ->
            val index = points.indexOfFirst { it.date == tightDate }
            if (index >= 0) {
                val x = index * stepX
                drawLine(tightColor, Offset(x, 0f), Offset(x, chartHeight), strokeWidth = 2.dp.toPx())
            }
        }

        val solidPath = Path()
        val dottedPath = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = yFor(point.balance.cents)
            if (index == 0) {
                solidPath.moveTo(x, y)
            } else if (!point.isProjected) {
                solidPath.lineTo(x, y)
            } else {
                if (dottedPath.isEmpty) dottedPath.moveTo((index - 1) * stepX, yFor(points[index - 1].balance.cents))
                dottedPath.lineTo(x, y)
            }
        }
        drawPath(solidPath, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
        if (!dottedPath.isEmpty) {
            drawPath(
                dottedPath,
                color = projectedColor,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
            )
        }

        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }
        labelPaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("vandaag", 0f, size.height, labelPaint)
        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(points.last().date.toShortDisplayString(), size.width, size.height, labelPaint)
    }
}

@Composable
private fun TaskTiles(state: VandaagUiState, onSeeAllClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.uncategorizedCount > 0) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = onSeeAllClick)
                    .padding(16.dp),
            ) {
                Text(
                    state.uncategorizedCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "nog te categoriseren",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Nu doen →",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (state.subscriptionCount > 0) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(state.subscriptionMonthlyTotal.toDisplayString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "vaste lasten deze maand",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${state.subscriptionCount} abonnementen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekRow(transaction: Transaction, categoryName: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategorySquare(categoryName, size = 28.dp)
        Column(Modifier.weight(1f)) {
            Text(transaction.counterpartyName, fontWeight = FontWeight.SemiBold)
            Text(
                "${categoryName ?: "Nog te categoriseren"} · ${transaction.date.toShortDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val isIncome = transaction.amount.cents > 0
        Text(
            (if (isIncome) "+" else "") + transaction.amount.toDisplayString(),
            fontWeight = FontWeight.SemiBold,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
