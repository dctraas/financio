package com.financio.core.importer

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
