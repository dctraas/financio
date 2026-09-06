package com.financio.core.backup

import com.financio.core.model.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CategoryImportTest {

    @Test
    fun `plans to create categories that don't exist locally yet`() {
        val plan = CategoryImport.plan(
            categories = listOf(CategoryExport("Boodschappen", "#5B7A52"), CategoryExport("Vervoer", "#4C6E77")),
            existing = emptyList(),
        )

        assertEquals(2, plan.toCreate.size)
        assertEquals(0, plan.skippedExisting)
    }

    @Test
    fun `skips a category whose name already exists, without touching its color`() {
        val existing = listOf(Category(id = 1, name = "Boodschappen", colorHex = "#000000"))
        val plan = CategoryImport.plan(
            categories = listOf(CategoryExport("Boodschappen", "#5B7A52")),
            existing = existing,
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedExisting)
    }

    @Test
    fun `a mix of new and existing names splits accordingly`() {
        val existing = listOf(Category(id = 1, name = "Boodschappen", colorHex = "#5B7A52"))
        val plan = CategoryImport.plan(
            categories = listOf(CategoryExport("Boodschappen", "#5B7A52"), CategoryExport("Overig", "#8B9992")),
            existing = existing,
        )

        assertEquals(listOf(CategoryExport("Overig", "#8B9992")), plan.toCreate)
        assertEquals(1, plan.skippedExisting)
    }
}
