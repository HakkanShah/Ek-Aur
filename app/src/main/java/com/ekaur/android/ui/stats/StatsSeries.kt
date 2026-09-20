package com.ekaur.android.ui.stats

import com.ekaur.android.data.local.DailyCountEntity
import com.ekaur.android.data.local.HourlyCountEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One column of the daily chart. */
data class DayPoint(val date: String, val reels: Int)

const val HOURS_IN_DAY = 24

/**
 * Daily totals for exactly [dates], oldest first.
 *
 * Zero-filled, so a day nobody scrolled is a gap in the chart rather than a day
 * that quietly disappears and shifts every later column left. Rows are summed
 * per date rather than taken one-per-date: `daily_counts` is keyed
 * `(date, packageName)`, so the day a second app is tracked, a single-row
 * assumption would be wrong.
 */
fun dailySeries(rows: List<DailyCountEntity>, dates: List<String>): List<DayPoint> {
    val totals = HashMap<String, Int>(rows.size)
    for (row in rows) {
        totals[row.date] = (totals[row.date] ?: 0) + row.reelCount
    }
    return dates.map { date -> DayPoint(date, totals[date] ?: 0) }
}

/**
 * Today's totals per hour: always 24 entries, whatever arrives.
 *
 * Hours outside 0..23 are dropped rather than trusted. They should not exist,
 * but a chart that throws on one bad row is worse than a chart that ignores it.
 */
fun hourlySeries(rows: List<HourlyCountEntity>): List<Int> {
    val totals = IntArray(HOURS_IN_DAY)
    for (row in rows) {
        if (row.hour in 0 until HOURS_IN_DAY) {
            totals[row.hour] += row.reelCount
        }
    }
    return totals.toList()
}

/**
 * Where the tallest column is, or null when there is nothing to point at.
 *
 * Null on an all-zero series matters: otherwise an untouched day prints
 * "peak 12am · 0", which is both wrong and sad. Ties go to the earliest.
 */
fun peakIndex(values: List<Int>): Int? {
    var index = -1
    var best = 0
    values.forEachIndexed { i, value ->
        if (value > best) {
            best = value
            index = i
        }
    }
    return index.takeIf { it >= 0 }
}

/** `0 -> "12am"`, `13 -> "1pm"`. Lowercase, like the rest of the app. */
fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12am"
    hour < 12 -> "${hour}am"
    hour == 12 -> "12pm"
    else -> "${hour - 12}pm"
}

/**
 * `"2026-09-14" -> "14 sep"`.
 *
 * A date that will not parse is shown as it is rather than throwing. An axis
 * label is not worth crashing a screen over.
 */
fun dayLabel(date: String): String =
    runCatching { LocalDate.parse(date).format(DAY_MONTH).lowercase(Locale.ENGLISH) }
        .getOrDefault(date)

/** `"2026-09-14" -> "sat 14 sep"`, for the tapped-column readout. */
fun dayLongLabel(date: String): String =
    runCatching { LocalDate.parse(date).format(WEEKDAY_DAY_MONTH).lowercase(Locale.ENGLISH) }
        .getOrDefault(date)

/** `"9:42pm"`, in the given zone. */
fun timeLabel(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(atMs).atZone(zone).format(CLOCK).lowercase(Locale.ENGLISH)

/**
 * The local date a timestamp falls on, in the same `yyyy-MM-dd` the database
 * stores, so a session can be labelled with the day it started.
 */
fun dateOf(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().toString()

/** `"34m"`, `"1h 12m"`, `"48s"` -- the app's one duration format. */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${totalMinutes}m"
        else -> "${ms / 1000}s"
    }
}

// Locale-pinned: month and weekday names must not change with the phone's
// language, or the axis reads differently on someone else's device.
private val DAY_MONTH: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private val WEEKDAY_DAY_MONTH: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private val CLOCK: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH)
