package com.ekaur.android.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ekaur.android.EkAurApp
import java.util.concurrent.TimeUnit

/**
 * Drops raw scroll events past their retention window.
 *
 * Aggregates are kept forever -- they are small and they are the product. Only
 * the raw event log, which exists for debugging, is trimmed.
 */
class PruneWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? EkAurApp)?.container ?: return Result.success()
        return runCatching { container.counterRepository.pruneRawEvents() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "prune-raw-events"

        /**
         * Best-effort. Trimming a debug log is maintenance, so failing to
         * schedule it must never take the app down at startup -- WorkManager
         * initialisation is exactly the kind of thing a modified OEM build
         * breaks, and the app still counts perfectly well without pruning.
         */
        fun schedule(context: Context): Boolean = runCatching {
            val request = PeriodicWorkRequestBuilder<PruneWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }.isSuccess
    }
}
