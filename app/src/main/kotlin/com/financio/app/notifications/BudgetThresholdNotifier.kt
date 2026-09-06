package com.financio.app.notifications

import android.content.Context
import com.financio.app.data.local.AppPreferences
import com.financio.core.budget.BudgetEvaluator
import com.financio.core.model.Money
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject

/**
 * Fires the "this budget just got worse" notification right after a categorization changes a
 * category's spend — not on a timer, since by then the moment (and the "why did this just
 * happen" context) has passed. Callers snapshot the category's spend *before* making their write
 * (see [TransactionsViewModel.categorize] and [ImportViewModel.confirm]) and hand it to
 * [checkAndNotify] afterwards; the decision itself is [BudgetEvaluator.crossedIntoWorseStatus],
 * already unit-tested in `:core`.
 */
class BudgetThresholdNotifier @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) {
    suspend fun checkAndNotify(categoryId: Long, previousSpent: Money) {
        if (!appPreferences.notificationsEnabled.first()) return

        val currentMonth = YearMonth.now()
        val budget = budgetRepository.observeBudgets(currentMonth).first().firstOrNull { it.categoryId == categoryId } ?: return
        val newSpent = transactionRepository.observeSpent(categoryId, currentMonth).first()
        if (!BudgetEvaluator.crossedIntoWorseStatus(previousSpent, newSpent, budget.limit)) return

        val categoryName = categoryRepository.observeCategories().first().firstOrNull { it.id == categoryId }?.name ?: return
        val status = BudgetEvaluator.evaluate(newSpent, budget.limit)
        NotificationHelper.notifyBudgetThreshold(context, categoryId, categoryName, status, newSpent, budget.limit)
    }

    /** The snapshot [checkAndNotify] needs *before* the write that might change [categoryId]'s spend. */
    suspend fun currentSpent(categoryId: Long): Money =
        transactionRepository.observeSpent(categoryId, YearMonth.now()).first()
}
