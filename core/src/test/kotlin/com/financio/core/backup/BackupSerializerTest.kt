package com.financio.core.backup

import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupSerializerTest {

    private val groceries = Category(id = 1, name = "Boodschappen", colorHex = "#5B7A52")
    private val transport = Category(id = 2, name = "Vervoer", colorHex = "#4C6E77")
    private val rule = CategoryRule(id = 10, categoryId = 1, matchType = MatchType.KEYWORD, pattern = "Albert Heijn", priority = 20)

    @Test
    fun `round-trips a categories-only export`() {
        val json = BackupSerializer.exportCategories(listOf(groceries, transport))
        val bundle = BackupSerializer.parse(json)

        assertEquals(listOf(CategoryExport("Boodschappen", "#5B7A52"), CategoryExport("Vervoer", "#4C6E77")), bundle.categories)
        assertTrue(bundle.rules.isEmpty())
    }

    @Test
    fun `round-trips a rules-only export, resolving the category by name`() {
        val json = BackupSerializer.exportRules(listOf(rule), mapOf(1L to groceries))
        val bundle = BackupSerializer.parse(json)

        assertTrue(bundle.categories.isEmpty())
        assertEquals(listOf(RuleExport("Boodschappen", "KEYWORD", "Albert Heijn", 20)), bundle.rules)
    }

    @Test
    fun `round-trips a combined export`() {
        val json = BackupSerializer.exportAll(listOf(groceries, transport), listOf(rule))
        val bundle = BackupSerializer.parse(json)

        assertEquals(2, bundle.categories.size)
        assertEquals(1, bundle.rules.size)
    }

    @Test
    fun `a rule pointing at a category not in the id map is dropped rather than exported broken`() {
        val json = BackupSerializer.exportRules(listOf(rule), categoriesById = emptyMap())
        val bundle = BackupSerializer.parse(json)
        assertTrue(bundle.rules.isEmpty())
    }

    @Test
    fun `fails loudly on malformed input instead of guessing`() {
        assertThrows(SerializationException::class.java) {
            BackupSerializer.parse("dit is geen json")
        }
    }

    @Test
    fun `ignores unknown fields for forward compatibility with a newer export format`() {
        val bundle = BackupSerializer.parse(
            """{"categories":[{"name":"Boodschappen","colorHex":"#5B7A52","futureField":"x"}],"rules":[]}""",
        )
        assertEquals("Boodschappen", bundle.categories.single().name)
    }
}
