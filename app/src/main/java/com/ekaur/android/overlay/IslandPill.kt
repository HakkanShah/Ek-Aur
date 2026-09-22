package com.ekaur.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Rests as a compact pill -- a face and a number. At each milestone it briefly
 * widens to cheer the user on, then collapses again. The cheering is the joke:
 * nothing here ever suggests stopping, and the number does the damage on its own.
 *
 * The face degrades as the count climbs, so the pill grows heavier in peripheral
 * vision without addressing anyone -- and without a loud reactive colour, which
 * kept the pill calm and sleek rather than turning red.
 *
 * Nothing here animates a width. The pill always wraps its content -- a message
 * makes it exactly as wide as the line needs, no more -- bounded by [maxWidthPx],
 * the room from its anchored edge to the far margin, so a long line wraps to two
 * lines rather than running off-screen. The window grows inward from the parked
 * edge, so the pill stays put and the text simply extends beside the number.
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

    // While a message shows, the host widens the window to make room; this box
    // fills that width and centres the pill in it, so the pill grows evenly to
    // both sides (dynamic-island style) and the line is never clipped. Collapsed,
    // the window is wrap-content, so filling it keeps the pill exactly its size.
    Box(
        modifier = modifier.then(if (message != null) Modifier.fillMaxWidth() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
    Row(
        modifier = Modifier
            .background(PillInk, RoundedCornerShape(50))
            // A single static hairline -- no count-reactive red. The climbing
            // "damage" now lives entirely in the emoji ladder, which reads cool
            // rather than loud.
            .border(1.dp, PillEdge, RoundedCornerShape(50))
            // Clips the message's slide, so it cannot be drawn past the pill's
            // rounded edge on the frames before it has settled.
            .clip(RoundedCornerShape(50))
            // The pill wraps its content -- the number, or the number plus the
            // line -- bounded by the room it has, so a long line wraps to two
            // rather than stretching. Never animated: measured once.
            .widthIn(max = maxWidth)
            .padding(horizontal = H_PADDING, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                    color = PillChalk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp,
                )
            }
        }

        AnimatedVisibility(
            // No weight: the message must not stretch the pill to fill anything.
            // It takes exactly the width the text needs, up to the room the pill
            // has, so the pill's length matches the line.
            visible = message != null,
            // Fade plus a short slide. Both are draw-layer properties applied to
            // content already measured at its full width, so no character is
            // ever re-truncated and the pill's width is never animated -- the
            // container opens in one step and the line glides into it.
            enter = fadeIn(tween(ENTER_MS)) +
                slideInHorizontally(tween(ENTER_MS)) { it / SLIDE_FRACTION },
            exit = fadeOut(tween(EXIT_MS)),
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
                    // Sizes to the text; the pill's widthIn(max) caps it, so a
                    // long line wraps to two rather than pushing the pill wider
                    // than the room it has.
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

/** A single static hairline -- a faint magenta, not a loud reactive border. */
private val PillEdge = Color(0x33DD2A7B)
