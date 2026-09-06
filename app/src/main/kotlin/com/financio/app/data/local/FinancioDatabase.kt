package com.financio.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        CategoryRuleEntity::class,
        BudgetEntity::class,
        TransactionEntity::class,
        TransactionSplitEntity::class,
        SavingsGoalEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FinancioDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun savingsGoalDao(): SavingsGoalDao
}
