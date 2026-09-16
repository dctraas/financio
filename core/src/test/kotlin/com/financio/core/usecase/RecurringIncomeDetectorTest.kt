package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecurringIncomeDetectorTest {

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
    fun `detects a regular monthly salary credit`() {
        val salary = monthly("Werkgever BV", 285000, LocalDate.of(2026, 1, 25), 4)
        val result = RecurringIncomeDetector.detect(salary)

        assertEquals(1, result.size)
        val income = result.single()
        assertEquals("Werkgever BV", income.counterpartyName)
        assertEquals(Money(285000), income.averageAmount)
        assertEquals(LocalDate.of(2026, 5, 25), income.estimatedNextDate)
    }

    @Test
    fun `ignores debits entirely`() {
        val expenses = monthly("Netflix", -1299, LocalDate.of(2026, 1, 15), 4)
        assertTrue(RecurringIncomeDetector.detect(expenses).isEmpty())
    }

    @Test
    fun `does not flag fewer than three occurrences`() {
        val twice = monthly("Werkgever BV", 285000, LocalDate.of(2026, 1, 25), 2)
        assertTrue(RecurringIncomeDetector.detect(twice).isEmpty())
    }

    @Test
    fun `does not flag wildly varying credit amounts`() {
        val varied = listOf(
            txn("Klant X", 50000, LocalDate.of(2026, 1, 5)),
            txn("Klant X", 200000, LocalDate.of(2026, 2, 5)),
            txn("Klant X", 15000, LocalDate.of(2026, 3, 5)),
        )
        assertTrue(RecurringIncomeDetector.detect(varied).isEmpty())
    }
}
