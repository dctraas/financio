package com.financio.app

import android.app.Application
import android.util.Log
import com.financio.app.data.local.DatabaseSeeder
import com.financio.app.notifications.NotificationHelper
import com.financio.app.notifications.WeeklyDigestWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FinancioApplication : Application() {

    @Inject lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget on a background dispatcher: seeding is a one-time, idempotent check
        // (see DatabaseSeeder) and nothing in the UI depends on it having finished before the
        // first frame, only before the first import — which needs at least a file pick first.
        // Caught rather than left to crash the process: worst case of a failed seed is an empty
        // category list (today's behavior), which is recoverable, not a reason to crash on launch.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { databaseSeeder.seedIfEmpty() }
                .onFailure { Log.e("FinancioApplication", "Kon standaardcategorieën niet aanmaken", it) }
        }

        // Both no-ops with no visible effect until the user turns notifications on in
        // Instellingen: the channel is silent/inert until something is actually posted to it, and
        // the worker checks AppPreferences.notificationsEnabled itself on every run (see
        // WeeklyDigestWorker.doWork) rather than being scheduled/cancelled from the toggle.
        NotificationHelper.ensureChannel(this)
        WeeklyDigestWorker.schedule(this)
    }
}
