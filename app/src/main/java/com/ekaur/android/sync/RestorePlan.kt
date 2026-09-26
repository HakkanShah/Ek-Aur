package com.ekaur.android.sync

import com.ekaur.android.data.local.DailyCountEntity
import com.ekaur.android.data.remote.LeaderboardSplit
import com.ekaur.android.detect.TrackedApp

/** One day as the server has it. */
data class ServerDay(
    val date: String,
    val total: Int,
    val reels: Int,
    val shorts: Int,
    val activeMs: Long,
)

/**
 * Bringing an account's history back onto a phone after a reinstall.
 *
 * An uninstall wipes the phone's database but not the server's daily totals,
 * and a recovered account used to come back with its name and nothing else:
 * no stats, no best day, today starting from zero. This decides which of the
 * server's days to write back, and how.
 *
 * The rule is "never lower anything": a day is taken from the server only when
 * the server has more than the phone. Anything counted on this phone that the
 * server hasn't seen yet stays, and gets uploaded as usual.
 */
object RestorePlan {

    /** The server days that should replace what the phone has for that date. */
    fun daysToWrite(server: List<ServerDay>, localTotals: Map<String, Int>): List<ServerDay> =
        server
            .filter { it.total > 0 && it.total > (localTotals[it.date] ?: 0) }
            .distinctBy { it.date }

    /**
     * The phone's rows for one restored day: Reels under Instagram, Shorts
     * under YouTube, marked as already synced so they aren't sent straight back.
     * Rows from before the split (only a combined total) count as Reels.
     */
    fun rowsFor(day: ServerDay, localActiveMs: Long, nowMs: Long): List<DailyCountEntity> {
        val split = LeaderboardSplit.of(total = day.total, reels = day.reels, shorts = day.shorts)
        val activeMs = maxOf(day.activeMs, localActiveMs)
        val rows = mutableListOf<DailyCountEntity>()
        if (split.reels > 0) {
            rows += DailyCountEntity(
                date = day.date,
                packageName = TrackedApp.Instagram.packageName,
                reelCount = split.reels,
                activeMs = activeMs,
                dirty = false,
                lastSyncedAtMs = nowMs,
            )
        }
        if (split.shorts > 0) {
            rows += DailyCountEntity(
                date = day.date,
                packageName = TrackedApp.YouTube.packageName,
                reelCount = split.shorts,
                // Time goes on one row only, so the day's total isn't doubled.
                activeMs = if (rows.isEmpty()) activeMs else 0L,
                dirty = false,
                lastSyncedAtMs = nowMs,
            )
        }
        return rows
    }
}
