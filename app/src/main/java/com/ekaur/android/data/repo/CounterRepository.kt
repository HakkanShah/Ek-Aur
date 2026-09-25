package com.ekaur.android.data.repo

import androidx.room.withTransaction
import com.ekaur.android.data.local.DailyCountEntity
import com.ekaur.android.data.local.DayTotal
import com.ekaur.android.data.local.EkAurDatabase
import com.ekaur.android.data.local.MilestoneFiredEntity
import com.ekaur.android.data.local.ScrollEventEntity
import com.ekaur.android.data.local.SessionRecordEntity
import com.ekaur.android.detect.DetectionEvent
import com.ekaur.android.detect.TrackedApp
import com.ekaur.android.milestone.MilestoneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single place detection results become durable.
 *
 * The service hands [DetectionEvent]s here and nothing else writes counts, so
 * all the bucketing rules live in one file.
 */
class CounterRepository(
    private val db: EkAurDatabase,
    private val clock: DayClock = DayClock(),
) : MilestoneLog {

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

    /** Today's count per app, e.g. {Instagram=120, YouTube=45}. Missing apps are 0. */
    fun observeTodayByApp(nowMs: Long = System.currentTimeMillis()): Flow<Map<TrackedApp, Int>> =
        db.dailyCounts().observeDayByApp(clock.today(nowMs)).map { rows ->
            TrackedApp.entries.associateWith { app ->
                rows.filter { it.packageName == app.packageName }.sumOf { it.total }
            }
        }

    fun observeTodayActiveMs(nowMs: Long = System.currentTimeMillis()): Flow<Long> =
        db.dailyCounts().observeDayActiveMs(clock.today(nowMs))

    fun observeTodayHours(nowMs: Long = System.currentTimeMillis()) =
        db.hourlyCounts().observeDay(clock.today(nowMs))

    fun observeRecentSessions(limit: Int = 20) = db.sessions().observeRecent(limit)

    fun observeRecentDays(limit: Int = 30) = db.dailyCounts().observeRecent(limit)

    /** Every daily total from [from] onwards, for charting a date window. */
    fun observeDaysSince(from: String): Flow<List<DailyCountEntity>> =
        db.dailyCounts().observeSince(from)

    /** The heaviest day so far, or null until there has been one. */
    fun observeBestDay(): Flow<DayTotal?> =
        db.dailyCounts().observeBestDay().map { it.firstOrNull() }

    /** Local dates for the last [days] days, oldest first, to chart against. */
    fun lastDays(days: Int, nowMs: Long = System.currentTimeMillis()): List<String> =
        clock.lastDays(days, nowMs)

    /**
     * Which milestones have already been used up on [date].
     *
     * Durable rather than in-memory on purpose: the service is restarted often
     * enough -- by the OS, by a force stop, by an OEM battery killer -- that an
     * in-memory record would replay the same milestone several times a day.
     */
    override suspend fun firedOn(date: String): Set<String> =
        db.milestones().firedOn(date).toSet()

    override suspend fun markFired(date: String, milestoneId: String, atMs: Long) {
        // IGNOREs a clash, so two writes racing the same milestone leave one row.
        db.milestones().markFired(
            MilestoneFiredEntity(date = date, milestoneId = milestoneId, firedAtMs = atMs)
        )
    }

    /** Drops raw events past the retention window. Aggregates are never pruned. */
    suspend fun pruneRawEvents(nowMs: Long = System.currentTimeMillis()): Int =
        db.scrollEvents().deleteOlderThan(nowMs - RAW_RETENTION_MS)

    companion object {
        const val RAW_RETENTION_DAYS = 7
        const val RAW_RETENTION_MS = RAW_RETENTION_DAYS * 24L * 60 * 60 * 1000
    }
}
