package com.financio.app.data.repository

import com.financio.app.data.local.BudgetDao
import com.financio.app.data.local.CategoryDao
import com.financio.app.data.local.CategoryRuleDao
import com.financio.app.data.local.TransactionDao
import com.financio.app.data.local.toDomain
import com.financio.app.data.local.toEntity
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

/**
 * The Room-backed half of the seam the architecture diagram draws: everything above
 * [TransactionRepository] et al. (use cases, ViewModels, UI) never imports Room directly, so a
 * fase-3 aggregator adapter could implement these same interfaces instead without touching them.
 */
class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
        dao.observeByAccount(accountId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun existingDedupHashes(accountId: Long): Set<String> =
        dao.existingDedupHashes(accountId).toSet()

    override suspend fun insertAll(transactions: List<Transaction>) {
        dao.insertAll(transactions.map { it.toEntity() })
    }

    override fun observeSpent(categoryId: Long, yearMonth: YearMonth): Flow<Money> =
        dao.observeSpent(categoryId, yearMonth.toString()).map { Money(it) }

    override suspend fun updateCategory(transactionId: Long, categoryId: Long) {
        dao.updateCategory(transactionId, categoryId)
    }
}

class RoomCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val ruleDao: CategoryRuleDao,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeRules(): Flow<List<CategoryRule>> =
        ruleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addRule(rule: CategoryRule) {
        ruleDao.insert(rule.toRuleEntity())
    }

    override suspend fun addCategory(name: String, colorHex: String): Long =
        categoryDao.insert(com.financio.app.data.local.CategoryEntity(name = name, colorHex = colorHex))

    override suspend fun deleteCategory(categoryId: Long) {
        categoryDao.delete(categoryId)
    }

    override suspend fun deleteRule(ruleId: Long) {
        ruleDao.delete(ruleId)
    }

    private fun CategoryRule.toRuleEntity() = com.financio.app.data.local.CategoryRuleEntity(
        id = id,
        categoryId = categoryId,
        matchType = matchType.name,
        pattern = pattern,
        priority = priority,
    )
}

class RoomBudgetRepository @Inject constructor(
    private val dao: BudgetDao,
) : BudgetRepository {

    override fun observeBudgets(yearMonth: YearMonth): Flow<List<Budget>> =
        dao.observeForMonth(yearMonth.toString()).map { entities -> entities.map { it.toDomain() } }

    override suspend fun setLimit(categoryId: Long, yearMonth: YearMonth, limit: Money) {
        dao.upsert(Budget(categoryId = categoryId, yearMonth = yearMonth, limit = limit).toEntity())
    }
}
