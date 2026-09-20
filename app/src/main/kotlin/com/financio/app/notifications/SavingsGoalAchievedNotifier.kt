package com.financio.app.notifications

import android.content.Context
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Money
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Fires "spaardoel gehaald" right after a write that could have pushed a goal linked to
 * [categoryId] over its target - same "snapshot before, compare after" shape as
 * [BudgetThresholdNotifier], and deliberately wired into the same two call sites that already do
 * that dance for the budget check ([com.financio.app.ui.transactions.TransactionsViewModel.categorize]
 * and [com.financio.app.ui.savings.SavingsGoalsViewModel.addManualAdjustment]) rather than every
 * categorization surface in the app - the goal still gets noticed as achieved the next time either
 * of those runs even if a less common path (bulk rule application, import) was what actually
 * tipped it over.
 */
class SavingsGoalAchievedNotifier @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val transactionRepository: TransactionRepository,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) {
    /** The snapshot [checkAndNotify] needs *before* the write that might change [categoryId]'s all-time net. */
    suspend fun currentCategoryNet(categoryId: Long): Money = transactionRepository.observeCategoryNetAllTime(categoryId).first()

    suspend fun checkAndNotify(categoryId: Long, previousCategoryNet: Money) {
        if (!appPreferences.savingsGoalAchievedNotificationsEnabled.first()) return

        val goals = savingsGoalRepository.observeGoals().first().filter { it.categoryId == categoryId && !it.archived && it.targetAmount.cents > 0 }
        if (goals.isEmpty()) return

        val newCategoryNet = transactionRepository.observeCategoryNetAllTime(categoryId).first()
        for (goal in goals) {
            val wasAchieved = (previousCategoryNet + goal.manualAdjustment).cents >= goal.targetAmount.cents
            val nowAchieved = (newCategoryNet + goal.manualAdjustment).cents >= goal.targetAmount.cents
            if (!wasAchieved && nowAchieved) {
                NotificationHelper.notifyGoalAchieved(context, goal.id, goal.name, goal.targetAmount)
            }
        }
    }
}
