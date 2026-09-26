package com.ekaur.android.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekaur.android.service.KeepAlive
import com.ekaur.android.ui.theme.Poppins
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Little looping films of the exact taps each setup step needs.
 *
 * People don't read instructions, least of all ones about system settings,
 * so every step shows a phone doing it: the screen they'll land on, a finger
 * moving to the right row, the switch flipping, the popup and the button to
 * press -- with a one-line caption synced to each part.
 *
 * Built to hold up frame by frame:
 * - **One clock.** Everything on the stage is a function of a single time
 *   value, so a frame can be rendered at any moment (the screenshot tests
 *   do exactly that) and nothing drifts between loops.
 * - **The finger aims at real positions.** Every tappable thing reports
 *   where it was laid out, and the finger travels to that spot -- no
 *   hand-typed coordinates to fall out of line when a label changes length.
 * - **Every scene is always composed**, only faded in and out, so a target
 *   in the next scene is already measured before the finger heads for it.
 */

// ---------------------------------------------------------------------------
// The stage
// ---------------------------------------------------------------------------

/** A tap on a named target at a moment in the loop. */
data class SimTap(val atMs: Float, val target: String)

/** A caption shown under the phone from [fromMs] until [toMs]. */
data class SimCaption(val fromMs: Float, val toMs: Float, val text: String)

/** Where each named target sits on the simulated screen. */
@Stable
class SimTargets {
    internal val rects = mutableStateMapOf<String, Rect>()
    internal var screen: LayoutCoordinates? = null
}

/** What the scene content gets: the current time and a way to mark targets. */
class SimScope internal constructor(val t: Float, private val targets: SimTargets) {

    /** Marks this element as [key], so a [SimTap] can aim at it. */
    fun Modifier.target(key: String): Modifier = onGloballyPositioned { coords ->
        val screen = targets.screen ?: return@onGloballyPositioned
        if (!screen.isAttached || !coords.isAttached) return@onGloballyPositioned
        val rect = screen.localBoundingBoxOf(coords, clipBounds = false)
        if (targets.rects[key] != rect) targets.rects[key] = rect
    }

    /** 0..1 progress of [t] through a span. */
    fun ramp(start: Float, durationMs: Float): Float = rampOf(t, start, durationMs)

    /** Opacity of something shown from [from] to [to], with short fades. */
    fun shown(from: Float, to: Float, fade: Float = 180f): Float =
        min(rampOf(t, from, fade), 1f - rampOf(t, to - fade, fade))

    /**
     * One scene of a film, on screen from [from] to [to].
     *
     * Off screen it isn't composed at all -- the loop redraws every frame,
     * and in the floating guide that runs the whole time someone is in
     * Settings. The one exception is a scene whose [targets] haven't been
     * measured yet: it is laid out invisibly once, so the finger already
     * knows where to go before the scene fades in.
     */
    @Composable
    fun Scene(from: Float, to: Float, targets: List<String> = emptyList(), content: @Composable BoxScope.() -> Unit) {
        val alpha = shown(from, to)
        if (alpha <= 0f && targets.all { it in this.targets.rects }) return
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, content = content)
    }
}

internal fun rampOf(t: Float, start: Float, durationMs: Float): Float =
    if (durationMs <= 0f) (if (t >= start) 1f else 0f) else ((t - start) / durationMs).coerceIn(0f, 1f)

private fun easeInOut(x: Float): Float = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).let { it * it } / 2

/** The simulated screen's size: a phone at a glance, big enough to read. */
val SimScreenWidth = 196.dp
val SimScreenHeight = 396.dp
private val Bezel = 6.dp

/**
 * A phone playing a loop [durationMs] long.
 *
 * Pass [fixedTimeMs] to freeze it on one frame (tests); otherwise it plays on
 * its own. [scale] shrinks the whole thing for the floating guide.
 */
