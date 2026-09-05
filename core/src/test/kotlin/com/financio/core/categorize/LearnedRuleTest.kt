package com.financio.core.categorize

import com.financio.core.model.MatchType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LearnedRuleTest {

    @Test
    fun `builds a keyword rule at the learned-rule priority`() {
        val rule = LearnedRule.from(categoryId = 3, counterpartyName = "Bol.com")

        assertEquals(3L, rule.categoryId)
        assertEquals(MatchType.KEYWORD, rule.matchType)
        assertEquals("Bol.com", rule.pattern)
        assertEquals(LearnedRule.PRIORITY, rule.priority)
    }

    @Test
    fun `a subsequent transaction from the same merchant now matches via RuleMatcher`() {
        val rule = LearnedRule.from(categoryId = 3, counterpartyName = "Bol.com")
        val matcher = RuleMatcher(listOf(rule))

        val nextPurchase = com.financio.core.model.ParsedTransaction(
            accountId = 1,
            date = java.time.LocalDate.of(2026, 10, 1),
            amount = com.financio.core.model.Money(-1999),
            counterpartyIban = null,
            counterpartyName = "Bol.com",
            description = "",
            balanceAfter = null,
            sourceFormat = com.financio.core.model.SourceFormat.CSV,
        )

        assertEquals(3L, matcher.categorize(nextPurchase))
    }
}
