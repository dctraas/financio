package com.financio.core.categorize

import com.financio.core.model.MatchType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManualRuleTest {

    @Test
    fun `creates a rule with the manual priority and given match type`() {
        val rule = ManualRule.from(categoryId = 7, matchType = MatchType.COUNTERPARTY_EXACT, pattern = "NL12INGB0001234567")

        assertEquals(7, rule.categoryId)
        assertEquals(MatchType.COUNTERPARTY_EXACT, rule.matchType)
        assertEquals("NL12INGB0001234567", rule.pattern)
        assertEquals(ManualRule.PRIORITY, rule.priority)
    }

    @Test
    fun `outranks both default and learned rules`() {
        assertTrue(ManualRule.PRIORITY < DefaultCategorization.PRIORITY)
        assertTrue(ManualRule.PRIORITY < LearnedRule.PRIORITY)
    }
}
