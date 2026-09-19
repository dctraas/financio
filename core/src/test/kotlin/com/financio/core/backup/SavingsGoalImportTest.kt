package com.financio.core.backup

import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SavingsGoalImportTest {

    @Test
    fun `plans to create a goal not yet known locally`() {
        val plan = SavingsGoalImport.plan(
            goals = listOf(SavingsGoalExport(name = "Vakantie", targetAmountCents = 100_000, categoryName = "Sparen")),
            categoryIdsByName = mapOf("Sparen" to 1L),
            existing = emptyList(),
        )

        assertEquals(1, plan.toCreate.size)
        assertEquals(0, plan.skippedExisting)
        assertEquals(0, plan.skippedUnresolvedCategory)
    }

    @Test
    fun `skips a goal with a name that already exists locally`() {
        val existing = listOf(SavingsGoal(name = "Vakantie", targetAmount = Money(50_000), categoryId = 1))
        val plan = SavingsGoalImport.plan(
            goals = listOf(SavingsGoalExport(name = "Vakantie", targetAmountCents = 100_000, categoryName = "Sparen")),
            categoryIdsByName = mapOf("Sparen" to 1L),
            existing = existing,
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedExisting)
    }

    @Test
    fun `skips a goal whose category name doesn't resolve locally`() {
        val plan = SavingsGoalImport.plan(
            goals = listOf(SavingsGoalExport(name = "Vakantie", targetAmountCents = 100_000, categoryName = "Onbekend")),
            categoryIdsByName = mapOf("Sparen" to 1L),
            existing = emptyList(),
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedUnresolvedCategory)
    }
}
