package com.financio.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.financio.app.ui.theme.CategoryColors

/**
 * A 34dp colored square identifies a category at a glance from across the room — the schermontwerp
 * redesign's replacement for the original 11dp dot, which was recognizable only up close. Split and
 * uncategorized get their own visual instead of a color: split shows the category glyph slot empty
 * with a soft outline (its meaning lives in the row text next to it, not here), uncategorized is a
 * dashed square in the neutral `lineDashed` tone — the "needs attention" signal now lives in the
 * "Categorie kiezen" pill next to it (see Transacties), not in the square's color.
 */
@Composable
fun CategorySquare(categoryName: String?, isSplit: Boolean = false, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    when {
        isSplit -> {
            Box(
                modifier
                    .size(size)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp)),
            )
        }
        categoryName == null -> {
            val dashColor = MaterialTheme.colorScheme.outline
            Canvas(modifier.size(size)) {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))),
                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
                )
            }
        }
        else -> {
            Box(
                modifier
                    .size(size)
                    .clip(RoundedCornerShape(9.dp))
                    .background(categoryColorFor(categoryName)),
            )
        }
    }
}

/** Category name -> its identity color. Case-insensitive so it survives a user's own casing. */
fun categoryColorFor(categoryName: String?): Color = when (categoryName?.lowercase()) {
    "boodschappen" -> CategoryColors.groceries
    "abonnementen" -> CategoryColors.subscriptions
    "uit eten" -> CategoryColors.dining
    "vervoer" -> CategoryColors.transport
    "kleding & verzorging" -> CategoryColors.clothing
    "wonen & vaste lasten" -> CategoryColors.housing
    "gezondheid & verzekering" -> CategoryColors.health
    "vrije tijd & hobby's" -> CategoryColors.leisure
    "vakantie & reizen" -> CategoryColors.travel
    "cadeaus & giften" -> CategoryColors.gifts
    "sparen & beleggen" -> CategoryColors.savings
    "inkomsten" -> CategoryColors.income
    // "Overig" and anything user-created falls through to the neutral swatch on purpose.
    else -> CategoryColors.fallback
}
