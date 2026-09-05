package com.financio.core.importer

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class Mt940ParserTest {

    // The exact sample from the architecture doc's MT940 illustration.
    private val sampleStatement = """
        :20:REF1234
        :25:NL12INGB0001234567
        :28C:00001/001
        :60F:C260901EUR1308,01
        :61:2609030903D23,45N654NONREF
        :86:/TRTP/SEPA IDEAL/IBAN/NL34RABO0123456789/NAME/Albert Heijn 1354/REMI/Boodschappen/
        :62F:C260903EUR1284,56
    """.trimIndent()

    @Test
    fun `parses the value date, debit amount and counterparty from the 86 field`() {
        val transactions = Mt940Parser().parse(sampleStatement, accountId = 1)

        assertEquals(1, transactions.size)
        val txn = transactions.single()
        assertEquals(LocalDate.of(2026, 9, 3), txn.date)
        assertEquals(Money(-2345), txn.amount)
        assertEquals("NL34RABO0123456789", txn.counterpartyIban)
        assertEquals("Albert Heijn 1354", txn.counterpartyName)
        assertEquals("Albert Heijn 1354 — Boodschappen", txn.description)
        assertEquals(SourceFormat.MT940, txn.sourceFormat)
    }

    @Test
    fun `a credit funds code produces a positive amount`() {
        val credit = sampleStatement.replace(":61:2609030903D23,45", ":61:2609030903C23,45")
        val txn = Mt940Parser().parse(credit, accountId = 1).single()
        assertEquals(Money(2345), txn.amount)
    }

    @Test
    fun `falls back to raw text when the 86 field isn't key-value shaped`() {
        val freeform = sampleStatement.replace(
            ":86:/TRTP/SEPA IDEAL/IBAN/NL34RABO0123456789/NAME/Albert Heijn 1354/REMI/Boodschappen/",
            ":86:Periodieke overschrijving huur september",
        )
        val txn = Mt940Parser().parse(freeform, accountId = 1).single()
        assertEquals("Periodieke overschrijving huur september", txn.description)
        assertEquals(null, txn.counterpartyIban)
    }

    @Test
    fun `handles multiple statement lines in one file`() {
        val two = sampleStatement + "\n:61:2604030403D18,00N654NONREF\n:86:/NAME/Jumbo/REMI/Boodschappen/"
        val transactions = Mt940Parser().parse(two, accountId = 1)
        assertEquals(2, transactions.size)
        assertEquals("Jumbo", transactions[1].counterpartyName)
    }
}
