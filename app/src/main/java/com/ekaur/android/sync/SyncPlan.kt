package com.ekaur.android.sync

import com.ekaur.android.data.local.DailyCountEntity
import com.ekaur.android.detect.TrackedApp

/**
 * One day's totals, on their way up.
 *
 * Deliberately only a date, a count and a duration. Raw scroll events never
 * leave the phone, and this is the shape that decides it -- there is nowhere in
 * here to put one.
 */
data class DayUpload(
    val date: String,
    /** The combined total across every counted app -- what people are ranked on. */
    val reelCount: Int,
    val activeMs: Long,
    /** Instagram Reels' share of [reelCount]. */
    val reelsCount: Int = reelCount,
    /** YouTube Shorts' share of [reelCount]. */
    val shortsCount: Int = 0,
)

/**
 * What to send, and what to do with the answer.
 *
 * Pure: rows in, decisions out. The interesting failures here are all about
 * *not* losing a count -- a row must never be marked synced because a request
 * merely finished, and a row that changed while the request was in flight must
 * stay dirty.
 */
object SyncPlan {

    /** Never send an unbounded batch; a phone left offline for a month still fits. */
    const val MAX_BATCH = 60

    /**
     * The rows worth uploading, oldest first.
     *
     * Oldest first so a partial success leaves the *recent* days dirty, which
     * are the ones a friend is looking at.
     */
    fun toUpload(rows: List<DailyCountEntity>, limit: Int = MAX_BATCH): List<DayUpload> =
        rows.groupBy { it.date }
            .toSortedMap()
            .entries
            .take(limit)
            .map { (date, dayRows) -> dayOf(date, dayRows) }

    /**
     * One date's upload: every app's row summed.
     *
     * The server keeps one row per person per day. Sending each app's row on
     * its own would make the second overwrite the first, and the leaderboard
     * would show only one app's count -- so the day always goes up whole, with
     * the per-app split alongside.
     */
    private fun dayOf(date: String, dayRows: List<DailyCountEntity>) = DayUpload(
        date = date,
        reelCount = dayRows.sumOf { it.reelCount },
        activeMs = dayRows.sumOf { it.activeMs },
        reelsCount = dayRows.filter { it.packageName == TrackedApp.Instagram.packageName }.sumOf { it.reelCount },
        shortsCount = dayRows.filter { it.packageName == TrackedApp.YouTube.packageName }.sumOf { it.reelCount },
    )

    /** The dates worth uploading, oldest first, from the dirty rows. */
    fun datesToSend(dirty: List<DailyCountEntity>, limit: Int = MAX_BATCH): List<String> =
        dirty.map { it.date }.distinct().sorted().take(limit)

    /**
     * Which rows may be marked clean after a successful upload.
     *
     * A row is only cleared if what is on the device still matches what was
     * sent. Counting carries on during a request, so a reel scrolled mid-flight
     * would otherwise be marked as uploaded and never sent again.
     */
    fun syncedRows(
        sent: List<DayUpload>,
        current: List<DailyCountEntity>,
    ): List<DailyCountEntity> {
        val bySent = sent.associateBy { it.date }
        // Compared a whole day at a time: the day went up summed, so it is only
        // settled if the whole day still sums to exactly what was sent.
        return current.groupBy { it.date }.flatMap { (date, dayRows) ->
            val uploaded = bySent[date] ?: return@flatMap emptyList()
            if (dayOf(date, dayRows) == uploaded) dayRows else emptyList()
        }
    }
}
