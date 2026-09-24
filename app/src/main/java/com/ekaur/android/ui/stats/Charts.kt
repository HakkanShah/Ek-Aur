package com.ekaur.android.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.ui.common.Motion
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.theme.AcidDim
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.Poppins
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.instaGradient

/**
 * A row of columns standing on a baseline, drawn in one Canvas.
 *
 * There is only ever one series here, which settles most of the design: the
 * column's *height* carries the magnitude, so every column is one soft colour
 * and only the tallest -- or the one under your finger -- wears the gradient.
 *
 * - Columns grow in, staggered, the first time and whenever the range changes;
 *   a new value eases from the old height rather than jumping.
 * - Tap a column, or drag across the chart to scrub, with a light tick per
 *   column; a small bubble names the value over the chosen one.
 *
 * One Canvas instead of a Box per column: a 30-day chart used to be 60
 * composables, each recomposed on every tap.
 */
@Composable
fun ColumnChart(
    values: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    onScrub: ((Int) -> Unit)? = null,
    description: String = "",
) {
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    val scrub by rememberUpdatedState(onScrub ?: onSelect)
    val peak = peakIndex(values)
    val count = values.size.coerceAtLeast(1)

    // Heights morph from what was shown to what is now true. A change of
    // length (the range toggle) restarts from the floor so the columns grow.
    var from by remember { mutableStateOf(List(values.size) { 0 }) }
    var to by remember { mutableStateOf(values) }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(values) {
        val current = to
        from = if (current.size == values.size) {
            // Where the bars are right now, so an interrupted morph continues.
            current.indices.map { i ->
                val start = from.getOrElse(i) { 0 }
                (start + (current[i] - start) * morph.value).toInt()
            }
        } else {
            List(values.size) { 0 }
        }
        to = values
        morph.snapTo(0f)
        morph.animateTo(1f, if (current.size == values.size) Motion.standard() else Motion.emphasised())
    }

    val bubbleStyle = remember {
        TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { if (description.isNotEmpty()) contentDescription = description }
            .pointerInput(count) {
                detectTapGestures { offset ->
                    val i = (offset.x / (size.width / count.toFloat())).toInt().coerceIn(0, count - 1)
                    haptics.tick()
                    select(i)
                }
            }
            .pointerInput(count) {
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { last = -1 },
                ) { change, _ ->
                    change.consume()
                    val i = (change.position.x / (size.width / count.toFloat())).toInt().coerceIn(0, count - 1)
                    if (i != last) {
                        last = i
                        haptics.tick()
                        scrub(i)
                    }
                }
            },
    ) {
        val bubbleRoom = 24.dp.toPx()
        val chartHeight = size.height - bubbleRoom
        val slot = size.width / count
        val barWidth = (slot * BAR_FILL).coerceAtMost(MAX_BAR.toPx())
        val minBar = MIN_BAR.toPx()
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        val t = morph.value
        val shown = to.indices.map { i ->
            val start = from.getOrElse(i) { 0 }.toFloat()
            // A small stagger across the chart, so the bars ripple up.
            val lag = (i.toFloat() / count) * 0.35f
            val p = ((t - lag) / (1f - lag)).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            start + (to[i] - start) * eased
        }
        val tallest = (maxOf(to.maxOrNull() ?: 0, from.maxOrNull() ?: 0)).coerceAtLeast(1).toFloat()

        shown.forEachIndexed { i, value ->
            if (value <= 0f) return@forEachIndexed
            val h = (chartHeight * value / tallest).coerceAtLeast(minBar)
            val left = slot * i + (slot - barWidth) / 2f
            val lit = if (selected != null) i == selected else i == peak
            drawBar(left, size.height - h, barWidth, h, radius, lit)
        }

        // Solid hairline floor. Never dashed -- a dashed rule reads as a
        // threshold when it is only a floor.
        drawRect(InkLine, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))

        // The value bubble over the chosen column.
        val chosen = selected
        if (chosen != null && chosen in to.indices) {
            val label = measurer.measure(to[chosen].toString(), bubbleStyle)
            val padX = 7.dp.toPx()
            val padY = 3.dp.toPx()
            val w = label.size.width + padX * 2
            val hgt = label.size.height + padY * 2
            val barH = (chartHeight * shown[chosen] / tallest).coerceAtLeast(if (shown[chosen] > 0f) minBar else 0f)
            val cx = slot * chosen + slot / 2f
            val left = (cx - w / 2f).coerceIn(0f, size.width - w)
            val top = (size.height - barH - hgt - 4.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(Chalk, topLeft = Offset(left, top), size = Size(w, hgt), cornerRadius = CornerRadius(hgt / 2f))
            drawText(label, topLeft = Offset(left + padX, top + padY))
        }
    }
}

private fun DrawScope.drawBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: CornerRadius,
    lit: Boolean,
) {
    if (lit) {
        drawRoundRect(
            brush = instaGradient(),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = radius,
        )
    } else {
        drawRoundRect(
            color = AcidDim,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = radius,
        )
    }
}

/**
 * Labels under an hourly chart. Each of the four sits at the start of its
 * six-hour quarter, so "6am" is over the 6am column.
 */
@Composable
fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        for (hour in HOUR_TICKS) {
            Text(
                text = hourLabel(hour),
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** First, middle and last date under a daily chart. */
@Composable
fun RangeAxis(dates: List<String>, modifier: Modifier = Modifier) {
    if (dates.isEmpty()) return
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(dayLabel(dates.first()), style = MaterialTheme.typography.bodySmall, color = Smoke)
        if (dates.size > 2) {
            Text(dayLabel(dates[dates.size / 2]), style = MaterialTheme.typography.bodySmall, color = Smoke)
        }
        Text("Today", style = MaterialTheme.typography.bodySmall, color = Smoke)
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
private const val BAR_FILL = 0.62f
private val MAX_BAR = 24.dp
private val MIN_BAR = 3.dp
