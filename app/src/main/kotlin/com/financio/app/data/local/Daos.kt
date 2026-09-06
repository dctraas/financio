package com.financio.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    /** Used by [com.financio.app.data.local.DatabaseSeeder] to decide whether seeding has already run. */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** Cascades to category_rules (ON DELETE CASCADE) and sets transactions.categoryId to null (ON DELETE SET NULL). */
    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun delete(categoryId: Long)
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY priority")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Insert
    suspend fun insert(rule: CategoryRuleEntity)

    @Insert
    suspend fun insertAll(rules: List<CategoryRuleEntity>)

    @Query("DELETE FROM category_rules WHERE id = :ruleId")
    suspend fun delete(ruleId: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun observeForMonth(yearMonth: String): Flow<List<BudgetEntity>>

    /**
     * Looked up by [com.financio.app.data.repository.RoomBudgetRepository.setLimit] so it can
     * pass the existing row's id along to [upsert] instead of always passing id=0. Without this,
     * every "set the limit" call inserted a brand-new row (there's no unique constraint on
     * categoryId+yearMonth), and REPLACE never had a real conflict to replace — hence the same
     * category showing up twice for the same month in Budgetten.
     */
    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    suspend fun find(categoryId: Long, yearMonth: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    /**
     * One-time repair for rows already duplicated by the bug [find] fixes going forward: keeps
     * only the most recently written (highest id) row per categoryId+yearMonth. Safe to run on
     * every startup — a no-op once no duplicates remain. Run from [DatabaseSeeder].
     */
    @Query(
        """
        DELETE FROM budgets WHERE id NOT IN (
            SELECT MAX(id) FROM budgets GROUP BY categoryId, yearMonth
        )
        """
    )
    suspend fun deleteDuplicates()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT dedupHash FROM transactions WHERE accountId = :accountId")
    suspend fun existingDedupHashes(accountId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /**
     * Sum of expenses (negative amounts only, negated back to positive) for one category in
     * one calendar month — exactly the number [com.financio.core.budget.BudgetEvaluator] compares
     * against a budget's limit. `date` is stored as ISO "yyyy-MM-dd", so a "yyyy-MM" prefix match
     * is enough to scope it to the month.
     */
    @Query(
        """
        SELECT COALESCE(-SUM(amountCents), 0) FROM transactions
        WHERE categoryId = :categoryId AND date LIKE :yearMonth || '-%' AND amountCents < 0
        """
    )
    fun observeSpent(categoryId: Long, yearMonth: String): Flow<Long>

    /**
     * Sum of all activity (debit or credit, as an absolute amount) for one category in one
     * calendar month — what Grafieken charts. [observeSpent] only sums debits, so a category
     * that's all credits (e.g. "Inkomsten") always summed to zero there and its chart looked
     * empty even though Transacties showed a full list of matching rows for the same filter.
     */
    @Query(
        """
        SELECT COALESCE(SUM(ABS(amountCents)), 0) FROM transactions
        WHERE categoryId = :categoryId AND date LIKE :yearMonth || '-%'
        """
    )
    fun observeCategoryTotal(categoryId: Long, yearMonth: String): Flow<Long>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :transactionId")
    suspend fun updateCategory(transactionId: Long, categoryId: Long)

    /** Bulk "categorize all the rest of this merchant's transactions the same way" from the transaction list. */
    @Query("UPDATE transactions SET categoryId = :categoryId WHERE accountId = :accountId AND counterpartyName = :counterpartyName")
    suspend fun updateCategoryForCounterparty(accountId: Long, counterpartyName: String, categoryId: Long): Int
}
