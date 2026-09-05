package com.financio.core.importer

import com.financio.core.model.Money
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses a "Mijn ING" rekeningoverzicht CSV export.
 *
 * Deliberately reads columns by *name*, not position: ING can (and has) reordered or renamed
 * columns between export versions. A column this parser needs that's missing fails loudly with
 * [UnrecognizedFormatException] instead of silently reading the wrong field.
 *
 * The field delimiter is auto-detected between a real tab and ";" rather than hardcoded: a real
 * "Mijn ING" export (despite the ".csv" extension) turned out to be tab-delimited, not the
 * semicolon originally assumed from the architecture doc's illustration — confirmed against an
 * actual downloaded export. Semicolon is kept as a fallback since it's a plausible variant (e.g.
 * a different export setting) and was the original assumption; if neither delimiter yields a
 * recognizable header this still fails with the same clear "column missing" error.
 *
 * Known simplification: assumes no column value itself contains the detected delimiter — true
 * for every ING export seen so far, since names/descriptions use ING's own separators internally.
 */
class CsvIngParser : BankStatementParser {

    override val format = SourceFormat.CSV

    override fun parse(content: String, accountId: Long): List<ParsedTransaction> {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "Leeg CSV-bestand." }

        val delimiter = detectDelimiter(lines.first())
        val header = lines.first().split(delimiter).map { it.trim() }
        val columnIndex = REQUIRED_COLUMNS.associateWith { column ->
            header.indexOf(column).also { index ->
                if (index < 0) {
                    throw UnrecognizedFormatException(
                        "Kolom '$column' ontbreekt in de CSV-header — is het ING-exportformaat gewijzigd?"
                    )
                }
            }
        }

        return lines.drop(1).map { line -> parseLine(line, delimiter, columnIndex, accountId) }
    }

    private fun detectDelimiter(headerLine: String): Char =
        CANDIDATE_DELIMITERS.firstOrNull { delimiter ->
            headerLine.split(delimiter).map { it.trim() }.contains(COL_DATE)
        } ?: ';' // no candidate recognized the header; fall through to the original error below

    private fun parseLine(line: String, delimiter: Char, columnIndex: Map<String, Int>, accountId: Long): ParsedTransaction {
        val fields = line.split(delimiter)
        fun col(name: String): String = fields.getOrElse(columnIndex.getValue(name)) { "" }.trim()

        val date = LocalDate.parse(col(COL_DATE), DATE_FORMAT)
        // "Bedrag (EUR)" is always a positive magnitude; direction comes from the separate Af/Bij column.
        val magnitude = Money(kotlin.math.abs(Money.parseCommaDecimal(col(COL_AMOUNT)).cents))
        val direction = col(COL_DIRECTION)
        val amount = when (direction) {
            "Af" -> -magnitude
            "Bij" -> magnitude
            else -> throw UnrecognizedFormatException("Onbekende Af/Bij-waarde: '$direction'")
        }

        val description = listOf(col(COL_NAME), col(COL_NOTES))
            .filter { it.isNotBlank() }
            .joinToString(" — ")

        val balance = col(COL_BALANCE).takeIf { it.isNotBlank() }?.let { Money.parseCommaDecimal(it) }

        return ParsedTransaction(
            accountId = accountId,
            date = date,
            amount = amount,
            counterpartyIban = col(COL_COUNTERPARTY).takeIf { it.isNotBlank() },
            counterpartyName = col(COL_NAME),
            description = description,
            balanceAfter = balance,
            sourceFormat = SourceFormat.CSV,
        )
    }

    companion object {
        private const val COL_DATE = "Datum"
        private const val COL_NAME = "Naam / Omschrijving"
        private const val COL_COUNTERPARTY = "Tegenrekening"
        private const val COL_DIRECTION = "Af Bij"
        private const val COL_AMOUNT = "Bedrag (EUR)"
        private const val COL_NOTES = "Mededelingen"
        private const val COL_BALANCE = "Saldo na mutatie"

        private val REQUIRED_COLUMNS = listOf(
            COL_DATE, COL_NAME, COL_COUNTERPARTY, COL_DIRECTION, COL_AMOUNT, COL_NOTES, COL_BALANCE,
        )
        private val CANDIDATE_DELIMITERS = listOf('\t', ';')
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
