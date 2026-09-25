package com.ekaur.android.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * What app is in front, read from usage stats rather than accessibility.
 *
 * The accessibility service is scoped to Instagram and cannot see any other
 * app, which is deliberate. Knowing when the user has *left* Instagram -- to
 * bring the pill down and to switch the service off for a payment -- therefore
 * comes from here instead. Usage access is a plain read of which app is
 * foreground; it carries no screen content and does not trip a bank's checks.
 *
 * A poll rather than a callback, because that is all `UsageStatsManager`
 * offers. The service already ticks, so this is read there, throttled.
 */
object ForegroundWatch {

    /** How far back to look for the most recent foreground change. */
    private const val WINDOW_MS = 10_000L

    /**
     * The package currently in front, or null if it cannot be told.
     *
     * Walks the recent event stream and keeps the last app that moved to the
     * foreground. Null is treated by callers as "unknown, assume unchanged" --
     * never as "left", because a wrong "left" would switch counting off while
     * the user is still scrolling.
     */
    fun currentForegroundPackage(
        context: Context,
        nowMs: Long,
        ignore: (String) -> Boolean = { false },
    ): String? {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null

        val events = runCatching {
            manager.queryEvents(nowMs - WINDOW_MS, nowMs)
        }.getOrNull() ?: return null

        var latest: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // A system screen that popped over the app (see ForegroundPolicy)
            // is skipped, so the app underneath it is still the answer.
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && !ignore(event.packageName)) {
                latest = event.packageName
            }
        }
        return latest
    }
}
