package com.ekaur.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 *
 * Nothing here animates a width. [maxWidthPx] is the room the pill has beside it
 * on the edge it is parked against, so a message is bounded before it is laid
 * out rather than growing until the window has to be moved to fit it.
 */
@Composable
fun IslandPill(
    count: Int,
    message: String?,
    maxWidthPx: Int,
    onCollapsedWidth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxWidth = with(density) {
        if (maxWidthPx > 0) maxWidthPx.toDp() else FALLBACK_MAX_WIDTH
    }
    val paddingPx = with(density) { (H_PADDING * 2).roundToPx() }
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

    Row(
        modifier = modifier
            .background(PillInk, RoundedCornerShape(50))
            .border(1.dp, edge, RoundedCornerShape(50))
            // Clips the message's slide, so it cannot be drawn past the pill's
            // rounded edge on the frames before it has settled.
            .clip(RoundedCornerShape(50))
            // Deliberately no animateContentSize. Animating the width made
            // Compose re-measure the message at a different width on every
            // frame, and because the message is ellipsised it was re-truncated
            // at a different character each time -- which read as the text
            // being typed out, with the ellipsis jumping. Snapping to the full
            // width lays the line out once, whole.
            .widthIn(max = maxWidth)
            .padding(horizontal = H_PADDING, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // The face and the number, measured on their own.
        //
        // This is what the window is placed against, and it is deliberately not
        // the whole pill: AnimatedVisibility keeps the message composed for the
        // length of its exit animation, so the pill is still at its expanded
        // width for a moment after the message is gone. Measuring a part that
        // can never hold the message means the width reported is always a
        // genuine collapsed one, whatever the animation is doing.
        Row(
            modifier = Modifier.onSizeChanged { onCollapsedWidth(it.width + paddingPx) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SarcasmCatalogue.faceFor(count),
                fontSize = 14.sp,
            )

            Spacer(Modifier.width(7.dp))

            // A soft fade rather than a scale kick. Snapping the whole pill to
            // 1.14x on every single reel was the popping; Crossfade takes the
            // larger of the two sizes instead of animating between them, so
            // 9 -> 10 cannot start a width animation either.
            Crossfade(
                targetState = count,
                animationSpec = tween(COUNT_FADE_MS),
                label = "pill-count",
            ) { value ->
                Text(
                    text = value.toString(),
                    color = numberColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp,
                )
            }
        }

        AnimatedVisibility(
            visible = message != null,
            // Fade plus a short slide. Both are draw-layer properties applied to
            // content already measured at its full width, so no character is
            // ever re-truncated and the pill's width is never animated -- the
            // container opens in one step and the line glides into it.
            enter = fadeIn(tween(ENTER_MS)) +
                slideInHorizontally(tween(ENTER_MS)) { it / SLIDE_FRACTION },
            exit = fadeOut(tween(EXIT_MS)) +
                slideOutHorizontally(tween(EXIT_MS)) { it / SLIDE_FRACTION },
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
                    // Measured once, at the width it will keep for its whole
                    // life on screen.
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

/** Counted into the collapsed width, since the core is measured inside it. */
private val H_PADDING = 13.dp

private const val COUNT_FADE_MS = 120
private const val ENTER_MS = 180
private const val EXIT_MS = 140

/** The slide travels a sixth of the message's width -- a hint, not a journey. */
private const val SLIDE_FRACTION = 6

/** Only reached before the window has reported how much room the pill has. */
private val FALLBACK_MAX_WIDTH = 330.dp

private val PillInk = Color(0xF00A0A0A)
private val PillChalk = Color(0xFFF2F2F2)
private val PillAsh = Color(0xFF5A5A5A)
private val PillEdge = Color(0xFFDD2A7B)
private val PillHeat = Color(0xFFFF3B1F)
