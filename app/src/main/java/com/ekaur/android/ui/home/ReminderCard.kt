package com.ekaur.android.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekaur.android.di.AppContainer
import com.ekaur.android.reminder.ReminderPlan
import com.ekaur.android.reminder.ReminderSettings
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.common.AppWords
import com.ekaur.android.ui.common.Card
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import com.ekaur.android.ui.common.SegmentedToggle
import com.ekaur.android.ui.common.rememberHaptics
import com.ekaur.android.ui.theme.Acid
import com.ekaur.android.ui.theme.Ash
import com.ekaur.android.ui.theme.Chalk
import com.ekaur.android.ui.theme.Heat
import com.ekaur.android.ui.theme.Smoke
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient
import kotlin.math.roundToInt

/**
 * The scroll reminder, on Home.
 *
 * Read at a glance: a ring filling toward the next popup, one line saying
 * where things stand, and the switch. Switched on, two controls open under
 * it -- a slider for the number (it snaps to sensible stops) and how many
 * more "remind me later" gives -- and nothing else.
 */
@Composable
fun ReminderCard(
    container: AppContainer,
    count: Int,
    today: String,
    overlayAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val store = container.settings
    val settings by store.reminder.collectAsState()
    val stored by store.reminderDayFlow.collectAsState()
    val apps by store.countedApps.collectAsState()
    val unit = AppWords.unit(apps).lowercase()
    val day = ReminderPlan.dayFor(settings, stored, today)
    val status = ReminderPlan.status(settings, day, count)

    fun update(next: ReminderSettings) = store.updateReminder(next, today)

    val (line, progress) = when (status) {
        ReminderPlan.Status.Off -> "Get a popup when you hit a number you pick." to 0f
        ReminderPlan.Status.DoneForToday -> "Done for today. Back tomorrow." to 1f
        is ReminderPlan.Status.OnNextReel -> "Past ${status.at}. Pops up on your next one." to 1f
        is ReminderPlan.Status.Waiting -> {
            val from = if (status.snoozed) status.at - settings.snooze else 0
            val span = (status.at - from).coerceAtLeast(1)
            val text = if (status.snoozed) {
                "Snoozed · ${status.toGo} to go, at ${status.at}"
            } else {
                "${status.toGo} to go · pops up at ${status.at}"
            }
            text to ((count - from).toFloat() / span).coerceIn(0f, 1f)
        }
    }

    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(progress = progress, on = settings.enabled)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Scroll reminder", style = MaterialTheme.typography.titleMedium, color = Chalk)
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = line,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "reminder-line",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Smoke)
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = settings.enabled,
                onCheckedChange = { on ->
                    haptics.tick()
                    update(settings.copy(enabled = on))
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Acid,
                    checkedThumbColor = Color.White,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Smoke,
                    uncheckedTrackColor = SurfaceLav,
                    uncheckedBorderColor = Ash,
                ),
            )
        }

        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "Pop up at",
                        style = MaterialTheme.typography.labelMedium,
                        color = Smoke,
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                    )
                    AnimatedContent(
                        targetState = settings.at,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "reminder-at",
                    ) { at ->
                        Text(
                            text = "$at",
                            style = MaterialTheme.typography.headlineSmall.merge(TextStyle(brush = instaGradient())),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = Smoke, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                val stops = ReminderSettings.STOPS
                val index = stops.indexOfFirst { it >= settings.at }.let { if (it < 0) stops.lastIndex else it }
                StopSlider(
                    index = index,
                    count = stops.size,
                    onIndex = { i ->
                        haptics.tick()
                        update(settings.copy(at = stops[i]))
                    },
                    description = "Pop up at ${settings.at} $unit",
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("${stops.first()}", style = MaterialTheme.typography.labelSmall, color = Ash)
                    Spacer(Modifier.weight(1f))
                    Text("${stops.last()}", style = MaterialTheme.typography.labelSmall, color = Ash)
                }

                Spacer(Modifier.height(16.dp))
                Text("Remind me later gives", style = MaterialTheme.typography.labelMedium, color = Smoke)
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val snoozes = ReminderSettings.SNOOZES
                    SegmentedToggle(
                        options = snoozes.map { "+$it" },
                        selectedIndex = snoozes.indexOf(settings.snooze).coerceAtLeast(0),
                        onSelect = { i -> update(settings.copy(snooze = snoozes[i])) },
                        // Full width: the toggle adds 3dp of padding each side.
                        segmentWidth = (maxWidth - 6.dp) / snoozes.size,
                    )
                }

                if (!overlayAllowed) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { ServiceControl.openOverlaySettings(context) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EkIcon(EkIcons.Warning, tint = Heat, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "The popup needs Overlay permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Smoke,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Allow", style = MaterialTheme.typography.labelMedium, color = Acid)
                    }
                }
            }
        }
    }
}

/** A bell in a ring that fills toward the next popup. */
@Composable
private fun GoalRing(progress: Float, on: Boolean) {
    val sweep by animateFloatAsState(
        targetValue = if (on) progress else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "reminder-ring",
    )
    val gradient = instaGradient()
    val track = SurfaceLav
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (sweep > 0f) {
                drawArc(
                    brush = gradient,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        EkIcon(EkIcons.Bell, tint = if (on) Acid else Smoke, size = 20.dp)
    }
}

/**
 * A slider that snaps to [count] stops: a gradient-filled track and a white
 * thumb with a gradient core. Drawn by hand so the fill can carry the brand
 * gradient; tap or drag anywhere on it.
 */
@Composable
private fun StopSlider(index: Int, count: Int, onIndex: (Int) -> Unit, description: String) {
    val current by rememberUpdatedState(index)
    val pick by rememberUpdatedState(onIndex)
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction by animateFloatAsState(
        targetValue = index / (count - 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "reminder-slider",
    )
    val gradient = buttonGradient()
    val track = SurfaceLav

    fun select(x: Float, radius: Float) {
        val usable = (widthPx - 2 * radius).coerceAtLeast(1f)
        val i = (((x - radius) / usable) * (count - 1)).roundToInt().coerceIn(0, count - 1)
        if (i != current) pick(i)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .semantics { contentDescription = description }
            .pointerInput(count) {
                val r = 13.dp.toPx()
                detectTapGestures { select(it.x, r) }
            }
            .pointerInput(count) {
                val r = 13.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    select(change.position.x, r)
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = 13.dp.toPx()
            val h = 8.dp.toPx()
            val cy = size.height / 2f
            val left = r
            val right = size.width - r
            val x = left + (right - left) * fraction
            drawRoundRect(track, Offset(left - h / 2, cy - h / 2), Size(right - left + h, h), CornerRadius(h / 2))
            drawRoundRect(gradient, Offset(left - h / 2, cy - h / 2), Size(x - left + h, h), CornerRadius(h / 2))
            drawCircle(Color.Black.copy(alpha = 0.06f), r + 1.dp.toPx(), Offset(x, cy + 0.5.dp.toPx()))
            drawCircle(Color.White, r, Offset(x, cy))
            drawCircle(gradient, r * 0.42f, Offset(x, cy))
        }
    }
}
