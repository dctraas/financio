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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.financio.app.data.local.TextSize
import com.financio.app.data.local.ThemeMode

/** Semantic budget-status colors — kept separate from Material's color scheme so "over budget"
 * always means the same literal color everywhere it's used, light or dark theme. */
data class BudgetStatusColors(val ok: Color, val warning: Color, val over: Color)

@Composable
fun FinancioTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, textSize: TextSize = TextSize.STANDARD, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens: FinancioColorTokens = if (darkTheme) FinancioColorsDark else FinancioColorsLight
    val inverseTokens: FinancioColorTokens = if (darkTheme) FinancioColorsLight else FinancioColorsDark

    // Every Material3 role is set explicitly here, not just the handful (background/surface/
    // primary/outline/...) the app used to override — anything left unset falls back to M3's own
    // baseline scheme, which is generated from a purple seed color. That's exactly why
    // onSecondaryContainer, tertiary and error used to show as an unrelated lilac regardless of
    // theme: this app's own tokens never reached them. tertiary is deliberately the *same* rose
    // as error rather than a fourth hue — "one red, not two" — since this app has no third accent
    // family beyond green/ochre/clay-red to give tertiary its own identity.
    //
    // Not overridden: the newer surfaceDim/surfaceBright/surfaceContainer(Low/High/...) tonal
    // roles. Their exact parameter set has moved between compose-material3 releases and this
    // sandbox cannot compile :app to confirm which are available on the pinned BOM version, so
    // guessing at names risked a real compile error for a handful of roles this card-based,
    // mostly-flat-surface design barely uses. They're left at each factory's own light/dark
    // baseline default, which is a neutral (very slightly warm-purple) gray, not the more visibly
    // wrong lilac the *colored* roles above were leaking.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            primaryContainer = tokens.accentSoft,
            onPrimaryContainer = tokens.onAccentSoft,
            inversePrimary = inverseTokens.accent,
            secondary = tokens.amber,
            onSecondary = tokens.onAmber,
            secondaryContainer = tokens.amberSoft,
            onSecondaryContainer = tokens.onAmberSoft,
            tertiary = tokens.rose,
            onTertiary = tokens.onRose,
            tertiaryContainer = tokens.roseSoft,
            onTertiaryContainer = tokens.onRoseSoft,
            background = tokens.background,
            onBackground = tokens.ink,
            surface = tokens.surface,
            onSurface = tokens.ink,
            surfaceVariant = tokens.surfaceAlt,
            onSurfaceVariant = tokens.inkSoft,
            surfaceTint = tokens.accent,
            inverseSurface = inverseTokens.surface,
            inverseOnSurface = inverseTokens.ink,
            error = tokens.rose,
            onError = tokens.onRose,
            errorContainer = tokens.roseSoft,
            onErrorContainer = tokens.onRoseSoft,
            outline = tokens.line,
            outlineVariant = tokens.lineFaint,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            primaryContainer = tokens.accentSoft,
            onPrimaryContainer = tokens.onAccentSoft,
            inversePrimary = inverseTokens.accent,
            secondary = tokens.amber,
            onSecondary = tokens.onAmber,
            secondaryContainer = tokens.amberSoft,
            onSecondaryContainer = tokens.onAmberSoft,
            tertiary = tokens.rose,
            onTertiary = tokens.onRose,
            tertiaryContainer = tokens.roseSoft,
            onTertiaryContainer = tokens.onRoseSoft,
            background = tokens.background,
            onBackground = tokens.ink,
            surface = tokens.surface,
            onSurface = tokens.ink,
            surfaceVariant = tokens.surfaceAlt,
            onSurfaceVariant = tokens.inkSoft,
            surfaceTint = tokens.accent,
            inverseSurface = inverseTokens.surface,
            inverseOnSurface = inverseTokens.ink,
            error = tokens.rose,
            onError = tokens.onRose,
            errorContainer = tokens.roseSoft,
            onErrorContainer = tokens.onRoseSoft,
            outline = tokens.line,
            outlineVariant = tokens.lineFaint,
            scrim = Color.Black,
        )
    }

    // "Over budget" always reads as the one rose/clay red, "let op" always as the one ochre amber,
    // regardless of which Material role a given screen happens to pull its color from.
    val statusColors = BudgetStatusColors(ok = tokens.accent, warning = tokens.amber, over = tokens.rose)

    // Every text style in financioTypography (and Material3's own defaults) is defined in sp,
    // which already scales with LocalDensity.fontScale at measure/draw time - overriding just
    // that one field of the ambient Density here is enough to scale every screen's text app-wide,
    // with no per-screen changes needed.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = textSize.fontScale)) {
        MaterialTheme(colorScheme = colorScheme, typography = financioTypography) {
            CompositionLocalProvider(LocalBudgetStatusColors provides statusColors, content = content)
        }
    }
}

