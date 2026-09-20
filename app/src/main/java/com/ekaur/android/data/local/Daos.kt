package com.ekaur.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollEventDao {
    @Insert
    suspend fun insert(event: ScrollEventEntity)

    @Query("DELETE FROM scroll_events WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM scroll_events")
    suspend fun count(): Int
}

@Dao
interface DailyCountDao {
    /**
     * Adds to a day's total, creating the row on first sight.
     *
     * Written as an upsert in SQL rather than read-modify-write in Kotlin so
     * concurrent scroll events cannot lose an increment.
     */
    @Query(
        """
        INSERT INTO daily_counts (date, packageName, reelCount, activeMs, dirty, lastSyncedAtMs)
        VALUES (:date, :packageName, :delta, 0, 1, NULL)
        ON CONFLICT(date, packageName) DO UPDATE SET
            reelCount = reelCount + :delta,
            dirty = 1
        """
    )
    suspend fun addToDay(date: String, packageName: String, delta: Int)

    @Query(
        """
        INSERT INTO daily_counts (date, packageName, reelCount, activeMs, dirty, lastSyncedAtMs)
        VALUES (:date, :packageName, 0, :deltaMs, 1, NULL)
        ON CONFLICT(date, packageName) DO UPDATE SET
            activeMs = activeMs + :deltaMs,
            dirty = 1
        """
    )
    suspend fun addActiveMs(date: String, packageName: String, deltaMs: Long)

    @Query("SELECT COALESCE(SUM(reelCount), 0) FROM daily_counts WHERE date = :date")
    fun observeDayTotal(date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(activeMs), 0) FROM daily_counts WHERE date = :date")
    fun observeDayActiveMs(date: String): Flow<Long>

    @Query("SELECT * FROM daily_counts WHERE date = :date")
    suspend fun forDate(date: String): List<DailyCountEntity>

    @Query("SELECT * FROM daily_counts ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyCountEntity>>

    /**
     * Every row from [from] onwards, by date rather than by row count.
     *
     * A row limit would be wrong here: the table is keyed `(date, packageName)`,
     * so the day a second app is tracked, "the last 30 rows" becomes the last
     * fifteen days and the chart silently loses half its range.
     */
    @Query("SELECT * FROM daily_counts WHERE date >= :from ORDER BY date DESC")
    fun observeSince(from: String): Flow<List<DailyCountEntity>>

    /** Days changed since their last upload, oldest first. */
    @Query("SELECT * FROM daily_counts WHERE dirty = 1 ORDER BY date ASC LIMIT :limit")
    suspend fun dirtyRows(limit: Int): List<DailyCountEntity>

    /**
     * Clears the dirty flag for a day that was uploaded unchanged.
     *
     * The count is part of the WHERE clause on purpose: counting carries on
     * while a request is in flight, so a row that grew mid-upload must stay
     * dirty rather than be marked as sent.
     */
    @Query(
        """
        UPDATE daily_counts
        SET dirty = 0, lastSyncedAtMs = :atMs
        WHERE date = :date AND packageName = :packageName
          AND reelCount = :reelCount AND activeMs = :activeMs
        """
    )
    suspend fun markSynced(
        date: String,
        packageName: String,
        reelCount: Int,
        activeMs: Long,
        atMs: Long,
    ): Int

    /**
     * The heaviest day on record, summed across apps.
     *
     * Returned as a list rather than a nullable row so an empty database is an
     * empty list -- the ordinary case on a fresh install, not a null to handle.
     */
    @Query(
        """
        SELECT date, SUM(reelCount) AS total FROM daily_counts
        GROUP BY date
        ORDER BY total DESC, date DESC
        LIMIT 1
        """
    )
    fun observeBestDay(): Flow<List<DayTotal>>
}

@Dao
interface HourlyCountDao {
    @Query(
        """
        INSERT INTO hourly_counts (date, hour, packageName, reelCount)
        VALUES (:date, :hour, :packageName, :delta)
        ON CONFLICT(date, hour, packageName) DO UPDATE SET
            reelCount = reelCount + :delta
        """
    )
    suspend fun addToHour(date: String, hour: Int, packageName: String, delta: Int)

    @Query("SELECT * FROM hourly_counts WHERE date = :date ORDER BY hour")
    fun observeDay(date: String): Flow<List<HourlyCountEntity>>

    @Query("SELECT * FROM hourly_counts WHERE date = :date ORDER BY hour")
    suspend fun forDate(date: String): List<HourlyCountEntity>
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionRecordEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC")
    suspend fun all(): List<SessionRecordEntity>
}

@Dao
interface MilestoneDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markFired(entity: MilestoneFiredEntity): Long

    @Query("SELECT milestoneId FROM milestones_fired WHERE date = :date")
    suspend fun firedOn(date: String): List<String>
}
