package com.financio.core.repository

import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.Transaction
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
    suspend fun existingDedupHashes(accountId: Long): Set<String>
    suspend fun insertAll(transactions: List<Transaction>)
    fun observeSpent(categoryId: Long, yearMonth: YearMonth): Flow<Money>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeRules(): Flow<List<CategoryRule>>
    suspend fun addRule(rule: CategoryRule)
}

interface BudgetRepository {
    fun observeBudgets(yearMonth: YearMonth): Flow<List<Budget>>
    suspend fun setLimit(categoryId: Long, yearMonth: YearMonth, limit: Money)
}
