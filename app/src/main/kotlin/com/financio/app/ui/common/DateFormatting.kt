package com.financio.app.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("nl"))

/** "4 sep" — used anywhere a compact, Dutch-locale date is shown (transaction rows, import review). */
fun LocalDate.toShortDisplayString(): String =
    format(shortDateFormatter).let { formatted ->
        // Force a lowercase month abbreviation regardless of locale data quirks ("4 Sep" -> "4 sep").
        val parts = formatted.split(" ")
        if (parts.size == 2) "${parts[0]} ${parts[1].lowercase()}" else formatted
    }
