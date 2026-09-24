package com.ekaur.android.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.ChipTone
import com.ekaur.android.ui.common.EmptyState
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.ScreenHeader
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.SegmentedToggle
import com.ekaur.android.ui.common.Skeleton
import com.ekaur.android.ui.common.StatTile
import com.ekaur.android.ui.common.StatusChip
import com.ekaur.android.ui.common.rememberToday
import com.ekaur.android.ui.common.reveal
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Smoke

/** How far back the daily chart can look. */
private val RANGES = listOf(7, 14, 30)

/**
 * The reckoning.
 *
 * Everything here is read from Room, so it survives a restart and matches the
 * pill exactly. The screen states numbers and nothing else -- no advice, no
 * warning, no "you've been scrolling a lot today". The graph says that without
 * help, which is the whole joke.
 */
@Composable
fun StatsScreen(
    repository: CounterRepository,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(RANGES[1]) }
    var pickedHour by remember { mutableStateOf<Int?>(null) }
    var pickedDay by remember { mutableStateOf<Int?>(null) }
    val today = rememberToday()

    // The dates the charts are drawn against. Keyed on the day, so a screen
    // left open overnight rolls over rather than charting yesterday as today.
    val dates = remember(today) { repository.lastDays(RANGES.last()) }

    // Every flow is remembered: rebuilding one would tear down and resubscribe
    // a Room query. They start as null, which means "still loading" -- not
    // "nothing yet" -- so the screen shows skeletons, not a false empty state.
    val todayCount by remember(today) { repository.observeTodayCount() }.collectAsState(initial = null)
    val activeMs by remember(today) { repository.observeTodayActiveMs() }.collectAsState(initial = 0L)
    val hourRows by remember(today) { repository.observeTodayHours() }.collectAsState(initial = null)
    val dayRows by remember(dates) { repository.observeDaysSince(dates.first()) }.collectAsState(initial = null)
    val sessions by remember { repository.observeRecentSessions(SESSION_ROWS) }.collectAsState(initial = null)
    val best by remember { repository.observeBestDay() }.collectAsState(initial = null)

    // Derived once per data change, never per tap.
    val hours = remember(hourRows) { hourRows?.let(::hourlySeries) }
    val allDays = remember(dayRows, dates) { dayRows?.let { dailySeries(it, dates) } }
    val days = remember(allDays, range) { allDays?.takeLast(range) }
    val dayValues = remember(days) { days?.map { it.reels } }
    val weekTotal = remember(allDays) { allDays?.takeLast(7)?.sumOf { it.reels } }
    val yesterday = allDays?.getOrNull(allDays.size - 2)?.reels

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader(
            title = "Stats",
            subtitle = "Your numbers. No judgement. Tap or drag a chart.",
            modifier = Modifier.reveal(0),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .reveal(1),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shownToday = todayCount ?: 0
            StatTile(
                label = "Today",
                value = shownToday.toString(),
                count = shownToday,
                delta = if (todayCount != null && yesterday != null && yesterday > 0) shownToday - yesterday else null,
                caption = if (activeMs > 0) formatDuration(activeMs) else "so far",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                label = "7 days",
                value = (weekTotal ?: 0).toString(),
                count = weekTotal ?: 0,
                caption = weekTotal?.let { "avg ${it / 7}/day" },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                label = "Best day",
                value = best?.total?.toString() ?: "—",
                count = best?.total,
                caption = best?.date?.let(::dayLabel) ?: "not yet",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(12.dp))

        ChartCard(
            title = "Today by hour",
            // getOrNull, not [], throughout: a selection index can outlive the
            // list it came from if the data shrinks under it.
            readout = when {
                hours == null -> null
                pickedHour != null -> hours.getOrNull(pickedHour!!)?.let { "${hourLabel(pickedHour!!)} · $it reels" }
                else -> peakIndex(hours)?.let { "Peak at ${hourLabel(it)} · ${hours[it]} reels" }
            },
            loading = hours == null,
            empty = hours != null && hours.all { it == 0 },
            emptyEmoji = "🌙",
            emptyTitle = "Nothing today yet.",
            emptyBody = "The chart fills in hour by hour as you scroll.",
            modifier = Modifier.reveal(2),
        ) {
            ColumnChart(
                values = hours.orEmpty(),
                selected = pickedHour,
                onSelect = { pickedHour = if (pickedHour == it) null else it },
                onScrub = { pickedHour = it },
                description = "Reels by hour today",
            )
            Spacer(Modifier.height(8.dp))
            HourAxis()
        }

        Spacer(Modifier.height(12.dp))

        ChartCard(
            title = "Last $range days",
            readout = when {
                days == null || dayValues == null -> null
                pickedDay != null -> days.getOrNull(pickedDay!!)?.let { "${dayLongLabel(it.date)} · ${it.reels} reels" }
                else -> peakIndex(dayValues)?.let {
                    val avg = dayValues.sum() / dayValues.size.coerceAtLeast(1)
                    "Avg $avg a day · best ${dayLabel(days[it].date)} (${days[it].reels})"
                }
            },
            loading = days == null,
            empty = dayValues != null && dayValues.all { it == 0 },
            emptyEmoji = "📉",
            emptyTitle = "No counts yet.",
            emptyBody = "Come back after a scroll or two. It remembers everything.",
            action = {
                SegmentedToggle(
                    options = RANGES.map { "${it}d" },
                    selectedIndex = RANGES.indexOf(range),
                    segmentWidth = 46.dp,
                    onSelect = {
                        range = RANGES[it]
                        // The old index would point at a different day.
                        pickedDay = null
                    },
                )
            },
            modifier = Modifier.reveal(3),
        ) {
            ColumnChart(
                values = dayValues.orEmpty(),
                selected = pickedDay,
                onSelect = { pickedDay = if (pickedDay == it) null else it },
                onScrub = { pickedDay = it },
                description = "Reels per day, last $range days",
            )
            Spacer(Modifier.height(8.dp))
            RangeAxis(days.orEmpty().map { it.date })
        }

        Spacer(Modifier.height(12.dp))

        SessionsCard(sessions = sessions, modifier = Modifier.reveal(4))

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SessionsCard(
    sessions: List<com.ekaur.android.data.local.SessionRecordEntity>?,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        SectionLabel("Sessions")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "One session = scrolling without a break.",
            style = MaterialTheme.typography.bodyMedium,
            color = Smoke,
        )
        Spacer(Modifier.height(10.dp))

        when {
            sessions == null -> repeat(3) {
                Skeleton(Modifier.fillMaxWidth().height(20.dp))
                Spacer(Modifier.height(14.dp))
            }
            sessions.isEmpty() -> EmptyState(
                emoji = "⏱️",
                title = "No sessions yet.",
                body = "Open Instagram and give it a minute.",
            )
            else -> {
                val longest = remember(sessions) { sessions.maxByOrNull { it.durationMs } }
                sessions.forEachIndexed { index, session ->
                    if (index > 0) Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${dayLabel(dateOf(session.startedAtMs))}, ${timeLabel(session.startedAtMs)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Chalk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${session.reelCount} reels",
                                style = MaterialTheme.typography.bodySmall,
                                color = Smoke,
                            )
                        }
                        if (session === longest && sessions.size > 1) {
                            StatusChip("Longest", ChipTone.Accent)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = formatDuration(session.durationMs),
                            style = MaterialTheme.typography.titleSmall,
                            color = Chalk,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A titled card with a readout under the title and a chart inside it.
 *
 * The readout line is where a tooltip would go on a screen that had room for
 * one: it names the tapped column, or sums the chart up when nothing is.
 */
@Composable
private fun ChartCard(
    title: String,
    readout: String?,
    loading: Boolean,
    empty: Boolean,
    emptyEmoji: String,
    emptyTitle: String,
    emptyBody: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    Card(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(title, Modifier.weight(1f))
            action?.invoke()
        }

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = readout ?: "—",
            transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) },
            label = "readout",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (readout == null) Smoke else Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading -> Skeleton(Modifier.fillMaxWidth().height(132.dp), corner = 16.dp)
            empty -> EmptyState(emoji = emptyEmoji, title = emptyTitle, body = emptyBody)
            else -> chart()
        }
    }
}

private const val SESSION_ROWS = 8
