package com.ekaur.android.data.repo

import androidx.room.withTransaction
import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.local.ScrollEventEntity
import com.ekaur.android.data.local.SessionRecordEntity
import com.ekaur.android.detect.DetectionEvent
import kotlinx.coroutines.flow.Flow

/**
 * The single place detection results become durable.
 *
 * The service hands [DetectionEvent]s here and nothing else writes counts, so
 * all the bucketing rules live in one file.
 */
class CounterRepository(
    private val db: EkAurDatabase,
    private val clock: DayClock = DayClock(),
) {

    /**
     * Records one scrolled reel.
     *
     * Raw event, hour bucket and day total are written together so a crash
     * between them cannot leave the aggregates disagreeing with each other.
     */
    suspend fun onReelScrolled(event: DetectionEvent.ReelScrolled) {
        val date = clock.dateOf(event.timestampMs)
        val hour = clock.hourOf(event.timestampMs)
        db.withTransaction {
            db.scrollEvents().insert(
                ScrollEventEntity(
                    packageName = event.packageName,
                    timestampMs = event.timestampMs,
                )
            )
            db.hourlyCounts().addToHour(date, hour, event.packageName, 1)
            db.dailyCounts().addToDay(date, event.packageName, 1)
        }
    }

    /**
     * Records a finished sitting.
     *
     * Time is attributed to the day the session started on, so a session that
     * runs past midnight stays whole rather than being split across two days.
     */
    suspend fun onSessionEnded(event: DetectionEvent.SessionEnded) {
        val date = clock.dateOf(event.startedAtMs)
        db.sessions().insert(
            SessionRecordEntity(
                packageName = event.packageName,
                startedAtMs = event.startedAtMs,
                endedAtMs = event.endedAtMs,
                reelCount = event.reelCount,
            )
        )
        if (event.durationMs > 0) {
            db.dailyCounts().addActiveMs(date, event.packageName, event.durationMs)
        }
    }

    fun observeTodayCount(nowMs: Long = System.currentTimeMillis()): Flow<Int> =
        db.dailyCounts().observeDayTotal(clock.today(nowMs))

    fun observeTodayActiveMs(nowMs: Long = System.currentTimeMillis()): Flow<Long> =
        db.dailyCounts().observeDayActiveMs(clock.today(nowMs))

    fun observeTodayHours(nowMs: Long = System.currentTimeMillis()) =
        db.hourlyCounts().observeDay(clock.today(nowMs))

    fun observeRecentSessions(limit: Int = 20) = db.sessions().observeRecent(limit)

    fun observeRecentDays(limit: Int = 30) = db.dailyCounts().observeRecent(limit)

    /** Drops raw events past the retention window. Aggregates are never pruned. */
    suspend fun pruneRawEvents(nowMs: Long = System.currentTimeMillis()): Int =
        db.scrollEvents().deleteOlderThan(nowMs - RAW_RETENTION_MS)

    companion object {
        const val RAW_RETENTION_DAYS = 7
        const val RAW_RETENTION_MS = RAW_RETENTION_DAYS * 24L * 60 * 60 * 1000
    }
}
