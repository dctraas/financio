package com.financio.app.ui.networth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.theme.LocalBudgetStatusColors
import com.financio.core.model.Money

/**
 * "Hoeveel ben ik waard, en gaat dat de goede kant op" — every account's own balance plus every
 * savings goal's progress, as one number with a 6-month trend, instead of the four separate
 * numbers Meer's tiles show as of right now only.
 */
@Composable
fun NetWorthScreen(onBackClick: () -> Unit, viewModel: NetWorthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vermogen") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        if (!state.hasAnyData) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("Nog geen vermogen om te tonen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Zodra er rekeningen of spaardoelen zijn, verschijnt hier het overzicht.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(state.current.toDisplayString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            state.deltaLabel?.let { label ->
                val statusColors = LocalBudgetStatusColors.current
                Text(
                    (if (state.deltaIsGood) "▲ " else "▼ ") + label,
                    color = if (state.deltaIsGood) statusColors.ok else statusColors.over,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            NetWorthBarChart(state.points, modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BreakdownStat("Rekeningen", state.accountsTotal)
                BreakdownStat("Spaardoelen", state.savingsTotal)
            }
        }
    }
}

@Composable
private fun BreakdownStat(label: String, amount: Money) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount.toDisplayString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A simplified version of Inzicht's own bar chart - no limit line, no tap-to-navigate, since a net worth trend has no budget concept and no reason to shift the window (always the trailing 6 months). */
@Composable
private fun NetWorthBarChart(points: List<NetWorthPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val statusColors = LocalBudgetStatusColors.current
    val barColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentColor = statusColors.ok

    val maxValue = points.maxOf { it.amount.cents }.coerceAtLeast(1L)
    val minValue = points.minOf { it.amount.cents }.coerceAtMost(0L)
    val range = (maxValue - minValue).coerceAtLeast(1L)

    Canvas(modifier) {
        val labelHeight = 20.dp.toPx()
        val valueLabelHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - valueLabelHeight
        val barWidth = size.width / (points.size * 2f)
        val gap = barWidth
        val zeroY = valueLabelHeight + chartHeight * (maxValue.toFloat() / range.toFloat())

        points.forEachIndexed { index, point ->
            val barHeight = chartHeight * (kotlin.math.abs(point.amount.cents).toFloat() / range.toFloat())
            val x = gap / 2f + index * (barWidth + gap)
            val isNegative = point.amount.cents < 0
            val top = if (isNegative) zeroY else zeroY - barHeight
            val color = if (point.isCurrent) currentColor else barColor.copy(alpha = 0.35f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawContext.canvas.nativeCanvas.drawText(
                point.amount.toDisplayString(),
                x + barWidth / 2f,
                top - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = labelColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 9.sp.toPx()
                },
            )
            drawContext.canvas.nativeCanvas.drawText(
                point.label,
                x + barWidth / 2f,
                size.height - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    this.color = labelColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 11.sp.toPx()
                },
            )
        }
    }
}
