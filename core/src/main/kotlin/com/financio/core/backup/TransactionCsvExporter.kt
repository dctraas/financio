package com.financio.core.backup

import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.Transaction

/**
 * Exports transactions back out as CSV — the one export format the app didn't have yet (see the
 * "Meer" redesign's Back-up & export section). Semicolon-delimited, Dutch decimal-comma amounts:
 * the same dialect [com.financio.core.importer.CsvIngParser] already reads, so this is round-
 * trippable with the app's own importer, not just "a CSV".
 */
object TransactionCsvExporter {
    private const val DELIMITER = ";"
    private val HEADER = listOf("Datum", "Naam", "Tegenrekening", "Omschrijving", "Bedrag", "Categorie", "Tag")

    fun export(transactions: List<Transaction>, categoriesById: Map<Long, Category>): String {
        val rows = transactions.sortedByDescending { it.date }.map { transaction ->
            listOf(
                transaction.date.toString(),
                transaction.counterpartyName,
                transaction.counterpartyIban ?: "",
                transaction.description,
                transaction.amount.toRawDecimalString(),
                categoriesById[transaction.categoryId]?.name ?: "",
                transaction.tag ?: "",
            ).joinToString(DELIMITER) { escapeCsvField(it) }
        }
        return (listOf(HEADER.joinToString(DELIMITER)) + rows).joinToString("\n")
    }

    /** RFC 4180: quote a field that contains the delimiter, a quote, or a newline; double up any quote inside it. */
    private fun escapeCsvField(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}

/** Plain "23,45" / "-23,45" — no euro sign, no thousands separator; the exact shape [Money.parseCommaDecimal] reads back. */
private fun Money.toRawDecimalString(): String {
    val sign = if (cents < 0) "-" else ""
    val absCents = kotlin.math.abs(cents)
    return "$sign${absCents / 100},${(absCents % 100).toString().padStart(2, '0')}"
}
