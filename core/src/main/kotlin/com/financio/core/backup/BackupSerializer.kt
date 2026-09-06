package com.financio.core.backup

import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Turns categories/rules into portable JSON and back. Kept in `:core` (framework-free, no Room)
 * so the format itself — and the decision of what's "the same" category or rule across two
 * different installs — is unit-testable without an Android SDK, like everything else risky here.
 */
object BackupSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // forward-compatible: an older app version can still open a newer export
    }

    fun exportCategories(categories: List<Category>): String =
        encode(BackupBundle(categories = categories.map { it.toExport() }))

    fun exportRules(rules: List<CategoryRule>, categoriesById: Map<Long, Category>): String =
        encode(BackupBundle(rules = rules.toExport(categoriesById)))

    fun exportAll(categories: List<Category>, rules: List<CategoryRule>): String {
        val categoriesById = categories.associateBy { it.id }
        return encode(BackupBundle(categories = categories.map { it.toExport() }, rules = rules.toExport(categoriesById)))
    }

    /** @throws SerializationException on malformed or unrecognizable JSON — the caller turns that into a user-facing message. */
    fun parse(content: String): BackupBundle = json.decodeFromString(BackupBundle.serializer(), content)

    private fun encode(bundle: BackupBundle): String = json.encodeToString(BackupBundle.serializer(), bundle)

    private fun Category.toExport() = CategoryExport(name = name, colorHex = colorHex)

    /** Rules whose category was deleted between loading and exporting (id no longer in [categoriesById]) are silently dropped: they're orphaned either way. */
    private fun List<CategoryRule>.toExport(categoriesById: Map<Long, Category>): List<RuleExport> =
        mapNotNull { rule ->
            categoriesById[rule.categoryId]?.let { category ->
                RuleExport(categoryName = category.name, matchType = rule.matchType.name, pattern = rule.pattern, priority = rule.priority)
            }
        }
}
