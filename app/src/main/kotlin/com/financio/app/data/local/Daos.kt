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

    @Query("UPDATE accounts SET name = :name WHERE id = :accountId")
    suspend fun rename(accountId: Long, name: String)

    @Query("UPDATE accounts SET hidden = :hidden WHERE id = :accountId")
    suspend fun setHidden(accountId: Long, hidden: Boolean)

    @Query("UPDATE accounts SET excludedFromTotal = :excluded WHERE id = :accountId")
    suspend fun setExcludedFromTotal(accountId: Long, excluded: Boolean)

    @Query("UPDATE accounts SET manualBalanceCents = :balanceCents WHERE id = :accountId")
    suspend fun setManualBalance(accountId: Long, balanceCents: Long?)

    @Query("UPDATE accounts SET importIdentifier = :identifier WHERE id = :accountId")
    suspend fun setImportIdentifier(accountId: Long, identifier: String)
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

    @Query("UPDATE categories SET name = :name WHERE id = :categoryId")
    suspend fun rename(categoryId: Long, name: String)

    @Query("UPDATE categories SET colorHex = :colorHex WHERE id = :categoryId")
    suspend fun setColor(categoryId: Long, colorHex: String)
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

    /** The "sleep om de volgorde te wijzigen" reorder action: whichever rules moved get their priority rewritten to match the new list order (1-indexed). */
    @Query("UPDATE category_rules SET priority = :priority WHERE id = :ruleId")
    suspend fun setPriority(ruleId: Long, priority: Int)

    /** The "regel bewerken" dialog's save action - priority is untouched, use [setPriority] to reorder. */
    @Query("UPDATE category_rules SET categoryId = :categoryId, matchType = :matchType, pattern = :pattern WHERE id = :ruleId")
    suspend fun update(ruleId: Long, categoryId: Long, matchType: String, pattern: String)
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

    /** How many budget rows (any month) point at this category - the "1 budgetlimiet" count in the delete-category confirmation. */
    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

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

    /** Full edit ("bewerken") of an existing goal's own fields - never touches [SavingsGoalEntity.archived] or [SavingsGoalEntity.manualAdjustmentCents], which have their own dedicated actions. */
    @Query(
        "UPDATE savings_goals SET name = :name, targetAmountCents = :targetAmountCents, " +
            "categoryId = :categoryId, linkedAccountId = :linkedAccountId, targetDate = :targetDate " +
            "WHERE id = :goalId",
    )
    suspend fun update(goalId: Long, name: String, targetAmountCents: Long, categoryId: Long, linkedAccountId: Long?, targetDate: String?)

    @Query("UPDATE savings_goals SET archived = :archived WHERE id = :goalId")
    suspend fun setArchived(goalId: Long, archived: Boolean)

    /** Adds (not sets) [deltaCents] to the running manual-adjustment total - see [com.financio.core.model.SavingsGoal.manualAdjustment]. */
    @Query("UPDATE savings_goals SET manualAdjustmentCents = manualAdjustmentCents + :deltaCents WHERE id = :goalId")
    suspend fun addManualAdjustment(goalId: Long, deltaCents: Long)
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
     * The one "how much did this category cost this month" number, shared by Budget, Inzicht and
     * the notification threshold checks — previously three different call sites could each get a
     * different answer for the same category/month ([observeSpent] only ever summed debits, so an
     * all-credit category like "Inkomsten" always showed €0 there even though Transacties' own
     * filter on it showed a full list of rows; a debit category with an occasional refund summed
     * to more in the old [observeSpent] than the net amount that actually left the account, since
     * it ignored the refund credit entirely). `ABS(SUM(amt))` unifies both: it nets debits and
     * credits together first, then takes the magnitude — same result [observeSpent] gave for a
     * category with debits only, same result the old `observeCategoryTotal` gave for a category
     * with credits only, and a more honest number than either gave for a category with both.
     * Splits are folded in alongside whole transactions categorized directly; a split transaction's
     * own `categoryId` is null (see [setSplits]), so it never double-counts here.
     */
    @Query(
        """
        SELECT ABS(COALESCE(SUM(amt), 0)) FROM (
            SELECT amountCents AS amt, date AS d FROM transactions WHERE categoryId = :categoryId
            UNION ALL
            SELECT s.amountCents AS amt, t.date AS d FROM transaction_splits s
                JOIN transactions t ON t.id = s.transactionId WHERE s.categoryId = :categoryId
        ) WHERE d LIKE :yearMonth || '-%'
        """
    )
    fun observeCategorySpent(categoryId: Long, yearMonth: String): Flow<Long>

    /** Signed net (debits minus credits), unscoped by month — a savings goal's all-time progress, where the sign itself (not just the magnitude) matters. */
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

    /** Every whole transaction currently in [oldCategoryId] moves to [newCategoryId] (or null) - the "kies waar deze transacties naartoe gaan" step before deleting a category. A split transaction's own categoryId is already null, so this never touches split allocations. */
    @Query("UPDATE transactions SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategory(oldCategoryId: Long, newCategoryId: Long?)

    @Query("UPDATE transactions SET note = :note WHERE id = :transactionId")
    suspend fun setNote(transactionId: Long, note: String?)

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

    /** Every stored split, across every transaction — Transacties' inline "Verzorging €14,95 · Vakantie €10,00" needs the actual parts, not just which rows are split. */
    @Query("SELECT * FROM transaction_splits")
    fun observeAllSplits(): Flow<List<TransactionSplitEntity>>

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
