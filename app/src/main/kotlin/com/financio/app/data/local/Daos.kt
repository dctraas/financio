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
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY priority")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Insert
    suspend fun insert(rule: CategoryRuleEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun observeForMonth(yearMonth: String): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)
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
}
