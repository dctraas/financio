package com.financio.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Small hand-drawn nav icons, same shapes as the icon sprite in the schermontwerp artifact —
 * kept as Canvas draws instead of pulling in the material-icons-extended dependency for four
 * glyphs the app will only ever need these four of.
 */
private val iconStrokeWidth = 1.8.dp

@Composable
fun TransactionsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val stroke = iconStrokeWidth.toPx()
        drawLine(color, Offset(5f, 7f), Offset(19f, 7f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(5f, 12f), Offset(19f, 12f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(5f, 17f), Offset(13f, 17f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun BudgetsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val stroke = Stroke(width = iconStrokeWidth.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius = 8f, center = center, style = stroke)
        drawCircle(color, radius = 4.5f, center = center, style = stroke)
        drawCircle(color, radius = 1f, center = center)
    }
}

@Composable
fun ChartsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val stroke = iconStrokeWidth.toPx()
        drawLine(color, Offset(4f, 18f), Offset(20f, 18f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(7f, 18f), Offset(7f, 11f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(12.5f, 18f), Offset(12.5f, 6f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(18f, 18f), Offset(18f, 14f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val stroke = iconStrokeWidth.toPx()
        val rows = listOf(6f to 9f, 12f to 16f, 18f to 8f)
        rows.forEach { (y, knobX) ->
            drawLine(color, Offset(4f, y), Offset(20f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = 2.3f, center = Offset(knobX, y))
        }
    }
}
