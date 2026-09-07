package com.financio.app.ui.transactions

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
 * Same Canvas-drawn approach as [com.financio.app.ui.nav.NavIcons] — `Search` and `FilterList`
 * would otherwise pull in `material-icons-extended` (or, for `Search`, rely on it actually being
 * one of the handful of icons `material-icons-core` bundles, which isn't worth staking a real
 * build on from this sandbox) for two glyphs the compact Transacties chrome needs.
 */
private const val DESIGN_SIZE = 24f
private val iconStrokeWidth = 1.8f

@Composable
fun SearchIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            drawCircle(color, radius = 6f, center = Offset(10f, 10f), style = Stroke(width = iconStrokeWidth))
            drawLine(color, Offset(14.5f, 14.5f), Offset(19f, 19f), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
        }
    }
}

/** Three sliders of decreasing length, each with a knob — the usual "filter" affordance. */
@Composable
fun FilterIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        scale(size.width / DESIGN_SIZE, size.height / DESIGN_SIZE, pivot = Offset.Zero) {
            val rows = listOf(Triple(4f, 20f, 8f), Triple(4f, 20f, 16f), Triple(4f, 20f, 12f))
            val ys = listOf(7f, 12f, 17f)
            rows.forEachIndexed { index, (startX, endX, knobX) ->
                val y = ys[index]
                drawLine(color, Offset(startX, y), Offset(endX, y), strokeWidth = iconStrokeWidth, cap = StrokeCap.Round)
                drawCircle(color, radius = 2f, center = Offset(knobX, y))
            }
        }
    }
}
