package com.ekaur.android.sync

import com.ekaur.android.data.local.DailyCountEntity

/**
 * One day's totals, on their way up.
 *
 * Deliberately only a date, a count and a duration. Raw scroll events never
 * leave the phone, and this is the shape that decides it -- there is nowhere in
 * here to put one.
 */
data class DayUpload(
    val date: String,
    val reelCount: Int,
    val activeMs: Long,
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
    fun toUpload(dirty: List<DailyCountEntity>, limit: Int = MAX_BATCH): List<DayUpload> =
        dirty.asSequence()
            .sortedBy { it.date }
            .take(limit)
            .map { DayUpload(date = it.date, reelCount = it.reelCount, activeMs = it.activeMs) }
            .toList()

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
        return current.filter { row ->
            val uploaded = bySent[row.date] ?: return@filter false
            uploaded.reelCount == row.reelCount && uploaded.activeMs == row.activeMs
        }
    }
}
