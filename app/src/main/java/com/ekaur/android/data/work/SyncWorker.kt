package com.ekaur.android.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ekaur.android.EkAurApp
import com.ekaur.android.sync.SyncResult
import java.util.concurrent.TimeUnit

/**
 * Pushes the day's totals in the background.
 *
 * Offline-first: a failure is never an error the user sees. The rows stay dirty
 * and the next run sends them, so a week on a train loses nothing.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? EkAurApp)?.container ?: return Result.success()
        if (!container.settings.joined.value) return Result.success()

        return when (val result = runCatching { container.syncer.syncNow() }.getOrNull()) {
            is SyncResult.Failed, null -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "ek-aur-sync"

        /**
         * Best-effort, like the pruner.
         *
         * A modified OEM build that refuses to schedule must not take the app
         * down on launch -- the leaderboard being stale is a far smaller
         * problem than the app not starting.
         */
        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        }
    }
}
