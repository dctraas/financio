package com.financio.core.model

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebtTest {

    private fun debt(principal: Long, currentBalance: Long) = Debt(
        name = "Lening",
        direction = DebtDirection.I_OWE,
        counterpartyName = "Jan",
        principal = Money(principal),
        currentBalance = Money(currentBalance),
        startDate = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `a brand new debt is 0 percent paid off`() {
        val d = debt(100_000, 100_000)
        assertEquals(Money(0), d.paidOff)
        assertEquals(0, d.percentagePaidOff)
        assertFalse(d.settled)
    }

    @Test
    fun `a partially paid debt reports its progress`() {
        val d = debt(100_000, 25_000)
        assertEquals(Money(75_000), d.paidOff)
        assertEquals(75, d.percentagePaidOff)
        assertFalse(d.settled)
    }

    @Test
    fun `a fully paid debt is settled at 100 percent`() {
        val d = debt(100_000, 0)
        assertEquals(100, d.percentagePaidOff)
        assertTrue(d.settled)
    }

    @Test
    fun `an overpaid debt still reports settled and caps at 100 percent`() {
        val d = debt(100_000, -500)
        assertEquals(100, d.percentagePaidOff)
        assertTrue(d.settled)
    }

    @Test
    fun `a zero-principal debt never divides by zero`() {
        val d = debt(0, 0)
        assertEquals(0, d.percentagePaidOff)
        assertFalse(d.settled)
    }
}
