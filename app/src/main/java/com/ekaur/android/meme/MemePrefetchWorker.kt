package com.ekaur.android.meme

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ekaur.android.EkAurApp
import java.util.concurrent.TimeUnit

/**
 * Keeps the reminder popup's meme stash full, in the background.
 *
 * WorkManager runs it as soon as there is a network -- even with the counting
 * service off, and after a reboot -- and retries with backoff if GIPHY can't
 * be reached, until the stash is full. So by the time a reminder fires the
 * memes are already on the phone.
 */
class MemePrefetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? EkAurApp)?.container ?: return Result.success()
        if (!container.settings.reminderSettings.enabled) return Result.success()
        val memes = container.memes
        if (!memes.hasKey) return Result.success()
        return if (memes.refill()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "ek-aur-memes"

        /** Tops the stash up soon. Best-effort: never take the app down over a meme. */
        fun schedule(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<MemePrefetchWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                // KEEP: a run already waiting (say, for the network) covers this ask.
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
