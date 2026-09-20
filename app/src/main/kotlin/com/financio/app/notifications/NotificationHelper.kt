package com.financio.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.financio.app.R
import com.financio.core.budget.BudgetStatus
import com.financio.core.model.Money
import com.financio.core.model.Transaction

/**
 * All local, on-device-only notifications (no server, no push token — see the manifest's
 * POST_NOTIFICATIONS comment): a budget crossing into WARNING/OVER right after a categorization,
 * a weekly digest, a spaardoel behaald, an ongewoon grote transactie, and a zondagavond
 * vooruitblik. All go through [notify], which is the one place that checks the runtime
 * permission — callers never need to remember to.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "financio_alerts"
    private const val BUDGET_THRESHOLD_NOTIFICATION_ID_BASE = 1_000
    private const val WEEKLY_DIGEST_NOTIFICATION_ID = 2_000
    private const val SAVINGS_GOAL_ACHIEVED_NOTIFICATION_ID_BASE = 3_000
    private const val UNUSUAL_TRANSACTION_NOTIFICATION_ID_BASE = 4_000
    private const val SUNDAY_PLANNING_NOTIFICATION_ID = 5_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Budgetten en samenvattingen",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Meldingen wanneer een budget over de limiet dreigt te gaan, en een wekelijkse samenvatting."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * [categoryId] makes the notification id unique per category so a second category crossing
     * its own threshold the same day gets its own notification instead of replacing the first.
     */
    fun notifyBudgetThreshold(context: Context, categoryId: Long, categoryName: String, status: BudgetStatus, spent: Money, limit: Money) {
        val title = if (status == BudgetStatus.OVER) {
            "$categoryName is over budget"
        } else {
            "$categoryName nadert de limiet"
        }
        val text = "${spent.toDisplayString()} van ${limit.toDisplayString()} deze maand besteed."
        notify(context, (BUDGET_THRESHOLD_NOTIFICATION_ID_BASE + categoryId).toInt(), title, text)
    }

    fun notifyWeeklyDigest(context: Context, spentThisWeek: Money, overBudgetCategoryCount: Int) {
        val title = "Jouw week in Financio"
        val text = if (overBudgetCategoryCount > 0) {
            "${spentThisWeek.toDisplayString()} uitgegeven deze week — $overBudgetCategoryCount " +
                if (overBudgetCategoryCount == 1) "budget is over de limiet." else "budgetten zijn over de limiet."
        } else {
            "${spentThisWeek.toDisplayString()} uitgegeven deze week — al je budgetten staan op groen of amber."
        }
        notify(context, WEEKLY_DIGEST_NOTIFICATION_ID, title, text)
    }

    fun notifyGoalAchieved(context: Context, goalId: Long, goalName: String, targetAmount: Money) {
        notify(
            context,
            (SAVINGS_GOAL_ACHIEVED_NOTIFICATION_ID_BASE + goalId).toInt(),
            "Spaardoel gehaald! 🎉",
            "'$goalName' staat op ${targetAmount.toDisplayString()} — helemaal vol.",
        )
    }

    /** [transactions] is always non-empty - the caller only calls this once it's found at least one. */
    fun notifyUnusualTransaction(context: Context, transactions: List<Transaction>) {
        val biggest = transactions.maxBy { kotlin.math.abs(it.amount.cents) }
        val title = "Ongewone transactie opgemerkt"
        val text = if (transactions.size == 1) {
            "${biggest.counterpartyName}: ${biggest.amount.toDisplayString()} — veel meer dan gebruikelijk."
        } else {
            "${transactions.size} transacties vallen op, waaronder ${biggest.counterpartyName} (${biggest.amount.toDisplayString()})."
        }
        // A single, reused id (not per-transaction) - repeatedly re-importing overlapping files
        // should update the same notification, not pile up a new one for every re-run.
        notify(context, UNUSUAL_TRANSACTION_NOTIFICATION_ID_BASE, title, text)
    }

    fun notifySundayPlanning(context: Context, upcomingText: String) {
        notify(context, SUNDAY_PLANNING_NOTIFICATION_ID, "Plan je week", upcomingText)
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        // Required from Android 13 (API 33) onward - posting without it doesn't crash
        // (NotificationManagerCompat checks this itself), it just silently does nothing, so this
        // check is only here to make that "nothing happens without permission" behavior explicit
        // rather than relying on the library's own internal guard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
