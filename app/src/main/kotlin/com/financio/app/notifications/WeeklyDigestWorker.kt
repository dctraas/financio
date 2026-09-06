package com.financio.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financio.app.data.local.AppPreferences
import com.financio.core.budget.BudgetEvaluator
import com.financio.core.budget.BudgetStatus
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Only ever has the standard `(Context, WorkerParameters)` constructor WorkManager's own default
 * [androidx.work.WorkerFactory] already knows how to build — deliberately, so this feature needs
 * no `androidx.hilt:hilt-work` dependency, no `HiltWorkerFactory`, and no
 * `Configuration.Provider` wiring in `FinancioApplication`. Hilt-provided repositories are reached
 * instead through [EntryPointAccessors], a plain Hilt feature that needs none of that.
 */
class WeeklyDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun transactionRepository(): TransactionRepository
        fun budgetRepository(): BudgetRepository
        fun appPreferences(): AppPreferences
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, Entry::class.java)
        // Scheduled unconditionally at startup (see schedule()) rather than only when enabled -
        // simpler than cancelling/rescheduling every time the Instellingen toggle flips, and this
        // check makes a disabled run a harmless no-op instead.
        if (!entry.appPreferences().notificationsEnabled.value) return Result.success()

        val spentThisWeek = spentThisWeek(entry.transactionRepository())
        val overBudgetCount = overBudgetCategoryCount(entry.transactionRepository(), entry.budgetRepository())

        NotificationHelper.notifyWeeklyDigest(applicationContext, spentThisWeek, overBudgetCount)
        return Result.success()
    }

    private suspend fun spentThisWeek(transactionRepository: TransactionRepository): Money {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(6)
        val transactions = transactionRepository.observeAllTransactions().first()
        val debitCents = transactions
            .filter { it.date in weekAgo..today && it.amount.cents < 0 }
            .sumOf { it.amount.cents }
        return Money(-debitCents)
    }

    private suspend fun overBudgetCategoryCount(transactionRepository: TransactionRepository, budgetRepository: BudgetRepository): Int {
        val currentMonth = YearMonth.now()
        val budgets = budgetRepository.observeBudgets(currentMonth).first()
        return budgets.count { budget ->
            val spent = transactionRepository.observeSpent(budget.categoryId, currentMonth).first()
            BudgetEvaluator.evaluate(spent, budget.limit) == BudgetStatus.OVER
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "weekly_digest"

        /** Idempotent — safe to call on every app startup, per [ExistingPeriodicWorkPolicy.KEEP]. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
