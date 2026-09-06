package com.financio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.financio.app.data.local.ThemeMode

/** Semantic budget-status colors — kept separate from Material's color scheme so "over budget"
 * always means the same literal color everywhere it's used, light or dark theme. */
data class BudgetStatusColors(val ok: Color, val warning: Color, val over: Color)

@Composable
fun FinancioTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens: FinancioColorTokens = if (darkTheme) FinancioColorsDark else FinancioColorsLight

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = tokens.background,
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceAlt,
            onBackground = tokens.ink,
            onSurface = tokens.ink,
            primary = tokens.accent,
            onPrimary = tokens.surface,
            secondaryContainer = tokens.accentSoft,
            outline = tokens.line,
        )
    } else {
        lightColorScheme(
            background = tokens.background,
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceAlt,
            onBackground = tokens.ink,
            onSurface = tokens.ink,
            primary = tokens.accent,
            onPrimary = tokens.surface,
            secondaryContainer = tokens.accentSoft,
            outline = tokens.line,
        )
    }

    val statusColors = if (darkTheme) {
        BudgetStatusColors(ok = tokens.accent, warning = FinancioColorsDark.amber, over = FinancioColorsDark.rose)
    } else {
        BudgetStatusColors(ok = tokens.accent, warning = FinancioColorsLight.amber, over = FinancioColorsLight.rose)
    }

    MaterialTheme(colorScheme = colorScheme, typography = financioTypography) {
        CompositionLocalProvider(LocalBudgetStatusColors provides statusColors, content = content)
    }
}

private val financioTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

val LocalBudgetStatusColors = staticCompositionLocalOf {
    BudgetStatusColors(ok = Color.Unspecified, warning = Color.Unspecified, over = Color.Unspecified)
}
