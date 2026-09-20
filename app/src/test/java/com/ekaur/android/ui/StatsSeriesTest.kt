package com.ekaur.android.ui

import com.ekaur.android.data.local.DailyCountEntity
import com.ekaur.android.data.local.HourlyCountEntity
import com.ekaur.android.ui.stats.HOURS_IN_DAY
import com.ekaur.android.ui.stats.dailySeries
import com.ekaur.android.ui.stats.dateOf
import com.ekaur.android.ui.stats.dayLabel
import com.ekaur.android.ui.stats.dayLongLabel
import com.ekaur.android.ui.stats.formatDuration
import com.ekaur.android.ui.stats.hourLabel
import com.ekaur.android.ui.stats.hourlySeries
import com.ekaur.android.ui.stats.peakIndex
import com.ekaur.android.ui.stats.timeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

private const val IG = "com.instagram.android"
private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

private fun day(date: String, reels: Int, pkg: String = IG) =
    DailyCountEntity(date = date, packageName = pkg, reelCount = reels)

private fun hour(hour: Int, reels: Int, pkg: String = IG) =
    HourlyCountEntity(date = "2026-09-20", hour = hour, packageName = pkg, reelCount = reels)

class StatsSeriesTest {

    @Test
    fun `a day with no scrolling is a zero, not a missing column`() {
        // Dropping the empty day would shift every later column left, so the
        // chart would quietly lie about which day was which.
        val dates = listOf("2026-09-18", "2026-09-19", "2026-09-20")
        val series = dailySeries(listOf(day("2026-09-18", 40), day("2026-09-20", 12)), dates)

        assertEquals(listOf(40, 0, 12), series.map { it.reels })
        assertEquals(dates, series.map { it.date })
    }

    @Test
    fun `rows arriving newest first still land on the right day`() {
        // The DAO returns them ORDER BY date DESC.
        val dates = listOf("2026-09-18", "2026-09-19", "2026-09-20")
        val series = dailySeries(
            rows = listOf(day("2026-09-20", 12), day("2026-09-19", 5), day("2026-09-18", 40)),
            dates = dates,
        )

        assertEquals(listOf(40, 5, 12), series.map { it.reels })
    }

    @Test
    fun `two apps on one day are added together`() {
        // daily_counts is keyed (date, packageName), so one date can hold more
        // than one row. Taking a single row would be a crash in waiting.
        val series = dailySeries(
            rows = listOf(day("2026-09-20", 12), day("2026-09-20", 8, "com.zhiliaoapp.musically")),
            dates = listOf("2026-09-20"),
        )

        assertEquals(20, series.single().reels)
    }

    @Test
    fun `a row outside the window is ignored`() {
        val series = dailySeries(
            rows = listOf(day("2020-01-01", 999), day("2026-09-20", 3)),
            dates = listOf("2026-09-20"),
        )

        assertEquals(listOf(3), series.map { it.reels })
    }

    @Test
    fun `an empty history still produces a full row of columns`() {
        val dates = listOf("2026-09-18", "2026-09-19", "2026-09-20")

        assertEquals(listOf(0, 0, 0), dailySeries(emptyList(), dates).map { it.reels })
    }

    @Test
    fun `an hourly series is always twenty four long`() {
        assertEquals(HOURS_IN_DAY, hourlySeries(emptyList()).size)
        assertEquals(HOURS_IN_DAY, hourlySeries(listOf(hour(3, 5))).size)
    }

    @Test
    fun `hours land in their own bucket and are summed`() {
        val series = hourlySeries(
            listOf(hour(3, 5), hour(23, 2), hour(3, 1, "com.zhiliaoapp.musically")),
        )

        assertEquals(6, series[3])
        assertEquals(2, series[23])
        assertEquals(0, series[4])
    }

    @Test
    fun `an impossible hour is dropped rather than thrown on`() {
        // Should never happen. A chart that ignores one bad row still draws;
        // one that throws takes the whole screen down.
        val series = hourlySeries(listOf(hour(24, 9), hour(-1, 9), hour(0, 4)))

        assertEquals(4, series[0])
        assertEquals(4, series.sum())
    }

    @Test
    fun `a day nobody scrolled has no peak`() {
        // Otherwise the card reads "peak 12am - 0", which is wrong and sad.
        assertNull(peakIndex(List(HOURS_IN_DAY) { 0 }))
        assertNull(peakIndex(emptyList()))
    }

    @Test
    fun `the peak is the tallest column, earliest on a tie`() {
        assertEquals(2, peakIndex(listOf(1, 4, 9, 9, 0)))
        assertEquals(0, peakIndex(listOf(3, 0, 0)))
    }

    @Test
    fun `hour labels read like a clock`() {
        assertEquals("12am", hourLabel(0))
        assertEquals("6am", hourLabel(6))
        assertEquals("12pm", hourLabel(12))
        assertEquals("1pm", hourLabel(13))
        assertEquals("11pm", hourLabel(23))
    }

    @Test
    fun `dates read the same on every phone`() {
        // Pinned to English: month names must not follow the device language,
        // or two people comparing screenshots see different axes.
        assertEquals("14 sep", dayLabel("2026-09-14"))
        assertEquals("mon 14 sep", dayLongLabel("2026-09-14"))
    }

    @Test
    fun `an unparseable date is shown as it is`() {
        assertEquals("not-a-date", dayLabel("not-a-date"))
    }

    @Test
    fun `a session start reads as a wall clock time`() {
        val at = ZonedDateTime.of(2026, 9, 20, 21, 42, 0, 0, ZONE).toInstant().toEpochMilli()

        assertEquals("9:42pm", timeLabel(at, ZONE))
    }

    @Test
    fun `a session is dated by the local day it started on`() {
        // Half an hour before midnight in Kolkata is still the previous day,
        // whatever UTC thinks -- the same rule the database buckets by.
        val lateNight = ZonedDateTime.of(2026, 9, 20, 23, 30, 0, 0, ZONE)
            .toInstant().toEpochMilli()

        assertEquals("2026-09-20", dateOf(lateNight, ZONE))
    }

    @Test
    fun `durations shorten sensibly`() {
        assertEquals("48s", formatDuration(48_000))
        assertEquals("34m", formatDuration(34 * 60_000L))
        assertEquals("1h 12m", formatDuration(72 * 60_000L))
        assertEquals("2h 0m", formatDuration(120 * 60_000L))
    }
}
