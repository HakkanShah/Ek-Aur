package com.ekaur.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.copy.SarcasmCatalogue

/**
 * The floating counter.
 *
 * Rests as a compact pill -- a face and a number. Every
 * [SarcasmCatalogue.MILESTONE_EVERY] reels it briefly widens to cheer the user
 * on, then collapses again. The cheering is the joke: nothing here ever
 * suggests stopping, and the number does the damage on its own.
 *
 * The face degrades and the number warms toward red as the count climbs, so the
 * pill grows heavier in peripheral vision without addressing anyone.
 */
@Composable
fun IslandPill(
    count: Int,
    message: String?,
    modifier: Modifier = Modifier,
) {
    val heat = heatFor(count)
    val numberColor by animateColorAsState(
        targetValue = lerpColor(PillChalk, PillHeat, heat),
        animationSpec = spring(),
        label = "pill-number",
    )
    val edge by animateColorAsState(
        targetValue = lerpColor(PillEdge, PillHeat, heat).copy(alpha = 0.55f),
        animationSpec = spring(),
        label = "pill-edge",
    )

    // A small kick each time the number actually changes, so it registers
    // peripherally without demanding attention.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count <= 0) return@LaunchedEffect
        pulse.snapTo(1.14f)
        pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 900f))
    }

    Row(
        modifier = modifier
            .scale(pulse.value)
            .background(PillInk, RoundedCornerShape(50))
            .border(1.dp, edge, RoundedCornerShape(50))
            .animateContentSize(spring(dampingRatio = 0.75f))
            .widthIn(max = 330.dp)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = SarcasmCatalogue.faceFor(count),
            fontSize = 14.sp,
        )

        Spacer(Modifier.width(7.dp))

        Text(
            text = count.toString(),
            color = numberColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp,
        )

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "·",
                    color = PillAsh,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = message.orEmpty(),
                    color = PillChalk,
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

private val PillInk = Color(0xF00A0A0A)
private val PillChalk = Color(0xFFF2F2F2)
private val PillAsh = Color(0xFF5A5A5A)
private val PillEdge = Color(0xFFC8FF00)
private val PillHeat = Color(0xFFFF3B1F)
