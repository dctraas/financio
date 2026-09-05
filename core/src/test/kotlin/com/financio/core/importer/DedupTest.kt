package com.financio.core.importer

import com.financio.core.model.Money
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DedupTest {

    private fun transaction(amountCents: Long = -2345) = ParsedTransaction(
        accountId = 1,
        date = LocalDate.of(2026, 9, 3),
        amount = Money(amountCents),
        counterpartyIban = "NL34RABO0123456789",
        counterpartyName = "Albert Heijn 1354",
        description = "Albert Heijn 1354 — Pasvolgnr 003",
        balanceAfter = Money(128456),
        sourceFormat = SourceFormat.CSV,
    )

    @Test
    fun `the same transaction imported twice hashes identically`() {
        assertEquals(Dedup.hashOf(transaction()), Dedup.hashOf(transaction()))
    }

    @Test
    fun `a different amount changes the hash`() {
        assertNotEquals(Dedup.hashOf(transaction()), Dedup.hashOf(transaction(amountCents = -2340)))
    }

    @Test
    fun `re-importing the same CSV line via either parser format yields the same hash`() {
        // Same identity fields, parsed from a CSV export vs. an MT940 file of the same statement
        // period — should still be recognized as the same transaction.
        val fromMt940 = transaction().copy(sourceFormat = SourceFormat.MT940)
        assertEquals(Dedup.hashOf(transaction()), Dedup.hashOf(fromMt940))
    }
}
