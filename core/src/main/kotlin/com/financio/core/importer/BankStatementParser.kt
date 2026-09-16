package com.financio.core.importer

import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat

/**
 * [rawLines] (the file's first few lines) and [detectedColumns] (the header actually found) are
 * only ever populated for a missing-required-column CSV failure — the import screen's error state
 * uses them to let the user manually point at "this is actually the date column" instead of just
 * showing a dead-end message. Empty for every other failure (an unrecognized format entirely, an
 * unparsable Af/Bij value, ...), where there's no single column to recover by picking.
 */
class UnrecognizedFormatException(
    message: String,
    val rawLines: List<String> = emptyList(),
    val detectedColumns: List<String> = emptyList(),
) : Exception(message)

/**
 * The file's own account, as identified by the file itself (an IBAN, or an internal ING code
 * like "L866-14401" for a savings account that has no visible IBAN in the export) — not a
 * counterparty. Used to detect whether an import belongs to an account the app doesn't know
 * about yet. [suggestedName] is a human-readable label the file also provides, if any
 * (e.g. "Oranje Spaarrekening"), to prefill a new-account screen with.
 */
data class DetectedAccount(val rawIdentifier: String, val suggestedName: String? = null)

/** One adapter per bron-formaat. Everything above this interface is formaat-onafhankelijk. */
interface BankStatementParser {
    val format: SourceFormat

    /**
     * [dateColumnOverrideIndex] lets the import screen's error-recovery flow say "column 3 is
     * actually the date column" when the header's real name wasn't recognized — ignored by every
     * parser except [CsvIngParser], which is the only one that identifies columns by name at all.
     */
    fun parse(content: String, accountId: Long, dateColumnOverrideIndex: Int? = null): List<ParsedTransaction>

    /**
     * Identifies which of the user's own bank accounts this file is an export of, if the format
     * exposes that. Returns null when the file carries no such identifier or parsing it fails —
     * callers treat that as "can't tell", not as "this is a new account".
     */
    fun detectOwnAccount(content: String): DetectedAccount? = null
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
