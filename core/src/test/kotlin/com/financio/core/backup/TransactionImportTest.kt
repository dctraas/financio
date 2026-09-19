package com.financio.core.backup

import com.financio.core.importer.Dedup
import com.financio.core.model.Money
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransactionImportTest {

    private val export = TransactionExport(
        accountIban = "NL••INGB••••••1234",
        date = "2026-09-01",
        amountCents = -2345,
        counterpartyName = "Albert Heijn",
        description = "Boodschappen",
        categoryName = "Boodschappen",
        sourceFormat = "CSV",
    )

    @Test
    fun `resolves the account by iban and the category by name`() {
        val plan = TransactionImport.plan(
            transactions = listOf(export),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = mapOf("Boodschappen" to 10L),
            existingDedupHashesByAccount = emptyMap(),
        )

        assertEquals(1, plan.toInsert.size)
        val planned = plan.toInsert.single()
        assertEquals(1L, planned.transaction.accountId)
        assertEquals(10L, planned.transaction.categoryId)
        assertEquals(LocalDate.of(2026, 9, 1), planned.transaction.date)
        assertTrue(planned.splits.isEmpty())
        assertEquals(0, plan.skippedUnresolvedAccount)
        assertEquals(0, plan.skippedDuplicate)
    }

    @Test
    fun `skips a transaction whose account iban doesn't resolve locally`() {
        val plan = TransactionImport.plan(
            transactions = listOf(export),
            accountIdsByIban = emptyMap(),
            categoryIdsByName = mapOf("Boodschappen" to 10L),
            existingDedupHashesByAccount = emptyMap(),
        )

        assertEquals(0, plan.toInsert.size)
        assertEquals(1, plan.skippedUnresolvedAccount)
    }

    @Test
    fun `leaves an uncategorized export uncategorized when the category name doesn't resolve`() {
        val plan = TransactionImport.plan(
            transactions = listOf(export),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = emptyMap(),
            existingDedupHashesByAccount = emptyMap(),
        )

        assertEquals(1, plan.toInsert.size)
        assertNull(plan.toInsert.single().transaction.categoryId)
    }

    @Test
    fun `skips a transaction already present locally, matched by the same dedup hash the file importer uses`() {
        val parsed = ParsedTransaction(
            accountId = 1L,
            date = LocalDate.of(2026, 9, 1),
            amount = Money(-2345),
            counterpartyIban = null,
            counterpartyName = "Albert Heijn",
            description = "Boodschappen",
            balanceAfter = null,
            sourceFormat = SourceFormat.CSV,
        )
        val existingHash = Dedup.hashOf(parsed)

        val plan = TransactionImport.plan(
            transactions = listOf(export),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = mapOf("Boodschappen" to 10L),
            existingDedupHashesByAccount = mapOf(1L to setOf(existingHash)),
        )

        assertEquals(0, plan.toInsert.size)
        assertEquals(1, plan.skippedDuplicate)
    }

    @Test
    fun `treats the same transaction appearing twice in one file as a duplicate the second time`() {
        val plan = TransactionImport.plan(
            transactions = listOf(export, export),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = mapOf("Boodschappen" to 10L),
            existingDedupHashesByAccount = emptyMap(),
        )

        assertEquals(1, plan.toInsert.size)
        assertEquals(1, plan.skippedDuplicate)
    }

    @Test
    fun `a split transaction resolves its own category to null, splits become authoritative`() {
        val splitExport = export.copy(
            categoryName = null,
            splits = listOf(TransactionSplitExport("Boodschappen", 1500), TransactionSplitExport("Kleding", 845)),
        )
        val plan = TransactionImport.plan(
            transactions = listOf(splitExport),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = mapOf("Boodschappen" to 10L, "Kleding" to 11L),
            existingDedupHashesByAccount = emptyMap(),
        )

        val planned = plan.toInsert.single()
        assertNull(planned.transaction.categoryId)
        assertEquals(2, planned.splits.size)
    }

    @Test
    fun `drops just the one split whose category doesn't resolve, keeping the rest`() {
        val splitExport = export.copy(
            categoryName = null,
            splits = listOf(TransactionSplitExport("Boodschappen", 1500), TransactionSplitExport("Verwijderde categorie", 845)),
        )
        val plan = TransactionImport.plan(
            transactions = listOf(splitExport),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = mapOf("Boodschappen" to 10L),
            existingDedupHashesByAccount = emptyMap(),
        )

        val planned = plan.toInsert.single()
        assertEquals(1, planned.splits.size)
        assertEquals(10L, planned.splits.single().categoryId)
    }

    @Test
    fun `falls back to the transaction's own category when none of its splits resolve`() {
        val splitExport = export.copy(
            categoryName = "Boodschappen",
            splits = listOf(TransactionSplitExport("Verwijderde categorie", 2345)),
        )
        val plan = TransactionImport.plan(
            transactions = listOf(splitExport),
            accountIdsByIban = mapOf("NL••INGB••••••1234" to 1L),
            categoryIdsByName = mapOf("Boodschappen" to 10L),
            existingDedupHashesByAccount = emptyMap(),
        )

        val planned = plan.toInsert.single()
        assertTrue(planned.splits.isEmpty())
        assertEquals(10L, planned.transaction.categoryId)
    }
}