/**
 * Named "Instrument Sans" (UI) + a serif for money in the schermontwerp redesign, loaded via
 * Android's Downloadable Fonts API in the real design. Deliberately NOT wired here: that API
 * needs a `com_google_android_gms_fonts_certs` certificate array — a large, opaque, byte-exact
 * constant — and this sandbox has no way to verify a reproduced copy is correct (a single wrong
 * byte fails silently, falling back to the default typeface with no error to catch). Until that's
 * wired for real, [FontFamily.SansSerif]/[FontFamily.Serif] stand in: every screen already reads
 * fonts through this one [Typography] object, so swapping in the real families later is a two-line
 * change here, not a per-screen one.
 */
private val sansFamily = FontFamily.SansSerif
private val serifFamily = FontFamily.Serif

/** Tabular (fixed-width) figures, so a column of amounts actually lines up — most visible in
 * Transacties and Budget's lists. Best-effort: depends on the active font actually shipping the
 * OpenType "tnum" feature, unverifiable against the real named fonts for the reason above. */
private const val TABULAR_FIGURES = "tnum"

private val financioTypography = Typography(
    displayLarge = TextStyle(fontFamily = serifFamily, fontSize = 40.sp, lineHeight = 46.sp, fontFeatureSettings = TABULAR_FIGURES),
    displayMedium = TextStyle(fontFamily = serifFamily, fontSize = 32.sp, lineHeight = 38.sp, fontFeatureSettings = TABULAR_FIGURES),
    displaySmall = TextStyle(fontFamily = serifFamily, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = TABULAR_FIGURES),
    // The big hero amounts (Vandaag's "vrij te besteden", a transaction's own amount) — serif,
    // matching the schermontwerp's "bedragen die ertoe doen in een serif" rule.
    headlineLarge = TextStyle(fontFamily = serifFamily, fontSize = 34.sp, lineHeight = 40.sp, fontFeatureSettings = TABULAR_FIGURES),
    headlineMedium = TextStyle(fontFamily = serifFamily, fontSize = 26.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR_FIGURES),
    headlineSmall = TextStyle(fontFamily = serifFamily, fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR_FIGURES),
    titleLarge = TextStyle(fontFamily = sansFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = sansFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = sansFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    // The three sizes everything that isn't a money hero or a section title uses: 15 primary,
    // 13 secondary, 11 label — per the schermontwerp's type scale.
    bodyLarge = TextStyle(fontFamily = sansFamily, fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR_FIGURES),
    bodyMedium = TextStyle(fontFamily = sansFamily, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
    bodySmall = TextStyle(fontFamily = sansFamily, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR_FIGURES),
    labelLarge = TextStyle(fontFamily = sansFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = sansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = sansFamily, fontSize = 11.sp, lineHeight = 14.sp),
)

val LocalBudgetStatusColors = staticCompositionLocalOf {
    BudgetStatusColors(ok = Color.Unspecified, warning = Color.Unspecified, over = Color.Unspecified)
}
