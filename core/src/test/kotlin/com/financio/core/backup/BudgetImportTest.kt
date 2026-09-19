package com.financio.core.backup

import com.financio.core.model.Budget
import com.financio.core.model.Money
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BudgetImportTest {

    @Test
    fun `resolves a budget's category by name and plans to create it`() {
        val plan = BudgetImport.plan(
            budgets = listOf(BudgetExport("Boodschappen", "2026-09", 30000)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = emptyList(),
        )

        assertEquals(1, plan.toCreate.size)
        val resolved = plan.toCreate.single()
        assertEquals(1L, resolved.categoryId)
        assertEquals(YearMonth.of(2026, 9), resolved.yearMonth)
        assertEquals(0, plan.skippedUnresolvedCategory)
        assertEquals(0, plan.skippedExisting)
    }

    @Test
    fun `skips a budget whose category name doesn't resolve locally`() {
        val plan = BudgetImport.plan(
            budgets = listOf(BudgetExport("Onbekend", "2026-09", 30000)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = emptyList(),
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedUnresolvedCategory)
    }

    @Test
    fun `leaves an existing category+month budget untouched instead of overwriting a hand-tuned limit`() {
        val existing = listOf(Budget(categoryId = 1, yearMonth = YearMonth.of(2026, 9), limit = Money(10000)))
        val plan = BudgetImport.plan(
            budgets = listOf(BudgetExport("Boodschappen", "2026-09", 30000)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = existing,
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedExisting)
    }

    @Test
    fun `the same category in a different month is not treated as existing`() {
        val existing = listOf(Budget(categoryId = 1, yearMonth = YearMonth.of(2026, 8), limit = Money(10000)))
        val plan = BudgetImport.plan(
            budgets = listOf(BudgetExport("Boodschappen", "2026-09", 30000)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = existing,
        )

        assertEquals(1, plan.toCreate.size)
    }
}
