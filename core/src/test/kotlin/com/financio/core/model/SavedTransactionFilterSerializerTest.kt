package com.financio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SavedTransactionFilterSerializerTest {

    @Test
    fun `round-trips a list of filters`() {
        val filters = listOf(
            SavedTransactionFilter(id = 1, name = "Boodschappen", categoryId = 5, minAmountCents = 1000),
            SavedTransactionFilter(
                id = 2,
                name = "Grote uitgaven",
                searchQuery = "albert",
                uncategorizedOnly = true,
                maxAmountCents = 5000,
                dateFrom = "2026-01-01",
                dateTo = "2026-01-31",
                pinnedOnVandaag = true,
            ),
        )

        val decoded = SavedTransactionFilterSerializer.decode(SavedTransactionFilterSerializer.encode(filters))

        assertEquals(filters, decoded)
    }

    @Test
    fun `blank input decodes to an empty list`() {
        assertTrue(SavedTransactionFilterSerializer.decode("").isEmpty())
        assertTrue(SavedTransactionFilterSerializer.decode("   ").isEmpty())
    }

    @Test
    fun `corrupted input decodes to an empty list instead of throwing`() {
        assertTrue(SavedTransactionFilterSerializer.decode("{not json").isEmpty())
    }
}
