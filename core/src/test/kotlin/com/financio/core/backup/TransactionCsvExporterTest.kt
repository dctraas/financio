package com.financio.core.backup

import com.financio.core.model.Category
import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TransactionCsvExporterTest {

    private fun transaction(
        id: Long = 1,
        date: LocalDate = LocalDate.of(2026, 9, 4),
        counterpartyName: String = "Albert Heijn",
        counterpartyIban: String? = "NL27INGB0000026500",
        description: String = "Betaalautomaat",
        amount: Money = Money(-3872),
        categoryId: Long? = null,
        tag: String? = null,
    ) = Transaction(
        id = id,
        accountId = 1,
        date = date,
        amount = amount,
        counterpartyIban = counterpartyIban,
        counterpartyName = counterpartyName,
        description = description,
        categoryId = categoryId,
        sourceFormat = SourceFormat.CSV,
        dedupHash = "hash-$id",
        tag = tag,
    )

    @Test
    fun `header and a plain row round-trip the exact amount format the importer parses`() {
        val category = Category(id = 1, name = "Boodschappen", colorHex = "#000000")
        val csv = TransactionCsvExporter.export(
            listOf(transaction(categoryId = 1)),
            mapOf(1L to category),
        )
        val lines = csv.lines()
        assertEquals("Datum;Naam;Tegenrekening;Omschrijving;Bedrag;Categorie;Tag", lines[0])
        assertEquals("2026-09-04;Albert Heijn;NL27INGB0000026500;Betaalautomaat;-38,72;Boodschappen;", lines[1])
    }

    @Test
    fun `an uncategorized transaction leaves the categorie column blank`() {
        val csv = TransactionCsvExporter.export(listOf(transaction()), emptyMap())
        assertEquals(";;", csv.lines()[1].takeLast(2)) // trailing "...;Categorie;Tag" both empty
    }

    @Test
    fun `most recent date first`() {
        val csv = TransactionCsvExporter.export(
            listOf(
                transaction(id = 1, date = LocalDate.of(2026, 9, 1)),
                transaction(id = 2, date = LocalDate.of(2026, 9, 4)),
            ),
            emptyMap(),
        )
        val dataLines = csv.lines().drop(1)
        assertEquals("2026-09-04", dataLines[0].substringBefore(";"))
        assertEquals("2026-09-01", dataLines[1].substringBefore(";"))
    }

    @Test
    fun `a description containing the delimiter is quoted, and an embedded quote is doubled`() {
        val csv = TransactionCsvExporter.export(
            listOf(transaction(description = "Term. 12:22; pasnr. \"004\"")),
            emptyMap(),
        )
        assert(csv.lines()[1].contains("\"Term. 12:22; pasnr. \"\"004\"\"\""))
    }

    @Test
    fun `positive amounts have no leading sign beyond the comma-decimal format`() {
        val csv = TransactionCsvExporter.export(listOf(transaction(amount = Money(324000))), emptyMap())
        assert(csv.lines()[1].contains(";3240,00;"))
    }
}
