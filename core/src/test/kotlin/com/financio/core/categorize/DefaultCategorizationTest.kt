package com.financio.core.categorize

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultCategorizationTest {

    @Test
    fun `category names are unique`() {
        val names = DefaultCategorization.CATEGORIES.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every category has a valid hex color`() {
        DefaultCategorization.CATEGORIES.forEach { category ->
            assertTrue(
                category.colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$")),
                "'${category.colorHex}' voor '${category.name}' is geen geldige hexkleur",
            )
        }
    }

    @Test
    fun `every keyword rule points at a category that actually exists`() {
        val categoryNames = DefaultCategorization.CATEGORIES.map { it.name }.toSet()
        DefaultCategorization.KEYWORD_RULES.forEach { rule ->
            assertTrue(
                rule.categoryName in categoryNames,
                "Regel voor '${rule.keyword}' verwijst naar onbekende categorie '${rule.categoryName}'",
            )
        }
    }

    @Test
    fun `no keyword is claimed by more than one category`() {
        val byKeyword = DefaultCategorization.KEYWORD_RULES.groupBy { it.keyword.lowercase() }
        val duplicated = byKeyword.filterValues { it.map { rule -> rule.categoryName }.distinct().size > 1 }
        assertTrue(duplicated.isEmpty(), "Trefwoorden die naar meerdere categorieën wijzen: $duplicated")
    }

    @Test
    fun `no keyword is listed twice for the same category`() {
        val byCategory = DefaultCategorization.KEYWORD_RULES.groupBy { it.categoryName }
        byCategory.forEach { (category, rules) ->
            val keywords = rules.map { it.keyword.lowercase() }
            assertEquals(keywords.size, keywords.toSet().size, "Dubbel trefwoord binnen '$category'")
        }
    }

    @Test
    fun `the catch-all category has no default rules pointing at it`() {
        val overigRules = DefaultCategorization.KEYWORD_RULES.filter { it.categoryName == "Overig" }
        assertTrue(overigRules.isEmpty(), "'Overig' is de handmatige vangnetcategorie, geen regeldoel")
    }
}
