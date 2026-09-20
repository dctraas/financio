package com.financio.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * "Plan je week" — a Sunday-evening look ahead, the mirror image of [WeeklyDigestWorker]'s own
 * look-back. Same [EntryPointAccessors] pattern (see that class's own doc comment for why: no
 * `androidx.hilt:hilt-work` dependency needed).
 */
class SundayPlanningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun transactionRepository(): TransactionRepository
        fun budgetRepository(): BudgetRepository
        fun appPreferences(): AppPreferences
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, Entry::class.java)
        if (!entry.appPreferences().sundayPlanningNotificationsEnabled.first()) return Result.success()

        val transactions = entry.transactionRepository().observeAllTransactions().first()
        val today = LocalDate.now()
        val weekAhead = today.plusDays(7)
        val subscriptions = SubscriptionDetector.detect(transactions)
            .filter { it.estimatedNextDate.isAfter(today) && !it.estimatedNextDate.isAfter(weekAhead) }

        val budgets = entry.budgetRepository().observeBudgets(YearMonth.now()).first()
        val headroomCents = budgets.sumOf { budget ->
            val spent = entry.transactionRepository().observeCategorySpent(budget.categoryId, YearMonth.now()).first()
            (budget.limit.cents - spent.cents).coerceAtLeast(0)
        }

        val text = buildString {
            if (subscriptions.isEmpty()) {
                append("Geen vaste lasten deze week. ")
            } else {
                val total = Money(subscriptions.sumOf { kotlin.math.abs(it.averageAmount.cents) })
                append("${subscriptions.size} ${if (subscriptions.size == 1) "vast lastje" else "vaste lasten"} deze week (${total.toDisplayString()}). ")
            }
            append("Nog ${Money(headroomCents).toDisplayString()} budgetruimte deze maand.")
        }
        NotificationHelper.notifySundayPlanning(applicationContext, text)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sunday_planning"

        /** Idempotent — safe to call on every app startup, per [ExistingPeriodicWorkPolicy.KEEP]. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SundayPlanningWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextSundayEvening(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Milliseconds until the next Sunday 18:00 - today counts if it's still before that time. */
        private fun delayUntilNextSundayEvening(): Long {
            val now = LocalDateTime.now()
            val nextSunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(18, 0))
            val target = if (nextSunday.isAfter(now)) nextSunday else nextSunday.plusWeeks(1)
            return java.time.Duration.between(now, target).toMillis()
        }
    }
}
