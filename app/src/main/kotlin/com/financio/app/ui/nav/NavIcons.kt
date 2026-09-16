package com.financio.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/**
 * Small hand-drawn nav icons, same shapes as the icon sprite in the schermontwerp artifact —
 * kept as Canvas draws instead of pulling in the material-icons-extended dependency for five
 * glyphs the app will only ever need these five of.
 *
 * All coordinates below are in a fixed 24×24 design-unit grid, regardless of the actual pixel
 * size of the Canvas they're drawn into. [DrawScope.size] is in real pixels (24.dp × device
 * density — 72px or more on most phones, not 24), so drawing raw values like `Offset(5f, 7f)`
 * directly used to place the whole icon in a tiny corner of a much larger canvas, making every
 * icon look "much too small". [scale] rescales the 24-unit design grid to whatever the Canvas's
 * actual pixel size turns out to be, once, per icon.
 */
private const val DESIGN_SIZE = 24f
private val iconStrokeWidth = 1.8f

@Composable
fun VandaagIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            val center = Offset(DESIGN_SIZE / 2f, DESIGN_SIZE / 2f)
            drawCircle(color, radius = 4.5f, center = center, style = Stroke(width = iconStrokeWidth))
            val rayInner = 7.5f
            val rayOuter = 10f
            listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0).forEach { degrees ->
                val radians = Math.toRadians(degrees)
                val dx = kotlin.math.cos(radians).toFloat()
                val dy = kotlin.math.sin(radians).toFloat()
                drawLine(
                    color,
                    Offset(center.x + dx * rayInner, center.y + dy * rayInner),
                    Offset(center.x + dx * rayOuter, center.y + dy * rayOuter),
                    strokeWidth = iconStrokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun TransactionsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            drawLine(color, Offset(5f, 7f), Offset(19f, 7f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(5f, 12f), Offset(19f, 12f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(5f, 17f), Offset(13f, 17f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun ChartsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            drawLine(color, Offset(4f, 18f), Offset(20f, 18f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(7f, 18f), Offset(7f, 11f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(12.5f, 18f), Offset(12.5f, 6f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(18f, 18f), Offset(18f, 14f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun SavingsGoalsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            drawLine(color, Offset(7f, 4f), Offset(7f, 20f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            val flag = Path().apply {
                moveTo(7f, 5f)
                lineTo(17f, 8f)
                lineTo(7f, 11f)
                close()
            }
            drawPath(flag, color)
        }
    }
}

/** "Meer" catch-all tab — three dots, the usual overflow/"more" affordance, not a gear: this tab is
 * mostly Vaste lasten/Budget/Rekeningen/Categorieën now, settings is just one row inside it. */
@Composable
fun MeerIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            listOf(6f, 12f, 18f).forEach { x -> drawCircle(color, radius = 1.8f, center = Offset(x, 12f)) }
        }
    }
}
