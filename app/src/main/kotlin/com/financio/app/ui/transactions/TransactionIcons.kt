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
 * Same Canvas-drawn approach as [com.financio.app.ui.nav.NavIcons] — `Search` would otherwise pull
 * in `material-icons-extended` (or rely on it actually being one of the handful of icons
 * `material-icons-core` bundles, which isn't worth staking a real build on from this sandbox) for
 * the one glyph Transacties' always-visible search field needs.
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