@Composable
fun SimStage(
    durationMs: Int,
    taps: List<SimTap>,
    captions: List<SimCaption>,
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
    /** Loop only this part of the film (a single hop of a longer one). */
    window: ClosedFloatingPointRange<Float>? = null,
    content: @Composable SimScope.() -> Unit,
) {
    val start = window?.start ?: 0f
    val length = window?.let { (it.endInclusive - it.start).toInt() } ?: durationMs
    val t = if (fixedTimeMs != null) {
        fixedTimeMs
    } else {
        val loop = rememberInfiniteTransition(label = "sim")
        val v by loop.animateFloat(
            initialValue = 0f,
            targetValue = length.toFloat(),
            animationSpec = infiniteRepeatable(tween(length, easing = LinearEasing)),
            label = "sim-clock",
        )
        start + v
    }
    val targets = remember { SimTargets() }
    val phoneW = SimScreenWidth + Bezel * 2
    val phoneH = SimScreenHeight + Bezel * 2

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(phoneW * scale, phoneH * scale)) {
            Box(
                Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(phoneW, phoneH)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .shadow(18.dp, RoundedCornerShape(30.dp), clip = false)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF111114))
                    .padding(Bezel),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SimColors.Page)
                        .onGloballyPositioned { targets.screen = it },
                ) {
                    val scope = SimScope(t, targets)
                    scope.content()
                    Finger(t, taps, targets)
                }
            }
        }
        if (showCaption && captions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            CaptionStrip(t, captions)
        }
    }
}

