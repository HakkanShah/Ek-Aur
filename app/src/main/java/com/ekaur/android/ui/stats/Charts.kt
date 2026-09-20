package com.ekaur.android.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.AcidDim
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.InkLine

/**
 * A row of columns standing on a baseline.
 *
 * There is only ever one series here, which settles most of the design: the
 * column's *height* carries the magnitude, so shading them darker-where-taller
 * would encode the same thing twice and waste the only free channel. Every
 * column is one colour; the tallest, or the tapped one, gets the bright step of
 * that same colour. Nothing else on the chart is loud.
 */
@Composable
fun ColumnChart(
    values: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
) {
    // A flat interaction source: this app has no ripples anywhere, and one
    // spreading out of a 6dp-wide column looks like a rendering fault.
    val interaction = remember { MutableInteractionSource() }
    val peak = peakIndex(values)
    val tallest = (values.maxOrNull() ?: 0).coerceAtLeast(1)

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            values.forEachIndexed { index, value ->
                val lit = if (selected != null) index == selected else index == peak

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value > 0) {
                        Box(
                            Modifier
                                // Kept off its full slot width so the gap
                                // between columns does the separating -- a
                                // border drawn round each one would be ink
                                // that is not data.
                                .fillMaxWidth(BAR_FILL)
                                .widthIn(max = MAX_BAR)
                                // Floored, so a single reel is still visible
                                // next to a day that ran to three figures.
                                .height((height * (value.toFloat() / tallest)).coerceAtLeast(MIN_BAR))
                                .background(
                                    color = if (lit) Acid else AcidDim,
                                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                )
                        )
                    }
                }
            }
        }

        // Solid hairline, one step off the card. Never dashed -- a dashed rule
        // reads as a threshold when it is only a floor.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(InkLine)
        )
    }
}

/**
 * Labels under an hourly chart, each sitting over the column it names.
 *
 * Four of them rather than twenty-four: an axis is there to orient the reader,
 * and the exact numbers come from tapping.
 */
@Composable
fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        for (hour in HOUR_TICKS) {
            Text(
                text = hourLabel(hour),
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** First and last date under a daily chart. The days between are implied. */
@Composable
fun RangeAxis(from: String, to: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dayLabel(from), style = MaterialTheme.typography.bodyMedium, color = Ash)
        Text(dayLabel(to), style = MaterialTheme.typography.bodyMedium, color = Ash)
    }
}

/** A hairline spacer, used between rows of a list. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(InkLine)
    )
}

/** Every sixth hour, so the labels split the day into quarters. */
private val HOUR_TICKS = listOf(0, 6, 12, 18)

/** Columns never fill their slot; the leftover is deliberate air. */
private const val BAR_FILL = 0.6f
private val MAX_BAR = 24.dp
private val MIN_BAR = 2.dp
