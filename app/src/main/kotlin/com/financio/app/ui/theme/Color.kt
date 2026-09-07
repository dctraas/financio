package com.financio.app.ui.theme

import androidx.compose.ui.graphics.Color

// Redesigned palette (Claude Design pass, September 2026): warm paper instead of cool gray, one
// deep forest green as the only brand accent, ochre and clay for status — replacing the earlier
// cooler green/amber/rose set. See Theme.kt for how these map onto every Material3 role.

/**
 * Without this shared interface, `if (darkTheme) FinancioColorsDark else FinancioColorsLight`
 * in Theme.kt infers as `Any` — the two objects have no common supertype otherwise — and every
 * `tokens.xxx` property access fails to resolve.
 */
interface FinancioColorTokens {
    val background: Color
    val surface: Color
    val surfaceAlt: Color
    val ink: Color
    val inkSoft: Color
    val inkFaint: Color
    val line: Color
    val lineFaint: Color
    val accent: Color
    val accentSoft: Color
    val onAccent: Color
    val onAccentSoft: Color
    val amber: Color
    val amberSoft: Color
    val onAmber: Color
    val onAmberSoft: Color
    val rose: Color
    val roseSoft: Color
    val onRose: Color
    val onRoseSoft: Color
}

object FinancioColorsLight : FinancioColorTokens {
    override val background = Color(0xFFF5F2EC) // warm paper
    override val surface = Color(0xFFFFFFFF)
    override val surfaceAlt = Color(0xFFEDE7DA)
    override val ink = Color(0xFF14201B)
    override val inkSoft = Color(0xFF5B6259)
    override val inkFaint = Color(0xFF8B9188)
    override val line = Color(0xFFDDD6C7)
    override val lineFaint = Color(0xFFE8E2D3)
    override val accent = Color(0xFF0F5C42) // deep bosgroen — the one brand accent
    override val accentSoft = Color(0xFFDCEBE3)
    override val onAccent = Color(0xFFFFFFFF)
    override val onAccentSoft = Color(0xFF0B3D2C)
    override val amber = Color(0xFFB0791A) // oker — "let op" status
    override val amberSoft = Color(0xFFF5E7C9)
    override val onAmber = Color(0xFFFFFFFF)
    override val onAmberSoft = Color(0xFF6B4A10)
    override val rose = Color(0xFFA63D2E) // klei — the one red, used for both "over" and error
    override val roseSoft = Color(0xFFF3DAD3)
    override val onRose = Color(0xFFFFFFFF)
    override val onRoseSoft = Color(0xFF6E2B1F)
}

object FinancioColorsDark : FinancioColorTokens {
    override val background = Color(0xFF17130F) // warm near-black, not cool charcoal
    override val surface = Color(0xFF1F1B15)
    override val surfaceAlt = Color(0xFF2A2419)
    override val ink = Color(0xFFECE7D9)
    override val inkSoft = Color(0xFFA79C87)
    override val inkFaint = Color(0xFF79705E)
    override val line = Color(0xFF3A3324)
    override val lineFaint = Color(0xFF2E2819)
    override val accent = Color(0xFF4FC492) // brightened for contrast against a dark background
    override val accentSoft = Color(0xFF1E3A2D)
    override val onAccent = Color(0xFF0B2A1E)
    override val onAccentSoft = Color(0xFFBFE9D6)
    override val amber = Color(0xFFE3A63F)
    override val amberSoft = Color(0xFF3B2E14)
    override val onAmber = Color(0xFF2E2001)
    override val onAmberSoft = Color(0xFFF0D9A0)
    override val rose = Color(0xFFE2695A)
    override val roseSoft = Color(0xFF3B2019)
    override val onRose = Color(0xFF300E09)
    override val onRoseSoft = Color(0xFFF2C8C0)
}

/**
 * Category identity colors — the first five swatches match the schermontwerp mockup exactly;
 * the rest extend that same muted palette for the default categories added when the app had no
 * seeded categories at all (see DefaultCategorization in :core). Deliberately not semantic.
 */
object CategoryColors {
    val groceries = Color(0xFF5B7A52)
    val subscriptions = Color(0xFF7A6A45)
    val dining = Color(0xFF8A4A3D)
    val transport = Color(0xFF4C6E77)
    val clothing = Color(0xFF6B6485)
    val housing = Color(0xFF4A5A8A)
    val health = Color(0xFF3D8A6E)
    val leisure = Color(0xFF9C7A3D)
    val travel = Color(0xFF3D8FA3)
    val gifts = Color(0xFFA35D82)
    val savings = Color(0xFF4A8A5D)
    val income = Color(0xFF2E7D6B)
    val fallback = Color(0xFF8B9992)
}
