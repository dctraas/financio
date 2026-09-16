package com.financio.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * size of the Canvas they're drawn into. [DrawScope.size] is in real pixels (20.dp × device
 * density, per the redesign's "20dp, 1.8dp lijndikte" tab-icon spec — not 24), so drawing raw
 * values like `Offset(5f, 7f)` directly used to place the whole icon in a tiny corner of a much
 * larger canvas, making every icon look "much too small". [scale] rescales the 24-unit design
 * grid to whatever the Canvas's actual pixel size turns out to be, once, per icon.
 */
private const val DESIGN_SIZE = 24f
private const val ICON_SIZE_DP = 20
private val iconStrokeWidth = 1.8f

/** Plain circle — the schermontwerp redesign's Vandaag glyph, not the earlier sun-with-rays. */
@Composable
fun VandaagIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(ICON_SIZE_DP.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            drawCircle(color, radius = 7f, center = Offset(DESIGN_SIZE / 2f, DESIGN_SIZE / 2f), style = Stroke(width = iconStrokeWidth))
        }
    }
}

@Composable
fun TransactionsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(ICON_SIZE_DP.dp)) {
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
    Canvas(modifier.size(ICON_SIZE_DP.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            drawLine(color, Offset(7f, 18f), Offset(7f, 11f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(12.5f, 18f), Offset(12.5f, 6f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(18f, 18f), Offset(18f, 14f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
        }
    }
}

/** Concentric circles ("dubbele cirkel/roos") — a target, standing in for a doel. */
@Composable
fun SavingsGoalsIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(ICON_SIZE_DP.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            val center = Offset(DESIGN_SIZE / 2f, DESIGN_SIZE / 2f)
            drawCircle(color, radius = 8f, center = center, style = Stroke(width = iconStrokeWidth))
            drawCircle(color, radius = 3.5f, center = center, style = Stroke(width = iconStrokeWidth))
        }
    }
}

/** 2×2 grid of squares — the schermontwerp redesign's Beheer glyph, replacing the earlier "meer" three-dot overflow icon now that this tab is a named hub, not a catch-all. */
@Composable
fun MeerIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(ICON_SIZE_DP.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            val squareSize = 7f
            val gap = 2f
            val start = (DESIGN_SIZE - squareSize * 2 - gap) / 2f
            listOf(0, 1).forEach { row ->
                listOf(0, 1).forEach { col ->
                    val x = start + col * (squareSize + gap)
                    val y = start + row * (squareSize + gap)
                    drawRoundRect(
                        color,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f),
                    )
                }
            }
        }
    }
}
