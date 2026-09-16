package com.financio.core.usecase

import com.financio.core.model.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BalanceForecastCalculatorTest {

    @Test
    fun `first point is today, unprojected, at the current balance`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(100_00),
            today = LocalDate.of(2026, 9, 4),
            averageDailySpend = Money(10_00),
            upcomingSubscriptions = emptyList(),
        )
        assertEquals(LocalDate.of(2026, 9, 4), result.points.first().date)
        assertEquals(Money(100_00), result.points.first().balance)
        assertEquals(false, result.points.first().isProjected)
    }

    @Test
    fun `covers every day through the end of the month, all projected after today`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(100_00),
            today = LocalDate.of(2026, 9, 28),
            averageDailySpend = Money(1_00),
            upcomingSubscriptions = emptyList(),
        )
        assertEquals(LocalDate.of(2026, 9, 30), result.points.last().date)
        assertEquals(3, result.points.size) // 28th (today), 29th, 30th
        assertEquals(listOf(false, true, true), result.points.map { it.isProjected })
    }

    @Test
    fun `each day declines by exactly the average daily spend with no subscriptions`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(100_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(5_00),
            upcomingSubscriptions = emptyList(),
        )
        assertEquals(Money(95_00), result.points[1].balance)
        assertEquals(Money(90_00), result.points[2].balance)
    }

    @Test
    fun `a subscription charge lands entirely on its own date, on top of the daily spend`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(100_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(1_00),
            upcomingSubscriptions = listOf(LocalDate.of(2026, 9, 3) to Money(20_00)),
        )
        // 9/2: -1. 9/3: -1 and -20 the same day.
        assertEquals(Money(99_00), result.points[1].balance)
        assertEquals(Money(78_00), result.points[2].balance)
        assertEquals(Money(77_00), result.points[3].balance)
    }

    @Test
    fun `multiple subscriptions on the same day are summed`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(100_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money.ZERO,
            upcomingSubscriptions = listOf(
                LocalDate.of(2026, 9, 2) to Money(10_00),
                LocalDate.of(2026, 9, 2) to Money(15_00),
            ),
        )
        assertEquals(Money(75_00), result.points[1].balance)
    }

    @Test
    fun `tightDate is the first projected day the balance reaches zero or below`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(25_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(10_00),
            upcomingSubscriptions = emptyList(),
        )
        // 9/2: 15, 9/3: 5, 9/4: -5 -> first non-positive projected day
        assertEquals(LocalDate.of(2026, 9, 4), result.tightDate)
    }

    @Test
    fun `tightDate is null when the balance never dips to zero this month`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(10_000_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(1_00),
            upcomingSubscriptions = emptyList(),
        )
        assertNull(result.tightDate)
    }

    @Test
    fun `upcoming income lands entirely on its own date, added on top of the daily spend`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(100_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(1_00),
            upcomingSubscriptions = emptyList(),
            upcomingIncome = listOf(LocalDate.of(2026, 9, 3) to Money(200_00)),
        )
        // 9/2: -1. 9/3: -1 and +200 the same day.
        assertEquals(Money(99_00), result.points[1].balance)
        assertEquals(Money(298_00), result.points[2].balance)
        assertEquals(Money(297_00), result.points[3].balance)
    }

    @Test
    fun `expected income before month-end keeps a low early-month balance from projecting as still negative`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(50_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(10_00),
            upcomingSubscriptions = emptyList(),
            upcomingIncome = listOf(LocalDate.of(2026, 9, 4) to Money(3_000_00)),
        )
        // Without the income, this would hit zero on 9/6 (50 - 5*10 = 0, tightDate) and go negative
        // from there. With the salary landing on 9/4, it stays comfortably positive all month.
        assertNull(result.tightDate)
        val afterPayday = result.points.first { it.date == LocalDate.of(2026, 9, 6) }
        assertTrue(afterPayday.balance.cents > 0)
    }

    @Test
    fun `an already-negative balance today does not itself count as tightDate, only a projected day does`() {
        val result = BalanceForecastCalculator.forecast(
            currentBalance = Money(-5_00),
            today = LocalDate.of(2026, 9, 1),
            averageDailySpend = Money(1_00),
            upcomingSubscriptions = emptyList(),
        )
        // Today itself is unprojected and excluded from the tightDate search by design; the next
        // (projected) day is already further negative, so it becomes tightDate instead.
        assertEquals(LocalDate.of(2026, 9, 2), result.tightDate)
    }
}
