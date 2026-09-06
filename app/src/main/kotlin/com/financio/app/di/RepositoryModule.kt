package com.financio.app.di

import com.financio.app.data.repository.RoomAccountRepository
import com.financio.app.data.repository.RoomBudgetRepository
import com.financio.app.data.repository.RoomCategoryRepository
import com.financio.app.data.repository.RoomSavingsGoalRepository
import com.financio.app.data.repository.RoomTransactionRepository
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain-layer interfaces (in `:core`, framework-free) to their Room-backed
 * implementations. This module — not the interfaces above it — is what a fase-3 aggregator
 * adapter would replace or extend.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: RoomBudgetRepository): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindSavingsGoalRepository(impl: RoomSavingsGoalRepository): SavingsGoalRepository
}
