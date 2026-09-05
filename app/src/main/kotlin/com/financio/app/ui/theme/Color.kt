package com.financio.app.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors the design tokens from the schermontwerp artifact 1:1, so the app matches what was
// reviewed rather than drifting into a fresh palette during implementation.

object FinancioColorsLight {
    val background = Color(0xFFF4F7F5)
    val surface = Color(0xFFFFFFFF)
    val surfaceAlt = Color(0xFFEAF0EC)
    val ink = Color(0xFF16231F)
    val inkSoft = Color(0xFF5C6E67)
    val inkFaint = Color(0xFF8B9992)
    val line = Color(0xFFDCE5E0)
    val accent = Color(0xFF1E8A63)
    val accentSoft = Color(0xFFDEEFE6)
    val amber = Color(0xFFB9791F)
    val amberSoft = Color(0xFFF6E8CE)
    val rose = Color(0xFFC0392B)
    val roseSoft = Color(0xFFF8E1DD)
}

object FinancioColorsDark {
    val background = Color(0xFF0E1613)
    val surface = Color(0xFF172420)
    val surfaceAlt = Color(0xFF1D2E29)
    val ink = Color(0xFFE8EDEA)
    val inkSoft = Color(0xFF9FB0AA)
    val inkFaint = Color(0xFF71827C)
    val line = Color(0xFF2A3B36)
    val accent = Color(0xFF4FCB9B)
    val accentSoft = Color(0xFF1C332B)
    val amber = Color(0xFFE8AC55)
    val amberSoft = Color(0xFF3A2E18)
    val rose = Color(0xFFF0796A)
    val roseSoft = Color(0xFF3C2420)
}

/** Category identity colors — same swatches as the schermontwerp mockup, deliberately not semantic. */
object CategoryColors {
    val groceries = Color(0xFF5B7A52)
    val subscriptions = Color(0xFF7A6A45)
    val dining = Color(0xFF8A4A3D)
    val transport = Color(0xFF4C6E77)
    val clothing = Color(0xFF6B6485)
    val fallback = Color(0xFF8B9992)
}
