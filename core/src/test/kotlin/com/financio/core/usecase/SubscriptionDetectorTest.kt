package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SubscriptionDetectorTest {

    private fun txn(name: String, amountCents: Long, date: LocalDate) = Transaction(
        accountId = 1,
        date = date,
        amount = Money(amountCents),
        counterpartyIban = null,
        counterpartyName = name,
        description = name,
        categoryId = null,
        sourceFormat = SourceFormat.CSV,
        dedupHash = "$name-$amountCents-$date",
    )

    private fun monthly(name: String, amountCents: Long, startDate: LocalDate, months: Int) =
        (0 until months).map { txn(name, amountCents, startDate.plusMonths(it.toLong())) }

    @Test
    fun `detects a regular monthly debit with a consistent amount`() {
        val netflix = monthly("Netflix", -1299, LocalDate.of(2026, 1, 15), 4)
        val result = SubscriptionDetector.detect(netflix)

        assertEquals(1, result.size)
        val subscription = result.single()
        assertEquals("Netflix", subscription.counterpartyName)
        assertEquals(Money(-1299), subscription.averageAmount)
        assertEquals(4, subscription.occurrences)
        assertEquals(LocalDate.of(2026, 4, 15), subscription.lastDate)
    }

    @Test
    fun `does not flag fewer than three occurrences`() {
        val twice = monthly("Spotify", -999, LocalDate.of(2026, 1, 1), 2)
        assertTrue(SubscriptionDetector.detect(twice).isEmpty())
    }

    @Test
    fun `does not flag a merchant with wildly varying amounts, even if visited regularly`() {
        val groceries = listOf(
            txn("Albert Heijn", -1200, LocalDate.of(2026, 1, 5)),
            txn("Albert Heijn", -4500, LocalDate.of(2026, 2, 5)),
            txn("Albert Heijn", -800, LocalDate.of(2026, 3, 5)),
            txn("Albert Heijn", -6000, LocalDate.of(2026, 4, 5)),
        )
        assertTrue(SubscriptionDetector.detect(groceries).isEmpty())
    }

    @Test
    fun `does not flag irregular, non-monthly timing even with a consistent amount`() {
        val irregular = listOf(
            txn("Bol.com", -2000, LocalDate.of(2026, 1, 3)),
            txn("Bol.com", -2000, LocalDate.of(2026, 1, 10)),
            txn("Bol.com", -2000, LocalDate.of(2026, 3, 20)),
        )
        assertTrue(SubscriptionDetector.detect(irregular).isEmpty())
    }

    @Test
    fun `ignores credits - a subscription is always a debit`() {
        val credits = monthly("Werkgever", 250000, LocalDate.of(2026, 1, 25), 4)
        assertTrue(SubscriptionDetector.detect(credits).isEmpty())
    }

    @Test
    fun `tolerates one skipped or merged cycle without losing the pattern`() {
        val withGap = listOf(
            txn("Sportschool", -3500, LocalDate.of(2026, 1, 1)),
            txn("Sportschool", -3500, LocalDate.of(2026, 2, 1)),
            // March skipped entirely
            txn("Sportschool", -3500, LocalDate.of(2026, 4, 1)),
            txn("Sportschool", -3500, LocalDate.of(2026, 5, 1)),
        )
        val result = SubscriptionDetector.detect(withGap)
        assertEquals(1, result.size)
    }

    @Test
    fun `estimates the next date from the average monthly gap`() {
        val subscription = SubscriptionDetector.detect(monthly("Netflix", -1299, LocalDate.of(2026, 1, 5), 3)).single()
        // Jan 5 -> Feb 5 (31 days) -> Mar 5 (28 days, 2026 isn't a leap year): average gap 29.5,
        // truncated to 29 whole days added to the last occurrence (Mar 5) -> Apr 3.
        assertEquals(LocalDate.of(2026, 4, 3), subscription.estimatedNextDate)
    }

    @Test
    fun `sorts multiple detected subscriptions by amount descending`() {
        val cheap = monthly("Spotify", -999, LocalDate.of(2026, 1, 1), 3)
        val expensive = monthly("Zorgverzekering", -14995, LocalDate.of(2026, 1, 5), 3)
        val names = SubscriptionDetector.detect(cheap + expensive).map { it.counterpartyName }
        assertEquals(listOf("Zorgverzekering", "Spotify"), names)
    }

    @Test
    fun `a small, gradual price increase still counts as consistent`() {
        val withIncrease = listOf(
            txn("Ziggo", -4500, LocalDate.of(2026, 1, 1)),
            txn("Ziggo", -4500, LocalDate.of(2026, 2, 1)),
            txn("Ziggo", -4700, LocalDate.of(2026, 3, 1)),
            txn("Ziggo", -4700, LocalDate.of(2026, 4, 1)),
        )
        assertEquals(1, SubscriptionDetector.detect(withIncrease).size)
    }
}
