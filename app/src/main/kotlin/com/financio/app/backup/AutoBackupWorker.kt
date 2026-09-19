package com.financio.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Keeps [AutoBackupManager]'s on-disk snapshot fresh so Android's Auto Backup always has
 * something recent to copy - same [EntryPointAccessors] pattern as
 * [com.financio.app.notifications.WeeklyDigestWorker] (see its own doc comment for why: no
 * `androidx.hilt:hilt-work` dependency or `HiltWorkerFactory` wiring needed).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun autoBackupManager(): AutoBackupManager
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Entry::class.java).autoBackupManager().writeSnapshot()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "auto_backup"

        /** Idempotent — safe to call on every app startup, per [ExistingPeriodicWorkPolicy.KEEP]. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
