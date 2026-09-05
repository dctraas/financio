package com.financio.app.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors the design tokens from the schermontwerp artifact 1:1, so the app matches what was
// reviewed rather than drifting into a fresh palette during implementation.

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
    val accent: Color
    val accentSoft: Color
    val amber: Color
    val amberSoft: Color
    val rose: Color
    val roseSoft: Color
}

object FinancioColorsLight : FinancioColorTokens {
    override val background = Color(0xFFF4F7F5)
    override val surface = Color(0xFFFFFFFF)
    override val surfaceAlt = Color(0xFFEAF0EC)
    override val ink = Color(0xFF16231F)
    override val inkSoft = Color(0xFF5C6E67)
    override val inkFaint = Color(0xFF8B9992)
    override val line = Color(0xFFDCE5E0)
    override val accent = Color(0xFF1E8A63)
    override val accentSoft = Color(0xFFDEEFE6)
    override val amber = Color(0xFFB9791F)
    override val amberSoft = Color(0xFFF6E8CE)
    override val rose = Color(0xFFC0392B)
    override val roseSoft = Color(0xFFF8E1DD)
}

object FinancioColorsDark : FinancioColorTokens {
    override val background = Color(0xFF0E1613)
    override val surface = Color(0xFF172420)
    override val surfaceAlt = Color(0xFF1D2E29)
    override val ink = Color(0xFFE8EDEA)
    override val inkSoft = Color(0xFF9FB0AA)
    override val inkFaint = Color(0xFF71827C)
    override val line = Color(0xFF2A3B36)
    override val accent = Color(0xFF4FCB9B)
    override val accentSoft = Color(0xFF1C332B)
    override val amber = Color(0xFFE8AC55)
    override val amberSoft = Color(0xFF3A2E18)
    override val rose = Color(0xFFF0796A)
    override val roseSoft = Color(0xFF3C2420)
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
