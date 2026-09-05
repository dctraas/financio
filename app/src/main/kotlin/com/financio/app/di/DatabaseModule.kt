package com.financio.app.di

import android.content.Context
import androidx.room.Room
import com.financio.app.data.local.AccountDao
import com.financio.app.data.local.BudgetDao
import com.financio.app.data.local.CategoryDao
import com.financio.app.data.local.CategoryRuleDao
import com.financio.app.data.local.DatabasePassphraseProvider
import com.financio.app.data.local.FinancioDatabase
import com.financio.app.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinancioDatabase {
        val passphrase = DatabasePassphraseProvider(context).getOrCreatePassphrase()
        // Verified directly against the 4.18.0 AAR (downloaded from Maven Central and inspected)
        // that this class still exists under this package, and that its bundled arm64-v8a native
        // library has 16 KB-aligned LOAD segments — required on newer devices, see the 16 KB
        // page size fix in the version bump that pinned this version.
        return Room.databaseBuilder(context, FinancioDatabase::class.java, "financio.db")
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @Provides fun provideAccountDao(db: FinancioDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: FinancioDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideCategoryRuleDao(db: FinancioDatabase): CategoryRuleDao = db.categoryRuleDao()
    @Provides fun provideBudgetDao(db: FinancioDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideTransactionDao(db: FinancioDatabase): TransactionDao = db.transactionDao()
}
