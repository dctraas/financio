package com.financio.core.backup

import com.financio.core.model.Debt
import com.financio.core.model.DebtDirection
import com.financio.core.model.Money
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebtImportTest {

    private val export = DebtExport(
        name = "Lening verbouwing",
        direction = "I_OWE",
        counterpartyName = "Jan",
        principalCents = 500_000,
        currentBalanceCents = 300_000,
        startDate = "2025-01-01",
    )

    @Test
    fun `plans to create a debt not yet known locally`() {
        val plan = DebtImport.plan(debts = listOf(export), existing = emptyList())

        assertEquals(1, plan.toCreate.size)
        assertEquals(0, plan.skippedExisting)
    }

    @Test
    fun `skips a debt whose name already exists locally, leaving it unmodified`() {
        val existing = listOf(
            Debt(
                name = "Lening verbouwing",
                direction = DebtDirection.I_OWE,
                counterpartyName = "Iemand anders",
                principal = Money(1),
                currentBalance = Money(1),
                startDate = LocalDate.of(2020, 1, 1),
            ),
        )
        val plan = DebtImport.plan(debts = listOf(export), existing = existing)

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedExisting)
    }
}