@Composable
private fun CaptionStrip(t: Float, captions: List<SimCaption>) {
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        captions.forEachIndexed { i, c ->
            val a = min(rampOf(t, c.fromMs, 200f), 1f - rampOf(t, c.toMs - 200f, 200f))
            if (a > 0f) {
                Row(
                    Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 6.dp.toPx() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (captions.size > 1) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(SimColors.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = c.text,
                        color = Color(0xFF1C1C1E),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Poppins,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The finger: rests near the bottom, glides to each target just before its
 * tap, presses (a squeeze and a ripple), and waits there for the next one.
 */
@Composable
private fun BoxScope.Finger(t: Float, taps: List<SimTap>, targets: SimTargets) {
    if (taps.isEmpty()) return
    val density = LocalDensity.current
    val screen = targets.screen ?: return
    val size = screen.size
    if (size.width == 0) return
    val rest = Offset(size.width * 0.72f, size.height * 0.9f)

    val first = taps.first().atMs
    val last = taps.last().atMs
    val alpha = min(rampOf(t, first - 1000f, 220f), 1f - rampOf(t, last + 450f, 260f))
    if (alpha <= 0f) return

    // The tap being approached (or just made).
    val index = taps.indexOfFirst { t <= it.atMs + 380f }.let { if (it == -1) taps.lastIndex else it }
    val tap = taps[index]
    val target = targets.rects[tap.target]?.center ?: return
    val from = if (index == 0) rest else targets.rects[taps[index - 1].target]?.center ?: rest
    val prevAt = if (index == 0) first - 1000f else taps[index - 1].atMs
    val moveStart = max(prevAt + 380f, tap.atMs - 700f)
    val moveEnd = tap.atMs - 140f
    val p = easeInOut(rampOf(t, moveStart, moveEnd - moveStart))
    val pos = Offset(from.x + (target.x - from.x) * p, from.y + (target.y - from.y) * p)

    val sincePress = t - tap.atMs
    val squeeze = if (sincePress in -120f..140f) 0.8f else 1f
    val ripple = if (sincePress in 0f..480f) sincePress / 480f else -1f

    val fingerPx = with(density) { 30.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((pos.x - fingerPx / 2).roundToInt(), (pos.y - fingerPx / 2).roundToInt()) }
            .size(30.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        if (ripple >= 0f) {
            Canvas(Modifier.requiredSize(64.dp).align(Alignment.Center)) {
                drawCircle(
                    color = SimColors.Accent.copy(alpha = 0.45f * (1f - ripple)),
                    radius = this.size.minDimension / 2 * (0.35f + 0.65f * ripple),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = squeeze; scaleY = squeeze }
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .border(2.dp, Color(0x55000000), CircleShape),
        )
    }
}

// ---------------------------------------------------------------------------
// The pieces a settings screen is made of
// ---------------------------------------------------------------------------

object SimColors {
    val Page = Color(0xFFF3F3F7)
    val Card = Color.White
    val Text = Color(0xFF1C1C1E)
    val Sub = Color(0xFF74747F)
    val Line = Color(0xFFE6E6EC)
    val Accent = Color(0xFF2F6BFF)
    val Good = Color(0xFF22C55E)
}

@Composable
private fun T(
    text: String,
    size: TextUnit = 10.sp,
    color: Color = SimColors.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        lineHeight = size * 1.25f,
        fontWeight = weight,
        fontFamily = Poppins,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun StatusBar(dark: Boolean = false) {
    val c = if (dark) Color.White else SimColors.Text
    Row(
        Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("12:30", 8.sp, c, FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(14.dp, 7.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, c, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun TopBar(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = size.width
            drawLine(SimColors.Text, Offset(w * 0.7f, w * 0.15f), Offset(w * 0.25f, w * 0.5f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(SimColors.Text, Offset(w * 0.25f, w * 0.5f), Offset(w * 0.7f, w * 0.85f), 1.6.dp.toPx(), StrokeCap.Round)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = SimColors.Text,
            fontSize = if (title.length > 20) 10.5.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Poppins,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun Section(text: String) {
    T(text, 9.sp, SimColors.Accent, FontWeight.SemiBold, Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp))
}

/** A white group of rows, as settings screens draw them. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SimColors.Card),
    ) { content() }
}

@Composable
private fun SettingRow(
    title: String,
    sub: String? = null,
    icon: (@Composable () -> Unit)? = null,
    pressed: Float = 0f,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(lerp(Color.Transparent, Color(0x1A2F6BFF), pressed))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            // One line when it sits beside a switch: a wrapped app name read as clutter.
            T(title, if (trailing != null) 10.sp else 11.sp, weight = FontWeight.Medium)
            if (sub != null) T(sub, 8.5.sp, SimColors.Sub)
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 12.dp).fillMaxWidth().height(1.dp).background(SimColors.Line))
}

@Composable
private fun SimSwitch(on: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp, 17.dp)
            .clip(RoundedCornerShape(50))
            .background(lerp(Color(0xFFC9C9D1), SimColors.Accent, on))
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = on * 13.dp.toPx() }
                .size(13.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Ek Aur's launcher icon: the gradient tile and its tally. */
@Composable
fun SimAppIcon(size: Dp = 22.dp) {
    Canvas(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF515BD4), Color(0xFF8134AF), Color(0xFFDD2A7B), Color(0xFFF58529), Color(0xFFFEDA77)),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f),
                ),
            ),
    ) {
        val w = this.size.width
        val stroke = w * 0.07f
        for (i in 0 until 4) {
            val x = w * (0.35f + i * 0.1f)
            drawLine(Color.White, Offset(x, w * 0.36f), Offset(x, w * 0.64f), stroke, StrokeCap.Round)
        }
        drawLine(Color.White, Offset(w * 0.3f, w * 0.67f), Offset(w * 0.71f, w * 0.33f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun OtherIcon(color: Color) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(color))
}

/** A system popup over a dimmed screen. */
@Composable
private fun BoxScope.Dialog(
    alpha: Float,
    title: String,
    body: String?,
    buttons: List<Pair<String, String?>>,
    pressed: String? = null,
    icon: (@Composable () -> Unit)? = null,
    scope: SimScope,
) {
    if (alpha <= 0f) return
    Box(Modifier.matchParentSize().graphicsLayer { this.alpha = alpha }.background(Color(0x66000000)))
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = 14.dp)
            .graphicsLayer {
                this.alpha = alpha
                val s = 0.92f + 0.08f * alpha
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(18.dp))
            .background(SimColors.Card)
            .padding(16.dp),
    ) {
        if (icon != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.height(10.dp))
        }
        T(title, 12.sp, weight = FontWeight.SemiBold, maxLines = 4)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            T(body, 9.5.sp, SimColors.Sub, maxLines = 4)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            buttons.forEachIndexed { i, (label, key) ->
                if (i > 0) Spacer(Modifier.width(6.dp))
                val isPressed = key != null && key == pressed
                with(scope) {
                    Box(
                        Modifier
                            .then(if (key != null) Modifier.target(key) else Modifier)
                            .clip(RoundedCornerShape(50))
                            .background(if (isPressed) Color(0x222F6BFF) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        T(label, 11.sp, SimColors.Accent, FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** A green badge that pops in: the step worked. */
@Composable
private fun BoxScope.Success(alpha: Float, pop: Float, text: String) {
    if (alpha <= 0f) return
    // A soft veil, so the badge never sits on top of readable rows.
    Box(Modifier.matchParentSize().graphicsLayer { this.alpha = alpha }.background(SimColors.Page.copy(alpha = 0.86f)))
    Column(
        Modifier.align(Alignment.Center).graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val s = 0.5f + 0.5f * pop + 0.12f * kotlin.math.sin(pop * Math.PI).toFloat()
                    scaleX = s
                    scaleY = s
                }
                .size(64.dp)
                .clip(CircleShape)
                .background(SimColors.Good),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(30.dp)) {
                val w = this.size.width
                val stroke = 4.dp.toPx()
                drawLine(Color.White, Offset(w * 0.12f, w * 0.52f), Offset(w * 0.4f, w * 0.78f), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.4f, w * 0.78f), Offset(w * 0.9f, w * 0.22f), stroke, StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(SimColors.Text)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            T(text, 10.sp, Color.White, FontWeight.SemiBold)
        }
    }
}

/** A few placeholder lines, for the explanatory text a settings page carries. */
@Composable
private fun Blurb(lines: Int = 3) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        repeat(lines) { i ->
            Box(
                Modifier
                    .padding(vertical = 3.dp)
                    .fillMaxWidth(if (i == lines - 1) 0.6f else 1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFE1E1E8)),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The screens, reused across films
// ---------------------------------------------------------------------------

/** Ek Aur's own page in Accessibility, with its main switch. */
@Composable
private fun SimScope.ServicePage(on: Float, switchPressed: Float) {
    Column(Modifier.fillMaxSize()) {
        StatusBar()
        TopBar("Ek Aur (One More)")
        Spacer(Modifier.height(6.dp))
        Group {
            SettingRow(
                title = "Use Ek Aur",
                sub = if (on > 0.5f) "On" else "Off",
                pressed = switchPressed,
            ) { SimSwitch(on, Modifier.target("switch")) }
            Divider()
            SettingRow(title = "Ek Aur shortcut", sub = "Off") { SimSwitch(0f) }
        }
        Blurb()
    }
}

@Composable
private fun SimScope.FullControlDialog(alpha: Float, pressed: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Dialog(
            alpha = alpha,
            title = "Allow Ek Aur to have full control of your device?",
            body = "It only sees the swipe to the next video.",
            buttons = listOf("Deny" to null, "Allow" to "allow"),
            pressed = if (pressed) "allow" else null,
            icon = { SimAppIcon(30.dp) },
            scope = this@FullControlDialog,
        )
    }
}

// ---------------------------------------------------------------------------
// The films
// ---------------------------------------------------------------------------

/** Accessibility list → Ek Aur → switch → Allow → counting. */
@Composable
fun AccessibilitySim(
    listSection: String,
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
) {
    val taps = listOf(SimTap(1500f, "row"), SimTap(3500f, "switch"), SimTap(5200f, "allow"))
    val captions = listOf(
        SimCaption(0f, 2600f, "Tap Ek Aur"),
        SimCaption(2600f, 4300f, "Turn the switch on"),
        SimCaption(4300f, 6000f, "Press Allow"),
        SimCaption(6000f, 9000f, "Done: it's counting"),
    )
    SimStage(9000, taps, captions, modifier, fixedTimeMs, scale, showCaption) {
        // 1: the accessibility list
        Scene(-1000f, 2650f, listOf("row")) {
            Column(Modifier.fillMaxSize()) {
                StatusBar()
                TopBar("Accessibility")
                Spacer(Modifier.height(4.dp))
                Group {
                    SettingRow("TalkBack", "Off", icon = { OtherIcon(Color(0xFF34A853)) })
                    Divider()
                    SettingRow("Select to Speak", "Off", icon = { OtherIcon(Color(0xFF1A73E8)) })
                }
                Section(listSection)
                Group {
                    val press = if (t in 1400f..1900f) 1f else 0f
                    SettingRow(
                        title = "Ek Aur (One More)",
                        sub = "Off",
                        icon = { SimAppIcon() },
                        pressed = press,
                        modifier = Modifier.target("row"),
                    )
                }
                Section("General")
                Group {
                    SettingRow("Text and display")
                    Divider()
                    SettingRow("Timeout")
                }
            }
        }
        // 2: Ek Aur's page, then the popup, then on
        val on = rampOf(t, 5350f, 160f)
        Scene(2650f, 9200f, listOf("switch")) {
            ServicePage(on = on, switchPressed = if (t in 3400f..3800f) 1f else 0f)
            FullControlDialog(alpha = shown(3700f, 5400f), pressed = t in 5150f..5400f)
        }
        Box(Modifier.fillMaxSize()) {
            Success(alpha = shown(6000f, 9200f), pop = rampOf(t, 6000f, 400f), text = "Counting")
        }
    }
}

/**
 * The "Restricted setting" wall and the way through it: tap the switch, OK
 * the popup, App info → ⋮ → Allow restricted settings, then switch on.
 */
@Composable
fun RestrictedSim(
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
    /** 0: the switch and the popup. 1: App info, the dots, Allow. Null: all of it. */
    part: Int? = null,
) {
    val taps = listOf(
        SimTap(1000f, "switch"),
        SimTap(2500f, "ok"),
        SimTap(4200f, "more"),
        SimTap(5500f, "allowRs"),
        SimTap(7600f, "switch"),
        SimTap(9000f, "allow"),
    )
    val captions = listOf(
        SimCaption(0f, 3000f, "Tap the switch, press OK"),
        SimCaption(3000f, 6500f, "App info → 3 dots → Allow"),
        SimCaption(6500f, 9800f, "Now switch it on"),
        SimCaption(9800f, 12500f, "Done: it's counting"),
    )
    val window = when (part) {
        0 -> 0f..3000f
        1 -> 2950f..6550f
        else -> null
    }
    SimStage(12500, taps, captions.filter { c -> window == null || (c.fromMs < window.endInclusive && c.toMs > window.start) },
        modifier, fixedTimeMs, scale, showCaption, window) {
        // 1: the switch, and the wall
        Scene(-1000f, 2950f, listOf("switch")) {
            ServicePage(on = 0f, switchPressed = if (t in 900f..1300f) 1f else 0f)
            Box(Modifier.fillMaxSize()) {
                Dialog(
                    alpha = shown(1200f, 2700f),
                    title = "Restricted setting",
                    body = "For your security, this setting is currently unavailable.",
                    buttons = listOf("OK" to "ok"),
                    pressed = if (t in 2450f..2700f) "ok" else null,
                    scope = this@SimStage,
                )
            }
        }
        // 2: App info and its menu
        Scene(2950f, 6550f, listOf("more")) {
            Column(Modifier.fillMaxSize()) {
                StatusBar()
                TopBar("App info") {
                    Box(
                        Modifier
                            .target("more")
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (t in 4100f..4500f) Color(0x222F6BFF) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                            repeat(3) { Box(Modifier.size(3.5.dp).clip(CircleShape).background(SimColors.Text)) }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SimAppIcon(44.dp)
                    Spacer(Modifier.height(6.dp))
                    T("Ek Aur (One More)", 12.sp, weight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Open", "Uninstall", "Force stop").forEach {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SimColors.Card).padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) { T(it, 8.5.sp, SimColors.Accent, FontWeight.Medium) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Group {
                    SettingRow("Notifications")
                    Divider()
                    SettingRow("Permissions")
                    Divider()
                    SettingRow("Battery")
                }
            }
            // The overflow menu
            val menu = shown(4450f, 5800f, 140f)
            if (menu > 0f) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 50.dp, end = 10.dp)
                        .graphicsLayer {
                            alpha = menu
                            transformOrigin = TransformOrigin(1f, 0f)
                            scaleX = 0.9f + 0.1f * menu
                            scaleY = 0.9f + 0.1f * menu
                        }
                        .shadow(8.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(SimColors.Card)
                        .padding(vertical = 4.dp),
                ) {
                    T("Uninstall updates", 10.sp, SimColors.Sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    Box(
                        Modifier
                            .target("allowRs")
                            .background(if (t in 5400f..5800f) Color(0x222F6BFF) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        T("Allow restricted settings", 10.sp, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // 3: back to the switch, which now works
        val on = rampOf(t, 9150f, 160f)
        Scene(6550f, 12700f, listOf("switch")) {
            ServicePage(on = on, switchPressed = if (t in 7500f..7900f) 1f else 0f)
            FullControlDialog(alpha = shown(7800f, 9200f), pressed = t in 8950f..9200f)
        }
        Box(Modifier.fillMaxSize()) {
            Success(alpha = shown(9800f, 12700f), pop = rampOf(t, 9800f, 400f), text = "Counting")
        }
    }
}

/** The switch is on but the phone never started it: off, then on again. */
@Composable
fun RestartSim(
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
) {
    val taps = listOf(
        SimTap(1100f, "switch"),
        SimTap(2400f, "stop"),
        SimTap(3800f, "switch"),
        SimTap(5200f, "allow"),
    )
    val captions = listOf(
        SimCaption(0f, 3000f, "Turn it off"),
        SimCaption(3000f, 6000f, "Turn it on again"),
        SimCaption(6000f, 9000f, "Done: it's counting"),
    )
    SimStage(9000, taps, captions, modifier, fixedTimeMs, scale, showCaption) {
        val off = rampOf(t, 2550f, 160f)
        val on = rampOf(t, 5350f, 160f)
        val level = if (t < 3000f) 1f - off else on
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown(-1000f, 9200f) }) {
            ServicePage(
                on = level,
                switchPressed = if (t in 1000f..1400f || t in 3700f..4100f) 1f else 0f,
            )
            Box(Modifier.fillMaxSize()) {
                Dialog(
                    alpha = shown(1300f, 2600f),
                    title = "Stop Ek Aur?",
                    body = null,
                    buttons = listOf("Cancel" to null, "Stop" to "stop"),
                    pressed = if (t in 2350f..2600f) "stop" else null,
                    scope = this@SimStage,
                )
            }
            FullControlDialog(alpha = shown(4000f, 5400f), pressed = t in 5150f..5400f)
        }
        Box(Modifier.fillMaxSize()) {
            Success(alpha = shown(6000f, 9200f), pop = rampOf(t, 6000f, 400f), text = "Counting")
        }
    }
}

/** "Display over other apps": one switch, then the pill over a video. */
@Composable
fun OverlaySim(
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
) {
    val taps = listOf(SimTap(1600f, "switch"))
    val captions = listOf(
        SimCaption(0f, 3100f, "Turn the switch on"),
        SimCaption(3100f, 8000f, "The counter floats on top"),
    )
    SimStage(8000, taps, captions, modifier, fixedTimeMs, scale, showCaption) {
        val on = rampOf(t, 1750f, 160f)
        Scene(-1000f, 3100f, listOf("switch")) {
            Column(Modifier.fillMaxSize()) {
                StatusBar()
                TopBar("Display over other apps")
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SimAppIcon(40.dp)
                    Spacer(Modifier.height(6.dp))
                    T("Ek Aur (One More)", 12.sp, weight = FontWeight.SemiBold)
                }
                Group {
                    SettingRow(
                        title = "Allow display over other apps",
                        pressed = if (t in 1500f..1900f) 1f else 0f,
                    ) { SimSwitch(on, Modifier.target("switch")) }
                }
                Blurb()
            }
        }
        Scene(3100f, 8200f) {
            ReelWithPill(t - 3100f, endMs = 8000f - 3100f)
        }
    }
}

/** A dark video feed scrolling past, with the counter ticking up on top. */
@Composable
fun SimScope.ReelWithPill(local: Float, start: Int = 12, endMs: Float? = null) {
    // A swipe every 1.6s: the next video slides up and the count goes up
    // the moment it lands.
    val phase = local - 900f
    val swipe = if (phase < 0f) -1 else (phase / SWIPE_EVERY).toInt()
    val within = if (phase < 0f) 0f else phase - swipe * SWIPE_EVERY
    val p = if (phase < 0f) 0f else easeInOut(rampOf(within, 0f, SWIPE_MS))
    // The video on screen before this swipe, and the one coming in.
    val current = max(swipe, 0)
    val landed = if (phase < 0f) 0 else swipe + (if (within >= SWIPE_MS) 1 else 0)
    val count = start + landed

    Box(Modifier.fillMaxSize()) {
        val h = SimScreenHeight
        listOf(current to -p, current + 1 to 1f - p).forEach { (index, at) ->
            if (at > -1f && at < 1f) {
                val colors = REELS[index.mod(REELS.size)]
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = at * h.toPx() }
                        .background(Brush.verticalGradient(colors)),
                ) {
                    // A blob and caption bars, so it reads as a video.
                    Box(
                        Modifier.align(Alignment.Center).size(90.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                        Box(Modifier.size(70.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.7f)))
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(110.dp, 6.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.4f)))
                    }
                }
            }
        }
        StatusBar(dark = true)
        // A thumb flicking up for each swipe.
        if (phase >= 0f && within < SWIPE_MS + 250f) {
            val lift = easeInOut(rampOf(within, -120f, SWIPE_MS))
            val fade = min(rampOf(within, -120f, 120f), 1f - rampOf(within, SWIPE_MS + 60f, 190f))
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationX = 26.dp.toPx()
                        translationY = h.toPx() * (0.78f - 0.42f * lift)
                        alpha = fade
                    }
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f))
                    .border(2.dp, Color(0x55000000), CircleShape),
            )
        }
        // The pill drops in, then ticks with each swipe; it lifts away just
        // before the loop starts over, so the count never runs backwards.
        val out = if (endMs != null) rampOf(local, endMs - 380f, 300f) else 0f
        val drop = easeInOut(rampOf(local, 250f, 450f)) * (1f - out)
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 26.dp)
                .graphicsLayer {
                    translationY = (1f - drop) * -40.dp.toPx()
                    alpha = drop
                }
                .clip(RoundedCornerShape(50))
                .background(Color(0xF00A0A0A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8134AF), Color(0xFFF58529)))))
            Spacer(Modifier.width(7.dp))
            T("$count", 13.sp, Color.White, FontWeight.Bold)
        }
    }
}

private const val SWIPE_EVERY = 1600f
private const val SWIPE_MS = 420f

private val REELS = listOf(
    listOf(Color(0xFF3A1C71), Color(0xFFD76D77)),
    listOf(Color(0xFF0F2027), Color(0xFF2C5364)),
    listOf(Color(0xFF42275A), Color(0xFF734B6D)),
    listOf(Color(0xFF1D2B64), Color(0xFFF8CDDA)),
    listOf(Color(0xFF134E5E), Color(0xFF71B280)),
)

/** The battery popup: "Let app always run in background?" → Allow. */
@Composable
fun BatterySim(
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
) {
    val taps = listOf(SimTap(2300f, "allow"))
    val captions = listOf(
        SimCaption(0f, 3000f, "Press Allow"),
        SimCaption(3000f, 6000f, "Your phone won't stop it"),
    )
    SimStage(6000, taps, captions, modifier, fixedTimeMs, scale, showCaption) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                StatusBar()
                TopBar("Ek Aur")
                Blurb(4)
                Blurb(3)
            }
            Dialog(
                alpha = shown(500f, 2600f),
                title = "Let app always run in background?",
                body = "Ek Aur (One More) needs this to keep counting.",
                buttons = listOf("Deny" to null, "Allow" to "allow"),
                pressed = if (t in 2250f..2600f) "allow" else null,
                icon = { SimAppIcon(30.dp) },
                scope = this@SimStage,
            )
            Success(alpha = shown(2900f, 6200f), pop = rampOf(t, 2900f, 400f), text = "Always on")
        }
    }
}

