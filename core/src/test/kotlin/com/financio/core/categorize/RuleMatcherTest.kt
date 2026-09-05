package com.financio.core.categorize

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.Money
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RuleMatcherTest {

    private val groceries = 1L
    private val subscriptions = 2L

    // The exact rule table from the architecture doc.
    private val rules = listOf(
        CategoryRule(categoryId = groceries, matchType = MatchType.COUNTERPARTY_EXACT, pattern = "NL34RABO0123456789", priority = 1),
        CategoryRule(categoryId = groceries, matchType = MatchType.KEYWORD, pattern = "Albert Heijn", priority = 2),
        CategoryRule(categoryId = groceries, matchType = MatchType.KEYWORD, pattern = "Jumbo", priority = 2),
        CategoryRule(categoryId = subscriptions, matchType = MatchType.KEYWORD, pattern = "Netflix", priority = 2),
    )

    private fun transactionFrom(name: String, iban: String? = null) = ParsedTransaction(
        accountId = 1,
        date = LocalDate.of(2026, 9, 3),
        amount = Money(-1000),
        counterpartyIban = iban,
        counterpartyName = name,
        description = "",
        balanceAfter = null,
        sourceFormat = SourceFormat.CSV,
    )

    @Test
    fun `an exact counterparty match wins even over a keyword rule with a lower number`() {
        val txn = transactionFrom(name = "Onbekende winkel", iban = "NL34RABO0123456789")
        assertEquals(groceries, RuleMatcher(rules).categorize(txn))
    }

    @Test
    fun `a keyword rule matches on the merchant name`() {
        val txn = transactionFrom(name = "Albert Heijn 1354")
        assertEquals(groceries, RuleMatcher(rules).categorize(txn))
    }

    @Test
    fun `keyword matching is case-insensitive`() {
        val txn = transactionFrom(name = "netflix.com")
        assertEquals(subscriptions, RuleMatcher(rules).categorize(txn))
    }

    @Test
    fun `no match returns null so the transaction lands in 'te categoriseren'`() {
        val txn = transactionFrom(name = "Bol.com")
        assertNull(RuleMatcher(rules).categorize(txn))
    }
}
