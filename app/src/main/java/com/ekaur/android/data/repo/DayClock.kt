package com.ekaur.android.data.repo

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns a timestamp into the local day and hour it belongs to.
 *
 * Buckets follow local wall-clock time, because "today" to a person means until
 * they go to sleep, not until UTC midnight. Injectable so tests can pin a zone
 * instead of depending on whatever the machine is set to.
 */
class DayClock(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun dateOf(timestampMs: Long): String =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate().format(FORMAT)

    fun hourOf(timestampMs: Long): Int =
        Instant.ofEpochMilli(timestampMs).atZone(zone).hour

    fun today(nowMs: Long = System.currentTimeMillis()): String = dateOf(nowMs)

    /** Local dates for the last [days] days, oldest first, including today. */
    fun lastDays(days: Int, nowMs: Long = System.currentTimeMillis()): List<String> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return (days - 1 downTo 0).map { back -> today.minusDays(back.toLong()).format(FORMAT) }
    }

    private companion object {
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
