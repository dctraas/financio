package com.financio.app.data.repository

import com.financio.app.data.local.AccountDao
import com.financio.app.data.local.BudgetDao
import com.financio.app.data.local.CategoryDao
import com.financio.app.data.local.CategoryRuleDao
import com.financio.app.data.local.SavingsGoalDao
import com.financio.app.data.local.SavingsGoalEntity
import com.financio.app.data.local.TransactionDao
import com.financio.app.data.local.toDomain
import com.financio.app.data.local.toEntity
import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
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

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun existingDedupHashes(accountId: Long): Set<String> =
        dao.existingDedupHashes(accountId).toSet()

    override suspend fun insertAll(transactions: List<Transaction>) {
        dao.insertAll(transactions.map { it.toEntity() })
    }

    override fun observeSpent(categoryId: Long, yearMonth: YearMonth): Flow<Money> =
        dao.observeSpent(categoryId, yearMonth.toString()).map { Money(it) }

    override fun observeCategoryTotal(categoryId: Long, yearMonth: YearMonth): Flow<Money> =
        dao.observeCategoryTotal(categoryId, yearMonth.toString()).map { Money(it) }

    override fun observeCategoryNetAllTime(categoryId: Long): Flow<Money> =
        dao.observeCategoryNetAllTime(categoryId).map { Money(it) }

    override suspend fun updateCategory(transactionId: Long, categoryId: Long) {
        dao.updateCategory(transactionId, categoryId)
    }

    override suspend fun updateCategoryForCounterparty(accountId: Long, counterpartyName: String, categoryId: Long): Int =
        dao.updateCategoryForCounterparty(accountId, counterpartyName, categoryId)

    override fun observeSplits(transactionId: Long): Flow<List<TransactionSplit>> =
        dao.observeSplits(transactionId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun setSplits(transactionId: Long, splits: List<TransactionSplit>, fallbackCategoryId: Long?) {
        dao.setSplits(transactionId, splits.map { it.toEntity() }, fallbackCategoryId)
    }

    override fun observeSplitTransactionIds(): Flow<Set<Long>> =
        dao.observeSplitTransactionIds().map { it.toSet() }
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

    override suspend fun addRules(rules: List<CategoryRule>) {
        ruleDao.insertAll(rules.map { it.toRuleEntity() })
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
        // Reuse the existing row's id (if any) so REPLACE actually replaces it in place, instead
        // of always inserting id=0 - which, with no unique constraint on categoryId+yearMonth,
        // just created a brand-new row every time and left the same category listed twice.
        val existing = dao.find(categoryId, yearMonth.toString())
        val budget = Budget(
            id = existing?.id ?: 0,
            categoryId = categoryId,
            yearMonth = yearMonth,
            limit = limit,
            rollover = existing?.rollover ?: false,
        )
        dao.upsert(budget.toEntity())
    }

    override suspend fun setRollover(categoryId: Long, yearMonth: YearMonth, rollover: Boolean) {
        val existing = dao.find(categoryId, yearMonth.toString())
        val budget = Budget(
            id = existing?.id ?: 0,
            categoryId = categoryId,
            yearMonth = yearMonth,
            limit = existing?.let { Money(it.limitCents) } ?: Money.ZERO,
            rollover = rollover,
        )
        dao.upsert(budget.toEntity())
    }
}

class RoomAccountRepository @Inject constructor(
    private val dao: AccountDao,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addAccount(name: String, ibanMasked: String): Long =
        dao.insert(com.financio.app.data.local.AccountEntity(name = name, ibanMasked = ibanMasked))
}

class RoomSavingsGoalRepository @Inject constructor(
    private val dao: SavingsGoalDao,
) : SavingsGoalRepository {

    override fun observeGoals(): Flow<List<SavingsGoal>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addGoal(name: String, targetAmount: Money, categoryId: Long): Long =
        dao.insert(SavingsGoalEntity(name = name, targetAmountCents = targetAmount.cents, categoryId = categoryId))

    override suspend fun deleteGoal(goalId: Long) {
        dao.delete(goalId)
    }
}
