package com.ekaur.android.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekaur.android.data.repo.CounterRepository
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.FlatButton
import com.ekaur.android.ui.common.SectionLabel
import com.ekaur.android.ui.common.StatTile
import com.ekaur.android.ui.theme.Ash
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
    var range by remember { mutableStateOf(RANGES[1]) }
    var pickedHour by remember { mutableStateOf<Int?>(null) }
    var pickedDay by remember { mutableStateOf<Int?>(null) }

    // The dates the charts are drawn against, fixed for as long as the screen
    // is open. Also the window the rows are fetched for -- by date, never by a
    // row count, since one date can hold a row per tracked app.
    val dates = remember { repository.lastDays(RANGES.last()) }

    // Every flow is remembered. Each tap on a column recomposes this screen,
    // and rebuilding the flows here would tear down and resubscribe six Room
    // queries on every one of them.
    val today by remember { repository.observeTodayCount() }.collectAsState(initial = 0)
    val activeMs by remember { repository.observeTodayActiveMs() }.collectAsState(initial = 0L)
    val hourRows by remember { repository.observeTodayHours() }
        .collectAsState(initial = emptyList())
    val dayRows by remember(dates) { repository.observeDaysSince(dates.first()) }
        .collectAsState(initial = emptyList())
    val sessions by remember { repository.observeRecentSessions(SESSION_ROWS) }
        .collectAsState(initial = emptyList())
    val best by remember { repository.observeBestDay() }.collectAsState(initial = null)

    val hours = hourlySeries(hourRows)
    val days = dailySeries(dayRows, dates.takeLast(range))
    val weekTotal = dailySeries(dayRows, dates.takeLast(7)).sumOf { it.reels }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Today",
                value = today.toString(),
                caption = if (activeMs > 0) formatDuration(activeMs) else null,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "7 days",
                value = weekTotal.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Best day",
                value = best?.total?.toString() ?: "—",
                caption = best?.date?.let(::dayLabel),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        ChartCard(
            title = "Today by hour",
            // The tapped column's own number, so no value is locked behind a
            // gesture -- the readout replaces the headline rather than floating
            // over the chart, which has nowhere to float on a phone.
            // getOrNull, not [], throughout: a selection index outlives the
            // list it came from if the data shrinks under it, and a crash here
            // is a crash on a screen with no way to report itself.
            readout = pickedHour
                ?.let { i -> hours.getOrNull(i)?.let { "${hourLabel(i)}  ·  $it reels" } }
                ?: peakIndex(hours)?.let { "peak ${hourLabel(it)}  ·  ${hours[it]}" },
            empty = hours.all { it == 0 },
            emptyText = "Nothing today yet.",
        ) {
            ColumnChart(
                values = hours,
                selected = pickedHour,
                onSelect = { pickedHour = if (pickedHour == it) null else it },
            )
            Spacer(Modifier.height(8.dp))
            HourAxis()
        }

        Spacer(Modifier.height(12.dp))

        ChartCard(
            title = "Last $range days",
            readout = pickedDay
                ?.let { i ->
                    days.getOrNull(i)
                        ?.let { "${dayLongLabel(it.date)}  ·  ${it.reels} reels" }
                }
                ?: peakIndex(days.map { it.reels })
                    ?.let { "best ${dayLabel(days[it].date)}  ·  ${days[it].reels}" },
            empty = days.all { it.reels == 0 },
            emptyText = "No counts yet.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RANGES.forEach { option ->
                        FlatButton(
                            text = option.toString(),
                            emphasised = option == range,
                            onClick = {
                                range = option
                                // The old index would point at a different day.
                                pickedDay = null
                            },
                        )
                    }
                }
            },
        ) {
            ColumnChart(
                values = days.map { it.reels },
                selected = pickedDay,
                onSelect = { pickedDay = if (pickedDay == it) null else it },
            )
            Spacer(Modifier.height(8.dp))
            if (days.isNotEmpty()) {
                RangeAxis(from = days.first().date, to = days.last().date)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card {
            SectionLabel("Sessions")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A session = scrolling without a break",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
            Spacer(Modifier.height(12.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = "No sessions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                )
            } else {
                sessions.forEachIndexed { index, session ->
                    if (index > 0) Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${dayLabel(dateOf(session.startedAtMs))}, " +
                                timeLabel(session.startedAtMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Smoke,
                        )
                        Text(
                            text = "${formatDuration(session.durationMs)}  ·  " +
                                "${session.reelCount} reels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Chalk,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A titled card with a number under the title and a chart inside it.
 *
 * The readout line is where a tooltip would go on a screen that had room for
 * one: it names the tapped column, or the tallest when nothing is tapped.
 */
@Composable
private fun ChartCard(
    title: String,
    readout: String?,
    empty: Boolean,
    emptyText: String,
    action: @Composable (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(title)
            action?.invoke()
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = readout ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = if (readout == null) Ash else Chalk,
        )

        Spacer(Modifier.height(16.dp))

        if (empty) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
            )
        } else {
            chart()
        }
    }
}

private const val SESSION_ROWS = 8
