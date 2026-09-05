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
    fun `fails loudly instead of guessing when a required column is missing`() {
        val missingColumn = sampleCsv.replace("Tegenrekening;", "")
        assertThrows(UnrecognizedFormatException::class.java) {
            CsvIngParser().parse(missingColumn, accountId = 1)
        }
    }
}
