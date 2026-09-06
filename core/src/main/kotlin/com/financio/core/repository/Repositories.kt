package com.financio.core.repository

import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Pure interfaces — no Room, no Android import anywhere in this file. The `:app` module
 * provides the Room-backed implementation; this is the seam the architecture diagram draws
 * between the domain layer and the data layer, and where a future aggregator adapter would
 * plug in without either of the layers above ever changing.
 */
interface TransactionRepository {
    fun observeTransactions(accountId: Long): Flow<List<Transaction>>

    /** Every transaction across every account — categorization/budgets/charts are account-agnostic on purpose. */
    fun observeAllTransactions(): Flow<List<Transaction>>
    suspend fun existingDedupHashes(accountId: Long): Set<String>
    suspend fun insertAll(transactions: List<Transaction>)

    /** Money spent (debits only) in a category for a month, including any split allocations — what a budget limit is compared against. */
    fun observeSpent(categoryId: Long, yearMonth: YearMonth): Flow<Money>

    /**
     * Total activity (sum of absolute amounts, debit or credit) in a category for a month,
     * including any split allocations — what the Grafieken screen charts. Deliberately not
     * [observeSpent]: that one only sums debits, so an income category (all credits) always
     * summed to zero and its chart looked empty even though Transacties showed plenty of
     * matching rows.
     */
    fun observeCategoryTotal(categoryId: Long, yearMonth: YearMonth): Flow<Money>

    /** Net amount ever "spent" (debits minus credits) into a category, unscoped by month — a savings goal's progress. */
    fun observeCategoryNetAllTime(categoryId: Long): Flow<Money>

    /** Manual categorization of an already-persisted transaction — from the transaction list's "Te categoriseren" state. Also clears any existing splits on it. */
    suspend fun updateCategory(transactionId: Long, categoryId: Long)

    /** Applies [categoryId] to every transaction sharing [counterpartyName] on this account. Returns the number of rows changed. */
    suspend fun updateCategoryForCounterparty(accountId: Long, counterpartyName: String, categoryId: Long): Int

    fun observeSplits(transactionId: Long): Flow<List<TransactionSplit>>

    /** Replaces the transaction's splits entirely and nulls its own categoryId — the splits become authoritative. Pass an empty list to un-split it back to a single [categoryId]. */
    suspend fun setSplits(transactionId: Long, splits: List<TransactionSplit>, fallbackCategoryId: Long?)
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeRules(): Flow<List<CategoryRule>>
    suspend fun addRule(rule: CategoryRule)
    suspend fun addRules(rules: List<CategoryRule>)

    /** Returns the new category's id. */
    suspend fun addCategory(name: String, colorHex: String): Long

    /** Deleting a category also deletes any rule pointing at it and un-categorizes its transactions. */
    suspend fun deleteCategory(categoryId: Long)
    suspend fun deleteRule(ruleId: Long)
}

interface BudgetRepository {
    fun observeBudgets(yearMonth: YearMonth): Flow<List<Budget>>
    suspend fun setLimit(categoryId: Long, yearMonth: YearMonth, limit: Money)
    suspend fun setRollover(categoryId: Long, yearMonth: YearMonth, rollover: Boolean)
}

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>

    /** Returns the new account's id. */
    suspend fun addAccount(name: String, ibanMasked: String): Long
}

interface SavingsGoalRepository {
    fun observeGoals(): Flow<List<SavingsGoal>>
    suspend fun addGoal(name: String, targetAmount: Money, categoryId: Long): Long
    suspend fun deleteGoal(goalId: Long)
}
