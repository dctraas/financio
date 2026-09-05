package com.financio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoneyTest {

    @Test
    fun `parses a simple comma-decimal amount`() {
        assertEquals(2345L, Money.parseCommaDecimal("23,45").cents)
    }

    @Test
    fun `parses an amount with a thousands separator`() {
        assertEquals(128456L, Money.parseCommaDecimal("1.284,56").cents)
    }

    @Test
    fun `parses a negative amount`() {
        assertEquals(-2345L, Money.parseCommaDecimal("-23,45").cents)
    }

    @Test
    fun `renders whole numbers of cents back as Dutch currency`() {
        assertEquals("€23,45", Money(2345).toDisplayString())
        assertEquals("-€23,45", Money(-2345).toDisplayString())
        assertEquals("€1.284,56", Money(128456).toDisplayString())
    }

    @Test
    fun `adding cents never drifts the way floating point would`() {
        var total = Money.ZERO
        repeat(1000) { total += Money.parseCommaDecimal("0,10") }
        // 1000 * 10 cents = exactly 10000 cents. A Double accumulator would not land here exactly.
        assertEquals(10_000L, total.cents)
    }
}
