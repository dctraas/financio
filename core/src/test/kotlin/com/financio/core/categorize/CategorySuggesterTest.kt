package com.financio.core.categorize

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CategorySuggesterTest {

    private val groceries = 1L
    private val transport = 2L

    private fun transaction(name: String, categoryId: Long?) = Transaction(
        accountId = 1,
        date = LocalDate.of(2026, 9, 3),
        amount = Money(-1000),
        counterpartyIban = null,
        counterpartyName = name,
        description = "",
        categoryId = categoryId,
        sourceFormat = SourceFormat.CSV,
        dedupHash = name,
    )

    @Test
    fun `suggests the category of a similarly-named counterparty over the globally most-used one`() {
        val history = listOf(
            transaction("Albert Heijn 1354", groceries),
            transaction("NS Groep", transport),
            transaction("NS Groep", transport),
            transaction("NS Groep", transport),
        )
        val ranked = CategorySuggester.rank(history, "Albert Heijn 2841")
        assertEquals(groceries, ranked.first().categoryId)
        assertTrue(ranked.first().isSimilarityBased)
    }

    @Test
    fun `falls back to plain frequency when no counterparty shares a word with the new one`() {
        val history = listOf(
            transaction("Albert Heijn", groceries),
            transaction("NS Groep", transport),
            transaction("NS Groep", transport),
        )
        val ranked = CategorySuggester.rank(history, "Volkomen Onbekende Winkel")
        assertEquals(transport, ranked.first().categoryId)
        assertEquals(2, ranked.first().matchingTransactionCount)
        assertTrue(!ranked.first().isSimilarityBased)
    }

    @Test
    fun `ranks every category with a similarity match, not just the top one`() {
        val history = listOf(
            transaction("Bakker Jansen", groceries),
            transaction("Bakker Jansen", groceries),
            transaction("Bakker de Vries", transport),
        )
        val ranked = CategorySuggester.rank(history, "Bakker Pietersen")
        assertEquals(listOf(groceries, transport), ranked.map { it.categoryId })
    }

    @Test
    fun `an uncategorized transaction never contributes a suggestion`() {
        val history = listOf(transaction("Albert Heijn 1354", categoryId = null))
        assertTrue(CategorySuggester.rank(history, "Albert Heijn 2841").isEmpty())
    }

    @Test
    fun `no history at all yields no suggestions`() {
        assertTrue(CategorySuggester.rank(emptyList(), "Albert Heijn").isEmpty())
    }
}
