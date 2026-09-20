package com.financio.app.ui.common

import com.financio.core.model.Money

/**
 * "+ 2.840,00" / "− 38,72" — no € symbol, a real minus glyph rather than a hyphen, and a space
 * after the sign. The schermontwerp redesign's compact list-row convention, used wherever a full
 * [Money.toDisplayString] would be too busy: Vandaag's week list, Transacties' rows and day/group
 * totals. Zero shows no sign at all, matching [Money.toDisplayString]'s own zero handling.
 */
fun Money.toSignedMagnitudeString(showCents: Boolean = true): String {
    val magnitude = toDisplayString(showCents).removePrefix("-").removePrefix("€")
    return when {
        cents > 0 -> "+ $magnitude"
        cents < 0 -> "− $magnitude"
        else -> magnitude
    }
}
