package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UncategorizedGroupTest {

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

    @Test
    fun `groups transactions by counterparty name`() {
        val transactions = listOf(
            txn("Nettorama", -3063, LocalDate.of(2026, 9, 1)),
            txn("Nettorama", -1200, LocalDate.of(2026, 9, 4)),
            txn("Shell", -5000, LocalDate.of(2026, 9, 2)),
        )

        val groups = transactions.groupForReview()

        assertEquals(2, groups.size)
        val nettorama = groups.first { it.counterpartyName == "Nettorama" }
        assertEquals(2, nettorama.count)
        assertEquals(Money(-4263), nettorama.totalAmount)
        assertEquals(LocalDate.of(2026, 9, 1), nettorama.firstDate)
        assertEquals(LocalDate.of(2026, 9, 4), nettorama.lastDate)
    }

    @Test
    fun `min and max amount reflect the actual smallest and largest cents value`() {
        val transactions = listOf(
            txn("Nettorama", -3063, LocalDate.of(2026, 9, 1)),
            txn("Nettorama", -1200, LocalDate.of(2026, 9, 4)),
        )
        val group = transactions.groupForReview().single()
        assertEquals(Money(-3063), group.minAmount)
        assertEquals(Money(-1200), group.maxAmount)
    }

    @Test
    fun `sorts groups by total absolute amount descending`() {
        val transactions = listOf(
            txn("Kleine Winkel", -500, LocalDate.of(2026, 9, 1)),
            txn("Grote Uitgave", -20000, LocalDate.of(2026, 9, 2)),
            txn("Middelgroot", -5000, LocalDate.of(2026, 9, 3)),
        )

        val names = transactions.groupForReview().map { it.counterpartyName }

        assertEquals(listOf("Grote Uitgave", "Middelgroot", "Kleine Winkel"), names)
    }

    @Test
    fun `many small transactions from the same merchant still outrank one bigger one-off if the total is higher`() {
        val transactions = listOf(
            txn("Koffietentje", -300, LocalDate.of(2026, 9, 1)),
            txn("Koffietentje", -300, LocalDate.of(2026, 9, 2)),
            txn("Koffietentje", -300, LocalDate.of(2026, 9, 3)),
            txn("Koffietentje", -300, LocalDate.of(2026, 9, 4)),
            txn("Eenmalige Aankoop", -1000, LocalDate.of(2026, 9, 5)),
        )

        val names = transactions.groupForReview().map { it.counterpartyName }

        assertEquals(listOf("Koffietentje", "Eenmalige Aankoop"), names)
    }
}
