package com.financio.core.backup

import com.financio.core.model.Category

/**
 * Import is deliberately additive-only, never mutating what's already there: a category whose
 * name already exists locally is left alone (not renamed, recolored, or duplicated) rather than
 * guessing whether the import or the existing local edit should win.
 */
object CategoryImport {

    data class Plan(val toCreate: List<CategoryExport>, val skippedExisting: Int)

    fun plan(categories: List<CategoryExport>, existing: List<Category>): Plan {
        val existingNames = existing.map { it.name }.toSet()
        val toCreate = categories.filter { it.name !in existingNames }
        return Plan(toCreate = toCreate, skippedExisting = categories.size - toCreate.size)
    }
}
