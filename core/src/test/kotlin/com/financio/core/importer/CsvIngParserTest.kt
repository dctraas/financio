package com.financio.core.importer

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvIngParserTest {

    // The exact sample from the architecture doc's "Mijn ING rekeningoverzicht" illustration.
    private val sampleCsv = """
        Datum;Naam / Omschrijving;Rekening;Tegenrekening;Code;Af Bij;Bedrag (EUR);Mutatiesoort;Mededelingen;Saldo na mutatie;Tag
        20260903;Albert Heijn 1354;NL12INGB0001234567;NL34RABO0123456789;BA;Af;23,45;Betaalautomaat;Pasvolgnr 003;1284,56;
    """.trimIndent()

    @Test
    fun `parses date, amount, counterparty and description from a debit line`() {
        val transactions = CsvIngParser().parse(sampleCsv, accountId = 1)

        assertEquals(1, transactions.size)
        val txn = transactions.single()
        assertEquals(LocalDate.of(2026, 9, 3), txn.date)
        assertEquals(Money(-2345), txn.amount)
        assertEquals("NL34RABO0123456789", txn.counterpartyIban)
        assertEquals("Albert Heijn 1354", txn.counterpartyName)
        assertEquals("Albert Heijn 1354 — Pasvolgnr 003", txn.description)
        assertEquals(Money(128456), txn.balanceAfter)
        assertEquals(SourceFormat.CSV, txn.sourceFormat)
    }

    @Test
    fun `a credit line produces a positive amount`() {
        val credit = sampleCsv.replace("Af;23,45", "Bij;23,45")
        val txn = CsvIngParser().parse(credit, accountId = 1).single()
        assertEquals(Money(2345), txn.amount)
    }

    @Test
    fun `still parses correctly when ING reorders the columns`() {
        val reordered = """
            Tegenrekening;Datum;Bedrag (EUR);Naam / Omschrijving;Af Bij;Mededelingen;Saldo na mutatie
            NL34RABO0123456789;20260903;23,45;Albert Heijn 1354;Af;Pasvolgnr 003;1284,56
        """.trimIndent()

        val txn = CsvIngParser().parse(reordered, accountId = 1).single()
        assertEquals(Money(-2345), txn.amount)
        assertEquals("NL34RABO0123456789", txn.counterpartyIban)
    }

    @Test
    fun `reads the Tag column when present and non-blank`() {
        val tagged = sampleCsv.replace("1284,56;", "1284,56;Verjaardag")
        val txn = CsvIngParser().parse(tagged, accountId = 1).single()
        assertEquals("Verjaardag", txn.tag)
    }

    @Test
    fun `tag is null when the column is blank`() {
        val txn = CsvIngParser().parse(sampleCsv, accountId = 1).single()
        assertEquals(null, txn.tag)
    }

    @Test
    fun `tag is null, not an error, when the whole column is missing - it's optional, not required`() {
        val withoutTagColumn = sampleCsv.lines().let { lines ->
            lines.mapIndexed { index, line -> if (index == 0) line.removeSuffix(";Tag") else line.trimEnd(';') }
        }.joinToString("\n")

        val txn = CsvIngParser().parse(withoutTagColumn, accountId = 1).single()
        assertEquals(null, txn.tag)
    }

    @Test
    fun `fails loudly instead of guessing when a required column is missing`() {
        val missingColumn = sampleCsv.replace("Tegenrekening;", "")
        assertThrows(UnrecognizedFormatException::class.java) {
            CsvIngParser().parse(missingColumn, accountId = 1)
        }
    }

    @Test
    fun `a missing-column failure carries the raw header and first lines for the error screen`() {
        val missingDateColumn = sampleCsv.replace("Datum;", "")
        val exception = assertThrows(UnrecognizedFormatException::class.java) {
            CsvIngParser().parse(missingDateColumn, accountId = 1)
        }
        assertEquals(2, exception.rawLines.size)
        assertTrue(exception.detectedColumns.contains("Naam / Omschrijving"))
        assertTrue(!exception.detectedColumns.contains("Datum"))
    }

    @Test
    fun `a manually picked date column index recovers from an unrecognized date column name`() {
        val renamedDateColumn = sampleCsv.replace("Datum;", "Transactiedatum;")
        // "Transactiedatum" is column 0, same position "Datum" would have been.
        val txn = CsvIngParser().parse(renamedDateColumn, accountId = 1, dateColumnOverrideIndex = 0).single()
        assertEquals(LocalDate.of(2026, 9, 3), txn.date)
    }

    @Test
    fun `other missing columns still fail even with a date column override given`() {
        val missingTwoColumns = sampleCsv.replace("Datum;", "Transactiedatum;").replace("Tegenrekening;", "")
        assertThrows(UnrecognizedFormatException::class.java) {
            CsvIngParser().parse(missingTwoColumns, accountId = 1, dateColumnOverrideIndex = 0)
        }
    }

    @Test
    fun `tab-delimited input is still recognized (defensive - seen from a garbled paste, not a real export)`() {
        // An earlier bug report of a real export appeared tab-delimited, which turned out to be
        // a copy-paste artifact rather than the file's actual format (see the RFC 4180-quoted
        // test below for what a real export looks like). Delimiter auto-detection is kept
        // regardless, since it's harmless and costs nothing if a genuinely tab-separated paste
        // shows up again.
        val tabDelimited = listOf(
            listOf("Datum", "Naam / Omschrijving", "Rekening", "Tegenrekening", "Code", "Af Bij", "Bedrag (EUR)", "Mutatiesoort", "Mededelingen", "Saldo na mutatie", "Tag"),
            listOf("20260904", "Nettorama a.onderweg GORINCHEM", "NL63INGB0663396727", "", "BA", "Af", "30,63", "Betaalautomaat", "Kaartnr: 5238 53** **** 8897", "1876,54", ""),
        ).joinToString("\n") { it.joinToString("\t") }

        val txn = CsvIngParser().parse(tabDelimited, accountId = 1).single()
        assertEquals(LocalDate.of(2026, 9, 4), txn.date)
        assertEquals(Money(-3063), txn.amount)
        assertEquals(Money(187654), txn.balanceAfter)
        assertEquals(null, txn.counterpartyIban) // Tegenrekening blank for card payments
        assertEquals("Nettorama a.onderweg GORINCHEM", txn.counterpartyName)
    }

    @Test
    fun `parses a savings-account export whose header spells the amount and description columns differently`() {
        // A real "Oranje Spaarrekening" export: "Omschrijving" instead of "Naam / Omschrijving",
        // "Bedrag" instead of "Bedrag (EUR)", and an ISO-formatted date - none of which the
        // checking-account format above uses. Reported as an import failure where the error
        // screen's "is this the date column?" recovery did nothing, because the date column was
        // never the actual problem.
        val savingsAccountCsv = """
            "Datum";"Omschrijving";"Rekening";"Rekening naam";"Tegenrekening";"Af Bij";"Bedrag";"Valuta";"Mutatiesoort";"Mededelingen";"Saldo na mutatie"
            "2026-09-15";"Overboeking naar betaalrekening NL14INGB0008028652";"L866-14401";"Oranje Spaarrekening";"NL14INGB0008028652";"Af";"200,00";"EUR";"Opname";"";"8387,49"
        """.trimIndent()

        val txn = CsvIngParser().parse(savingsAccountCsv, accountId = 1).single()
        assertEquals(LocalDate.of(2026, 9, 15), txn.date)
        assertEquals(Money(-20000), txn.amount)
        assertEquals("NL14INGB0008028652", txn.counterpartyIban)
        assertEquals("Overboeking naar betaalrekening NL14INGB0008028652", txn.counterpartyName)
        assertEquals(Money(838749), txn.balanceAfter)
    }

    @Test
    fun `also accepts an ISO-formatted date column, not just yyyyMMdd`() {
        val isoDated = sampleCsv.replace("20260903", "2026-09-03")
        val txn = CsvIngParser().parse(isoDated, accountId = 1).single()
        assertEquals(LocalDate.of(2026, 9, 3), txn.date)
    }

    @Test
    fun `an unparseable date value fails loudly rather than crashing with a raw exception`() {
        val badDate = sampleCsv.replace("20260903", "03-09-2026")
        assertThrows(UnrecognizedFormatException::class.java) {
            CsvIngParser().parse(badDate, accountId = 1)
        }
    }

    @Test
    fun `parses a real RFC 4180-quoted, semicolon-delimited ING export`() {
        // This is the actual "Mijn ING" export format, confirmed against a real download copied
        // straight from the source: every field wrapped in double quotes, ";" as the delimiter.
        // IBAN and card details anonymized; everything else (including the Mededelingen content)
        // taken verbatim.
        val quoted = """
            "Datum";"Naam / Omschrijving";"Rekening";"Tegenrekening";"Code";"Af Bij";"Bedrag (EUR)";"Mutatiesoort";"Mededelingen";"Saldo na mutatie";"Tag"
            "20260904";"Nettorama a.onderweg GORINCHEM";"NL63INGB0663396727";"";"BA";"Af";"30,63";"Betaalautomaat";"Kaartnr: 5238 53** **** 8897 Datum: 04-09-2026 Tijd: 10:00 Transactie: I96213 Term: 1BGD9H Apple Pay Valutadatum: 04-09-2026";"1876,54";""
        """.trimIndent()

        val txn = CsvIngParser().parse(quoted, accountId = 1).single()
        assertEquals(LocalDate.of(2026, 9, 4), txn.date)
        assertEquals(Money(-3063), txn.amount)
        assertEquals(Money(187654), txn.balanceAfter)
        assertEquals(null, txn.counterpartyIban) // Tegenrekening blank for card payments
        assertEquals("Nettorama a.onderweg GORINCHEM", txn.counterpartyName)
        assertEquals(
            "Nettorama a.onderweg GORINCHEM — Kaartnr: 5238 53** **** 8897 Datum: 04-09-2026 Tijd: 10:00 Transactie: I96213 Term: 1BGD9H Apple Pay Valutadatum: 04-09-2026",
            txn.description,
        )
    }
}
