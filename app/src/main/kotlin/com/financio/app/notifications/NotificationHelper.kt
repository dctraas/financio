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

/**
 * Two kinds of local, on-device-only notifications (no server, no push token — see the
 * manifest's POST_NOTIFICATIONS comment): a budget crossing into WARNING/OVER right after a
 * categorization, and a weekly digest. Both go through [notify], which is the one place that
 * checks the runtime permission — callers never need to remember to.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "financio_alerts"
    private const val BUDGET_THRESHOLD_NOTIFICATION_ID_BASE = 1_000
    private const val WEEKLY_DIGEST_NOTIFICATION_ID = 2_000

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
