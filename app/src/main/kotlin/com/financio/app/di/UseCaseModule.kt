package com.financio.app.di

import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.ImportStatementUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * [ImportStatementUseCase] lives in `:core` and stays free of Hilt annotations on principle —
 * the domain layer shouldn't need to know which DI framework the app happens to use. This
 * module is the one place that wires the two together.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideImportStatementUseCase(
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
    ): ImportStatementUseCase = ImportStatementUseCase(transactionRepository, categoryRepository)
}
