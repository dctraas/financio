package com.financio.core.backup

import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleImportTest {

    @Test
    fun `resolves a rule's category by name and plans to create it`() {
        val plan = RuleImport.plan(
            rules = listOf(RuleExport("Boodschappen", "KEYWORD", "Albert Heijn", 20)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = emptyList(),
        )

        assertEquals(1, plan.toCreate.size)
        val created = plan.toCreate.single()
        assertEquals(1L, created.categoryId)
        assertEquals(MatchType.KEYWORD, created.matchType)
        assertEquals("Albert Heijn", created.pattern)
        assertEquals(20, created.priority)
        assertEquals(0, plan.skippedUnresolvedCategory)
        assertEquals(0, plan.skippedDuplicate)
    }

    @Test
    fun `skips a rule whose category name doesn't resolve locally`() {
        val plan = RuleImport.plan(
            rules = listOf(RuleExport("Onbekende categorie", "KEYWORD", "Iets", 20)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = emptyList(),
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedUnresolvedCategory)
    }

    @Test
    fun `skips a rule with an unrecognized match type`() {
        val plan = RuleImport.plan(
            rules = listOf(RuleExport("Boodschappen", "ONBEKEND_TYPE", "Iets", 20)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = emptyList(),
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedUnresolvedCategory)
    }

    @Test
    fun `skips a rule identical to one that already exists, so re-importing is a no-op`() {
        val existing = listOf(CategoryRule(categoryId = 1, matchType = MatchType.KEYWORD, pattern = "Albert Heijn", priority = 20))
        val plan = RuleImport.plan(
            rules = listOf(RuleExport("Boodschappen", "KEYWORD", "Albert Heijn", 20)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = existing,
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedDuplicate)
    }

    @Test
    fun `a different priority on an otherwise-identical rule still counts as a duplicate, not overwritten`() {
        val existing = listOf(CategoryRule(categoryId = 1, matchType = MatchType.KEYWORD, pattern = "Albert Heijn", priority = 50))
        val plan = RuleImport.plan(
            rules = listOf(RuleExport("Boodschappen", "KEYWORD", "Albert Heijn", 20)),
            categoryIdsByName = mapOf("Boodschappen" to 1L),
            existing = existing,
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedDuplicate)
    }

    @Test
    fun `resolves a rule pointing at a category created earlier in the same import`() {
        // The caller is expected to apply CategoryImport.Plan.toCreate and re-fetch ids before
        // calling this - simulated here by simply including the new category in the id map.
        val plan = RuleImport.plan(
            rules = listOf(RuleExport("Nieuwe Categorie", "KEYWORD", "Iets", 20)),
            categoryIdsByName = mapOf("Nieuwe Categorie" to 99L),
            existing = emptyList(),
        )

        assertEquals(1, plan.toCreate.size)
        assertEquals(99L, plan.toCreate.single().categoryId)
    }
}
