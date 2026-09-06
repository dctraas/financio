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

    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>
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
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY name")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Insert
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Query("DELETE FROM savings_goals WHERE id = :goalId")
    suspend fun delete(goalId: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    /** Every transaction, every account — categorization/budgets/charts don't care which account money moved through. */
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT dedupHash FROM transactions WHERE accountId = :accountId")
    suspend fun existingDedupHashes(accountId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /**
     * Sum of expenses (negative amounts only, negated back to positive) for one category in one
     * calendar month, folding in any split allocations to that category alongside whole
     * transactions categorized directly — exactly the number
     * [com.financio.core.budget.BudgetEvaluator] compares against a budget's limit. A split
     * transaction's own `categoryId` is null (see [setSplits]), so it never double-counts here:
     * the first branch only matches whole, non-split transactions.
     */
    @Query(
        """
        SELECT COALESCE(-SUM(amt), 0) FROM (
            SELECT amountCents AS amt, date AS d FROM transactions WHERE categoryId = :categoryId
            UNION ALL
            SELECT s.amountCents AS amt, t.date AS d FROM transaction_splits s
                JOIN transactions t ON t.id = s.transactionId WHERE s.categoryId = :categoryId
        ) WHERE d LIKE :yearMonth || '-%' AND amt < 0
        """
    )
    fun observeSpent(categoryId: Long, yearMonth: String): Flow<Long>

    /**
     * Sum of all activity (debit or credit, as an absolute amount) for one category in one
     * calendar month, splits included — what Grafieken charts. [observeSpent] only sums debits,
     * so a category that's all credits (e.g. "Inkomsten") always summed to zero there even though
     * Transacties showed a full list of matching rows for the same filter.
     */
    @Query(
        """
        SELECT COALESCE(SUM(ABS(amt)), 0) FROM (
            SELECT amountCents AS amt, date AS d FROM transactions WHERE categoryId = :categoryId
            UNION ALL
            SELECT s.amountCents AS amt, t.date AS d FROM transaction_splits s
                JOIN transactions t ON t.id = s.transactionId WHERE s.categoryId = :categoryId
        ) WHERE d LIKE :yearMonth || '-%'
        """
    )
    fun observeCategoryTotal(categoryId: Long, yearMonth: String): Flow<Long>

    /** Same shape as [observeSpent] but unscoped by month — a savings goal's all-time progress. */
    @Query(
        """
        SELECT COALESCE(-SUM(amt), 0) FROM (
            SELECT amountCents AS amt FROM transactions WHERE categoryId = :categoryId
            UNION ALL
            SELECT s.amountCents AS amt FROM transaction_splits s WHERE s.categoryId = :categoryId
        )
        """
    )
    fun observeCategoryNetAllTime(categoryId: Long): Flow<Long>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :transactionId")
    suspend fun setCategoryColumn(transactionId: Long, categoryId: Long?)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE accountId = :accountId AND counterpartyName = :counterpartyName")
    suspend fun setCategoryForCounterparty(accountId: Long, counterpartyName: String, categoryId: Long): Int

    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun clearSplits(transactionId: Long)

    @Query(
        """
        DELETE FROM transaction_splits WHERE transactionId IN (
            SELECT id FROM transactions WHERE accountId = :accountId AND counterpartyName = :counterpartyName
        )
        """
    )
    suspend fun clearSplitsForCounterparty(accountId: Long, counterpartyName: String)

    @Query("SELECT * FROM transaction_splits WHERE transactionId = :transactionId")
    fun observeSplits(transactionId: Long): Flow<List<TransactionSplitEntity>>

    @Query("SELECT DISTINCT transactionId FROM transaction_splits")
    fun observeSplitTransactionIds(): Flow<List<Long>>

    @Insert
    suspend fun insertSplits(splits: List<TransactionSplitEntity>)

    /** Manual categorization of an already-persisted transaction. Also clears any existing splits: picking one category un-splits it. */
    @androidx.room.Transaction
    suspend fun updateCategory(transactionId: Long, categoryId: Long) {
        clearSplits(transactionId)
        setCategoryColumn(transactionId, categoryId)
    }

    /** Bulk "categorize all the rest of this merchant's transactions the same way" from the transaction list. Also clears their splits, if any. */
    @androidx.room.Transaction
    suspend fun updateCategoryForCounterparty(accountId: Long, counterpartyName: String, categoryId: Long): Int {
        clearSplitsForCounterparty(accountId, counterpartyName)
        return setCategoryForCounterparty(accountId, counterpartyName, categoryId)
    }

    /** Replaces a transaction's splits entirely. An empty list un-splits it back to [fallbackCategoryId] (nullable — leaves it uncategorized). */
    @androidx.room.Transaction
    suspend fun setSplits(transactionId: Long, splits: List<TransactionSplitEntity>, fallbackCategoryId: Long?) {
        clearSplits(transactionId)
        if (splits.isNotEmpty()) {
            insertSplits(splits)
            setCategoryColumn(transactionId, null)
        } else {
            setCategoryColumn(transactionId, fallbackCategoryId)
        }
    }
}
