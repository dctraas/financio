package com.financio.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The schermontwerp redesign's segmented bar — colored proportional segments with a small gap
 * between them, replacing a donut chart everywhere one appeared (a donut reads worse than a bar
 * at 412dp width and is harder to label). [segments] are (color, fraction) pairs; the caller
 * decides whether the last one is a muted "rest" bucket (as `#CFCBBF`-style) or a real category.
 * Fractions should sum to roughly 1 - each segment gets `weight(fraction)` in the underlying
 * [Row], so they're read as relative to each other and to the whole bar either way.
 *
 * [animate] plays the "grow from 0" entrance the categorize-done screen uses once, per the
 * redesign's "dit is het enige moment waarop de app viert" note - every other screen using this
 * bar (Inzicht, ...) leaves it off and shows the final widths immediately.
 */
@Composable
fun SegmentedBar(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    gap: Dp = 2.dp,
    animate: Boolean = false,
) {
    var grown by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate) { if (animate) grown = true }

    Row(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp))) {
        segments.forEachIndexed { index, (color, fraction) ->
            val animatedFraction by animateFloatAsState(
                targetValue = if (grown) fraction.coerceAtLeast(0f) else 0f,
                animationSpec = tween(durationMillis = 500, delayMillis = index * 60),
                label = "segmented-bar-$index",
            )
            if (index > 0) Spacer(Modifier.width(gap))
            Spacer(
                Modifier
                    .weight(animatedFraction.coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}