/** The phone maker's Autostart list: find Ek Aur, turn it on. */
@Composable
fun AutostartSim(
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
    showCaption: Boolean = true,
    kind: KeepAlive.Kind = KeepAlive.kind(),
    screenTitle: String? = null,
) {
    val title = screenTitle ?: when (kind) {
        KeepAlive.Kind.Oppo -> "Auto launch"
        KeepAlive.Kind.Vivo -> "Background startup"
        else -> "Autostart"
    }
    val taps = listOf(SimTap(2000f, "switch"))
    val captions = listOf(
        SimCaption(0f, 3000f, "Find Ek Aur, turn it on"),
        SimCaption(3000f, 6000f, "It can start by itself now"),
    )
    SimStage(6000, taps, captions, modifier, fixedTimeMs, scale, showCaption) {
        val on = rampOf(t, 2150f, 160f)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown(-1000f, 6200f) }) {
            Column(Modifier.fillMaxSize()) {
                StatusBar()
                TopBar(title)
                Spacer(Modifier.height(4.dp))
                Group {
                    SettingRow("Calculator", icon = { OtherIcon(Color(0xFFFF9500)) }) { SimSwitch(0f) }
                    Divider()
                    SettingRow("Compass", icon = { OtherIcon(Color(0xFF8E8E93)) }) { SimSwitch(0f) }
                    Divider()
                    SettingRow(
                        "Ek Aur (One More)",
                        icon = { SimAppIcon(20.dp) },
                        pressed = if (t in 1900f..2300f) 1f else 0f,
                    ) { SimSwitch(on, Modifier.target("switch")) }
                    Divider()
                    SettingRow("Gallery", icon = { OtherIcon(Color(0xFFFF2D55)) }) { SimSwitch(1f) }
                    Divider()
                    SettingRow("Notes", icon = { OtherIcon(Color(0xFFFFCC00)) }) { SimSwitch(0f) }
                }
            }
            Success(alpha = shown(3000f, 6200f), pop = rampOf(t, 3000f, 400f), text = "Autostart on")
        }
    }
}

/** The app doing its thing, for the welcome screen. */
@Composable
fun WelcomeSim(
    modifier: Modifier = Modifier,
    fixedTimeMs: Float? = null,
    scale: Float = 1f,
) {
    SimStage(8000, emptyList(), emptyList(), modifier, fixedTimeMs, scale, showCaption = false) {
        ReelWithPill(t, start = 41, endMs = 8000f)
    }
}
