package com.financio.app.data.local

import androidx.room.withTransaction
import com.financio.core.categorize.DefaultCategorization
import com.financio.core.model.MatchType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the default categories and keyword rules the first time the app runs on an empty
 * database, so a first import has something sensible to auto-categorize against instead of
 * showing every transaction as "te controleren" against an empty category list — see the
 * README's categorization UX notes for the full reasoning.
 *
 * Guarded by an emptiness check on the categories table rather than a uniqueness constraint:
 * this is a single local database with no concurrent writers, so "no categories yet" is a
 * reliable signal this has never run, and simpler than adding a migration for a one-time seed.
 * The insert itself runs in one transaction so a process death mid-seed can't leave categories
 * without their rules — a retried [seedIfEmpty] would otherwise see a non-empty table and skip.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: FinancioDatabase,
    private val categoryDao: CategoryDao,
    private val categoryRuleDao: CategoryRuleDao,
) {
    suspend fun seedIfEmpty() {
        if (categoryDao.count() > 0) return

        database.withTransaction {
            val categoryIds = DefaultCategorization.CATEGORIES.associate { category ->
                val id = categoryDao.insert(CategoryEntity(name = category.name, colorHex = category.colorHex))
                category.name to id
            }

            val rules = DefaultCategorization.KEYWORD_RULES.map { rule ->
                CategoryRuleEntity(
                    categoryId = categoryIds.getValue(rule.categoryName),
                    matchType = MatchType.KEYWORD.name,
                    pattern = rule.keyword,
                    priority = DefaultCategorization.PRIORITY,
                )
            }
            categoryRuleDao.insertAll(rules)
        }
    }
}
