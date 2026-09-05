package com.financio.core.importer

import com.financio.core.model.Money
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses a SWIFT MT940 bank statement.
 *
 * Only the tags Financio needs are read: :61: (one line per transaction: date, amount, D/C)
 * and :86: (free-form info that follows it — usually "/KEY/value/KEY/value/" pairs, but its
 * exact shape varies per payment type). Per the architecture doc's risk list, an :86: value
 * that isn't in that key/value shape falls back to being stored as raw text rather than
 * throwing — a SEPA iDEAL payment and a plain incasso don't structure this field the same way.
 *
 * Known simplification: the running balance from :60F:/:62F: is statement-level, not attached
 * to individual transactions, so [ParsedTransaction.balanceAfter] is always null here.
 */
class Mt940Parser : BankStatementParser {

    override val format = SourceFormat.MT940

    override fun parse(content: String, accountId: Long): List<ParsedTransaction> {
        val lines = content.lines()
        val transactions = mutableListOf<ParsedTransaction>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith(TAG_STATEMENT_LINE)) {
                val statementLine = line.removePrefix(TAG_STATEMENT_LINE)
                val match = STATEMENT_LINE_PATTERN.matchEntire(statementLine)
                    ?: throw UnrecognizedFormatException("Kan :61:-regel niet lezen: '$line'")

                val (valueDate, fundsCode, amountRaw) = match.destructured
                val date = LocalDate.parse(valueDate, VALUE_DATE_FORMAT)
                val magnitude = Money(kotlin.math.abs(Money.parseCommaDecimal(amountRaw).cents))
                val amount = if (fundsCode.startsWith("D")) -magnitude else magnitude

                // Gather the :86: continuation lines that belong to this transaction.
                val infoLines = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && (lines[j].startsWith(TAG_INFO) || isContinuationLine(lines[j]))) {
                    infoLines += lines[j].removePrefix(TAG_INFO)
                    j++
                }
                val info = parseInfoField(infoLines.joinToString(""))

                transactions += ParsedTransaction(
                    accountId = accountId,
                    date = date,
                    amount = amount,
                    counterpartyIban = info["IBAN"],
                    counterpartyName = info["NAME"] ?: "Onbekende tegenpartij",
                    description = buildDescription(info),
                    balanceAfter = null,
                    sourceFormat = SourceFormat.MT940,
                )
                i = j
            } else {
                i++
            }
        }

        return transactions
    }

    /** A raw :86: line's continuation, when the bank wraps a long info field over multiple lines. */
    private fun isContinuationLine(line: String): Boolean =
        line.isNotBlank() && !line.startsWith(":")

    /** "/TRTP/SEPA IDEAL/IBAN/NL.../NAME/Albert Heijn/REMI/Boodschappen/" -> {TRTP=..,IBAN=..,...} */
    private fun parseInfoField(raw: String): Map<String, String> {
        if (!raw.startsWith("/")) return mapOf(RAW_KEY to raw.trim())
        val parts = raw.split("/").filter { it.isNotEmpty() }
        if (parts.size < 2) return mapOf(RAW_KEY to raw.trim())
        return parts.chunked(2)
            .filter { it.size == 2 }
            .associate { (key, value) -> key to value }
    }

    private fun buildDescription(info: Map<String, String>): String {
        info[RAW_KEY]?.takeIf { it.isNotBlank() }?.let { return it }
        val name = info["NAME"]
        val remittance = info["REMI"]
        return listOfNotNull(name, remittance).joinToString(" — ").ifBlank { "Geen omschrijving" }
    }

    companion object {
        private const val TAG_STATEMENT_LINE = ":61:"
        private const val TAG_INFO = ":86:"
        private const val RAW_KEY = "__raw__"

        // YYMMDD, optional MMDD entry date, D or C funds code, amount, rest (type code + refs, ignored).
        private val STATEMENT_LINE_PATTERN = Regex("""(\d{6})(?:\d{4})?([DC])(\d+,\d{2}).*""")
        private val VALUE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
    }
}
