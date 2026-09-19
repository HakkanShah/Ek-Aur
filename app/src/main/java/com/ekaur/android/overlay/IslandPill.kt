package com.ekaur.android.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The floating counter.
 *
 * Deliberately says nothing. No warning, no emoji, no encouragement -- just the
 * number, which is more damning than any copy would be. The only thing that
 * changes with the count is weight: the pill's colour drifts from white toward
 * the accent and then to red as the number climbs, so it grows heavier in the
 * corner of your eye without ever addressing you.
 */
@Composable
fun IslandPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val heat = heatFor(count)

    val numberColor by animateColorAsState(
        targetValue = lerpColor(PillChalk, PillHeat, heat),
        animationSpec = spring(),
        label = "pill-color",
    )

    // A small kick each time the number actually changes, so it registers
    // peripherally without demanding attention.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            pulse.snapTo(1.12f)
            pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 900f))
        }
    }

    Row(
        modifier = modifier
            .scale(pulse.value)
            .background(PillInk, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = count.toString(),
            color = numberColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 0f at rest, 1f once the count is frankly embarrassing. */
private fun heatFor(count: Int): Float = when {
    count <= 50 -> 0f
    count >= 400 -> 1f
    else -> (count - 50) / 350f
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * clamped,
        green = from.green + (to.green - from.green) * clamped,
        blue = from.blue + (to.blue - from.blue) * clamped,
        alpha = 1f,
    )
}

private val PillInk = Color(0xE60A0A0A)
private val PillChalk = Color(0xFFF2F2F2)
private val PillHeat = Color(0xFFFF3B1F)
