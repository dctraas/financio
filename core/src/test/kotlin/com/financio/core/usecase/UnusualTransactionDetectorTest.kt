package com.financio.core.usecase

import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnusualTransactionDetectorTest {

    private fun tx(counterparty: String, cents: Long, date: LocalDate = LocalDate.of(2026, 1, 1)) = Transaction(
        accountId = 1,
        date = date,
        amount = Money(cents),
        counterpartyIban = null,
        counterpartyName = counterparty,
        description = "",
        categoryId = null,
        sourceFormat = SourceFormat.CSV,
        dedupHash = "$counterparty-$cents-$date",
    )

    @Test
    fun `flags a debit far above the counterparty's usual amount`() {
        val history = listOf(tx("Albert Heijn", -4500), tx("Albert Heijn", -5000), tx("Albert Heijn", -4800))
        val newTransaction = tx("Albert Heijn", -45000)
        assertTrue(UnusualTransactionDetector.isUnusual(newTransaction, history))
    }

    @Test
    fun `does not flag an amount in line with the counterparty's usual range`() {
        val history = listOf(tx("Albert Heijn", -4500), tx("Albert Heijn", -5000), tx("Albert Heijn", -4800))
        val newTransaction = tx("Albert Heijn", -5200)
        assertFalse(UnusualTransactionDetector.isUnusual(newTransaction, history))
    }

    @Test
    fun `never flags income, however large`() {
        val history = listOf(tx("Werkgever", 250000), tx("Werkgever", 250000), tx("Werkgever", 250000))
        val newTransaction = tx("Werkgever", 900000)
        assertFalse(UnusualTransactionDetector.isUnusual(newTransaction, history))
    }

    @Test
    fun `a brand new counterparty with no history is never flagged`() {
        assertFalse(UnusualTransactionDetector.isUnusual(tx("Nieuwe Winkel", -99900), emptyList()))
    }

    @Test
    fun `too little history to have a baseline is never flagged`() {
        val history = listOf(tx("Albert Heijn", -4500), tx("Albert Heijn", -5000))
        assertFalse(UnusualTransactionDetector.isUnusual(tx("Albert Heijn", -45000), history))
    }

    @Test
    fun `findUnusual folds each accepted candidate into the running history for the next one`() {
        val history = listOf(tx("Albert Heijn", -4500), tx("Albert Heijn", -5000), tx("Albert Heijn", -4800))
        val candidates = listOf(tx("Albert Heijn", -45000, LocalDate.of(2026, 1, 2)), tx("Bol.com", -3000, LocalDate.of(2026, 1, 2)))
        val unusual = UnusualTransactionDetector.findUnusual(candidates, history)
        assertTrue(unusual.size == 1 && unusual.single().counterpartyName == "Albert Heijn")
    }
}
