package com.financio.core.usecase

import com.financio.core.model.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SafeToSpendCalculatorTest {

    @Test
    fun `subtracts upcoming commitments from the current balance`() {
        val result = SafeToSpendCalculator.calculate(
            currentBalance = Money(100000),
            upcomingCommitments = Money(20000),
            today = LocalDate.of(2026, 9, 20),
        )
        assertEquals(Money(80000), result.safeToSpendTotal)
    }

    @Test
    fun `divides evenly over the days remaining in the month, inclusive of today`() {
        // September has 30 days; from the 20th (inclusive) that's 11 days left (20..30).
        val result = SafeToSpendCalculator.calculate(
            currentBalance = Money(110000),
            upcomingCommitments = Money(0),
            today = LocalDate.of(2026, 9, 20),
        )
        assertEquals(11, result.daysRemaining)
        assertEquals(Money(10000), result.safeToSpendPerDay)
    }

    @Test
    fun `never goes negative even when commitments exceed the balance`() {
        val result = SafeToSpendCalculator.calculate(
            currentBalance = Money(5000),
            upcomingCommitments = Money(20000),
            today = LocalDate.of(2026, 9, 1),
        )
        assertEquals(Money.ZERO, result.safeToSpendTotal)
        assertEquals(Money.ZERO, result.safeToSpendPerDay)
    }

    @Test
    fun `the last day of the month still counts as one full day remaining`() {
        val result = SafeToSpendCalculator.calculate(
            currentBalance = Money(5000),
            upcomingCommitments = Money(0),
            today = LocalDate.of(2026, 9, 30),
        )
        assertEquals(1, result.daysRemaining)
    }
}
