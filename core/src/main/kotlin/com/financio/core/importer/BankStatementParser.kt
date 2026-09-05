package com.financio.core.importer

import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat

class UnrecognizedFormatException(message: String) : Exception(message)

/** One adapter per bron-formaat. Everything above this interface is formaat-onafhankelijk. */
interface BankStatementParser {
    val format: SourceFormat
    fun parse(content: String, accountId: Long): List<ParsedTransaction>
}

/**
 * Sniffs the raw file content to decide which parser to hand it to. Deliberately conservative:
 * an unrecognized format fails loudly here rather than letting a parser guess and silently
 * produce wrong transactions (see the architecture doc's risk list).
 */
object FormatDetector {

    fun detect(content: String): SourceFormat {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith(":20:") -> SourceFormat.MT940
            trimmed.firstOrNull { !it.isWhitespace() } != null && looksLikeIngCsvHeader(trimmed) -> SourceFormat.CSV
            else -> throw UnrecognizedFormatException(
                "Onbekend bestandsformaat — verwacht een MT940-statement (begint met ':20:') " +
                    "of een ING CSV-export (header met 'Datum;...')."
            )
        }
    }

    private fun looksLikeIngCsvHeader(content: String): Boolean {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return firstLine.contains("Datum") && firstLine.contains(";")
    }
}
