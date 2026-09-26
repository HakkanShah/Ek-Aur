package com.ekaur.android.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ekaur.android.R
import com.ekaur.android.ui.theme.buttonGradient
import com.ekaur.android.ui.theme.instaGradient

/**
 * The scroll reminder: a card in the middle of the screen, over Instagram or
 * YouTube, when today's count reaches the number the user picked.
 *
 * Unlike the pill it takes touches and focus: the dimmed backdrop keeps the
 * feed from scrolling on behind it, and Back closes it (as "later"). Every
 * way out is safe, because the next reminder was already scheduled before
 * this one appeared -- see [com.ekaur.android.reminder.ReminderPlan]. It
 * also closes itself after [AUTO_CLOSE_MS], so nobody can ever be stuck
 * behind it.
 */
class ReminderPopup(private val context: Context) {

    /** What the user chose. */
    enum class Choice { Break, Later, NotToday, TurnOff }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoClose = Runnable { dismiss() }

    val isShowing: Boolean get() = root != null

    /**
     * Shows the card. **Main thread.** [onChoice] is called once, for a button;
     * Back, the timeout and [dismiss] close it without calling it.
     */
    fun show(
        count: Int,
        unit: String,
        minutesToday: Int,
        snooze: Int,
        line: String,
        onChoice: (Choice) -> Unit,
    ) {
        if (isShowing) return
        if (!Settings.canDrawOverlays(context)) return

        val lifecycle = OverlayLifecycleOwner().apply { create() }
        val frame = object : FrameLayout(context) {
            // Back closes the card; the next reminder is already set.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) main.post { dismiss() }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        // The owners go on the frame, the window's root: Compose builds its
        // recomposer from the root view and looks for the lifecycle there, so
        // set only on the ComposeView inside it, the popup crashed the app the
        // moment it appeared (build 56). The ComposeView finds them by walking up.
        frame.setViewTreeLifecycleOwner(lifecycle)
        frame.setViewTreeViewModelStoreOwner(lifecycle)
        frame.setViewTreeSavedStateRegistryOwner(lifecycle)
        val compose = ComposeView(context).apply {
            setContent {
                ReminderCard(
                    count = count,
                    unit = unit,
                    minutesToday = minutesToday,
                    snooze = snooze,
                    line = line,
                    // Closed on the next frame, not from inside the tap that is
                    // still being handled by the view being removed.
                    onChoice = { choice ->
                        main.post {
                            dismiss()
                            onChoice(choice)
                        }
                    },
                )
            }
        }
        frame.addView(compose)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable, so Back comes here rather than to the app behind.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        runCatching { windowManager.addView(frame, params) }
            .onFailure {
                lifecycle.destroy()
                return
            }
        lifecycle.start()
        root = frame
        owner = lifecycle
        frame.performHapticFeedback(HapticFeedbackConstants.CONFIRM.takeIf { Build.VERSION.SDK_INT >= 30 } ?: HapticFeedbackConstants.LONG_PRESS)
        main.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    /** Closes the card if it is up. **Main thread.** Safe to call any time. */
    fun dismiss() {
        main.removeCallbacks(autoClose)
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        (view.getChildAt(0) as? ComposeView)?.disposeComposition()
        owner?.destroy()
        root = null
        owner = null
    }

    private companion object {
        const val AUTO_CLOSE_MS = 60_000L
    }
}

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val Night = Color(0xFF111114)
private val White = Color.White

/**
 * Dark glass, like the pill it pops up from: over a busy video a white sheet
 * shouts, and this should feel like part of the same overlay. The count sits
 * in a gradient ring that sweeps to full as the card lands -- "you hit it".
 */
@Composable
internal fun ReminderCard(
    count: Int,
    unit: String,
    minutesToday: Int,
    snooze: Int,
    line: String,
    onChoice: (ReminderPopup.Choice) -> Unit,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scrim.animateTo(1f, tween(220)) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(900, delayMillis = 150, easing = FastOutSlowInEasing)) }

    val gradient = instaGradient()
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f * scrim.value))
            // Swallows touches, so the feed cannot scroll on behind the card.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = shown.value.coerceIn(0f, 1f)
                    translationY = (1f - shown.value) * 80f
                    scaleX = 0.92f + 0.08f * shown.value
                    scaleY = 0.92f + 0.08f * shown.value
                }
                .shadow(40.dp, shape, ambientColor = Color(0xFFDD2A7B), spotColor = Color(0xFFDD2A7B))
                .clip(shape)
                .background(Night)
                .border(1.5.dp, gradient, shape)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A small badge: what this is, at a glance.
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EkIcon(EkIcons.Bell, tint = White.copy(alpha = 0.85f), size = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Scroll reminder",
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = White.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(18.dp))

            // The count, in a ring that fills as the card lands.
            Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = 9.dp.toPx()
                    val inset = stroke / 2f
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = White.copy(alpha = 0.08f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke),
                    )
                    drawArc(
                        brush = gradient,
                        startAngle = -90f, sweepAngle = 360f * sweep.value, useCenter = false,
                        topLeft = Offset(inset, inset), size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = count.toString(),
                        style = TextStyle(brush = gradient),
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (count >= 1000) 42.sp else 52.sp,
                        lineHeight = 56.sp,
                    )
                    Text(
                        text = unit.lowercase(),
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = White.copy(alpha = 0.6f),
                    )
                }
            }

            if (minutesToday > 0) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EkIcon(EkIcons.Timer, tint = White.copy(alpha = 0.7f), size = 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${minutesLabel(minutesToday)} today",
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = White.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = line,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))

            PopupButton(
                text = "Take a break",
                primary = true,
                onClick = { onChoice(ReminderPopup.Choice.Break) },
            )
            Spacer(Modifier.height(10.dp))
            PopupButton(
                text = "Remind me in $snooze more",
                primary = false,
                onClick = { onChoice(ReminderPopup.Choice.Later) },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                QuietButton("Not today") { onChoice(ReminderPopup.Choice.NotToday) }
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.3f)),
                )
                QuietButton("Turn off reminders") { onChoice(ReminderPopup.Choice.TurnOff) }
            }
        }
    }
}

/** "42 min", "1 h 5 min". */
private fun minutesLabel(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

@Composable
private fun PopupButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(buttonGradient(), shape)
                } else {
                    Modifier
                        .background(White.copy(alpha = 0.10f), shape)
                        .border(1.dp, White.copy(alpha = 0.12f), shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = White,
        )
    }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = White.copy(alpha = 0.55f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}
