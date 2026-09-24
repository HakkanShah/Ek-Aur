package com.ekaur.android.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ekaur.android.ui.theme.InkLine
import com.ekaur.android.ui.theme.InstaStops
import com.ekaur.android.ui.theme.SurfaceLav
import com.ekaur.android.ui.theme.instaGradient
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The squish every tappable thing gives when touched.
 *
 * Pass the same [interaction] to the element's `clickable`, so the press state
 * is the real one. The scale is applied in the draw layer, so pressing never
 * re-measures anything.
 */
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressed: Float = 0.96f,
): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = Motion.quick(),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * A staggered entrance: fades in and rises a few dp, [index] steps after the
 * first item. Plays once per composition -- the tabs are kept alive in the
 * pager, so it plays when the app opens, not on every swipe past.
 */
fun Modifier.reveal(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(12) * Motion.STAGGER.toLong())
        progress.animateTo(1f, Motion.emphasised())
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
    }
}

/**
 * A soft sheen sweeping across a placeholder, so a loading block reads as
 * "coming" rather than "empty".
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmer-x",
    )
    drawWithCache {
        val width = size.width
        onDrawBehind {
            val start = x * width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(SurfaceLav, Color.White.copy(alpha = 0.9f), SurfaceLav),
                    start = Offset(start - width * 0.4f, 0f),
                    end = Offset(start + width * 0.4f, size.height),
                ),
            )
        }
    }
}

/** A rounded loading block. */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(SurfaceLav)
            .shimmer(),
    )
}

/**
 * A number that counts to its value instead of snapping.
 *
 * It starts from zero on first show (the hero moment) and from the previous
 * value after that, so a new reel ticks up rather than restarting. Longer
 * jumps take a little longer, capped so nothing drags.
 */
@Composable
fun AnimatedCount(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    format: (Int) -> String = { it.toString() },
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(value) {
        val distance = kotlin.math.abs(value - animated.value)
        val duration = (320f + distance * 4f).coerceAtMost(1100f).toInt()
        animated.animateTo(value.toFloat(), tween(duration, easing = Motion.Decelerate))
    }
    Text(
        text = format(animated.value.roundToInt()),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/**
 * Small, deliberate vibrations. A tick is barely there (tabs, toggles, chart
 * scrubbing); a confirm marks a success. Both respect the system's touch
 * feedback setting because they go through the view.
 */
class Haptics internal constructor(private val view: android.view.View) {
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * A dot that breathes while [active] -- "we're watching for this". When not
 * active nothing animates at all, so an idle dot costs no frames.
 */
@Composable
fun PulseDot(
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(modifier.size(size * 2.6f), contentAlignment = Alignment.Center) {
        if (active) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                label = "pulse-t",
            )
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        val s = 1f + t * 1.6f
                        scaleX = s; scaleY = s
                        alpha = (1f - t) * 0.45f
                    }
                    .background(color, CircleShape),
            )
        }
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/** A small spinner in the app's accent, for inline busy states. */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = com.ekaur.android.ui.theme.Acid,
    stroke: Dp = 2.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = stroke,
        trackColor = InkLine,
    )
}

/**
 * A one-shot burst of confetti in the gradient's colours.
 *
 * Plays whenever [key] changes to a new non-null value, runs ~1.4s, then draws
 * nothing and composes nothing -- no idle cost. Meant to sit in a Box on top
 * of whatever is being celebrated, with `Modifier.matchParentSize()`.
 */
@Composable
fun Celebration(key: Any?, modifier: Modifier = Modifier) {
    if (key == null) return
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(1400, easing = LinearEasing))
    }
    if (progress.value >= 1f) return

    val particles = remember(key) {
        val random = Random(key.hashCode())
        List(56) {
            val angle = random.nextFloat() * (Math.PI * 2).toFloat()
            Particle(
                dx = cos(angle),
                dy = sin(angle) - 0.6f,
                speed = 0.45f + random.nextFloat() * 0.75f,
                size = 5f + random.nextFloat() * 6f,
                color = InstaStops[random.nextInt(InstaStops.size)],
                spin = random.nextFloat() * 720f - 360f,
                round = random.nextBoolean(),
            )
        }
    }
    Canvas(modifier) {
        val t = progress.value
        val origin = Offset(size.width / 2f, size.height * 0.42f)
        val reach = size.minDimension * 0.7f
        val fade = if (t < 0.65f) 1f else (1f - (t - 0.65f) / 0.35f)
        particles.forEach { p ->
            val travel = p.speed * reach * (1f - (1f - t) * (1f - t))
            val gravity = t * t * reach * 0.55f
            val center = Offset(origin.x + p.dx * travel, origin.y + p.dy * travel + gravity)
            val px = p.size.dp.toPx()
            rotate(p.spin * t, center) {
                if (p.round) {
                    drawCircle(p.color.copy(alpha = fade), radius = px / 2f, center = center)
                } else {
                    drawRect(
                        color = p.color.copy(alpha = fade),
                        topLeft = Offset(center.x - px / 2f, center.y - px / 4f),
                        size = Size(px, px / 2f),
                    )
                }
            }
        }
    }
}

private class Particle(
    val dx: Float,
    val dy: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val round: Boolean,
)

/** A thin gradient bar that eases to [fraction] instead of jumping. */
@Composable
fun GradientProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = SurfaceLav,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.emphasised(),
        label = "progress",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(track)
            .drawWithCache {
                val brush = instaGradient()
                onDrawBehind {
                    if (animated > 0f) {
                        val w = size.width * animated
                        drawRoundRect(
                            brush = brush,
                            size = Size(w, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                    }
                }
            }
            .height(height),
    )
}
