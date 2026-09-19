package com.ekaur.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw scroll events, kept for seven days.
 *
 * Only useful for debugging and for rebuilding a session after the fact. The
 * aggregate tables are what the app actually reads, so these are pruned
 * aggressively rather than kept forever.
 */
@Entity(tableName = "scroll_events", indices = [Index("timestampMs")])
data class ScrollEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestampMs: Long,
)

/**
 * Per-hour totals. Bounded at 24 rows a day, which makes the time-of-day
 * heatmap a cheap read rather than an aggregation over raw events.
 */
@Entity(tableName = "hourly_counts", primaryKeys = ["date", "hour", "packageName"])
data class HourlyCountEntity(
    /** Local date, `yyyy-MM-dd`. */
    val date: String,
    /** Local hour, 0-23. */
    val hour: Int,
    val packageName: String,
    val reelCount: Int,
)

/**
 * Per-day totals. The dashboard reads these, and they are the only thing that
 * will ever be uploaded -- raw events never leave the device.
 */
@Entity(tableName = "daily_counts", primaryKeys = ["date", "packageName"])
data class DailyCountEntity(
    /** Local date, `yyyy-MM-dd`. */
    val date: String,
    val packageName: String,
    val reelCount: Int,
    /** Time spent with the player on screen, accumulated from sessions. */
    val activeMs: Long = 0,
    /** Changed since the last upload. Unused until sync lands. */
    val dirty: Boolean = true,
    val lastSyncedAtMs: Long? = null,
)

/** One continuous sitting with the player open. */
@Entity(tableName = "sessions", indices = [Index("startedAtMs")])
data class SessionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val reelCount: Int,
) {
    val durationMs: Long get() = endedAtMs - startedAtMs
}

/** Dedupe record so a milestone fires at most once a day. Unused until later. */
@Entity(tableName = "milestones_fired", primaryKeys = ["date", "milestoneId"])
data class MilestoneFiredEntity(
    val date: String,
    val milestoneId: String,
    val firedAtMs: Long,
)
