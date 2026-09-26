package com.ekaur.android.overlay

import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.widget.ImageView
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
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
class ReminderPopup(
    private val context: Context,
    /** It closes itself after this long, so nobody is ever stuck behind it. */
    private val autoCloseMs: Long = AUTO_CLOSE_MS,
) {

    /** What the user chose. */
    enum class Choice { Break, Later, NotToday, TurnOff }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoClose = Runnable { dismiss() }

    /** The GIF playing in the card, stopped when it closes. */
    private var playing: Animatable2? = null

    val isShowing: Boolean get() = root != null

    /**
     * Shows the card. **Main thread.** [onChoice] is called once, for a button;
     * Back, the timeout and [dismiss] close it without calling it.
     */
    fun show(
        top: String,
        punchline: String,
        minutesToday: Int,
        snooze: Int,
        meme: Drawable?,
        sticker: String,
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
                    top = top,
                    punchline = punchline,
                    minutesToday = minutesToday,
                    snooze = snooze,
                    meme = meme,
                    sticker = sticker,
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
        playing = meme as? Animatable2
        root = frame
        owner = lifecycle
        frame.performHapticFeedback(HapticFeedbackConstants.CONFIRM.takeIf { Build.VERSION.SDK_INT >= 30 } ?: HapticFeedbackConstants.LONG_PRESS)
        main.postDelayed(autoClose, autoCloseMs)
    }

    /** Closes the card if it is up. **Main thread.** Safe to call any time. */
    fun dismiss() {
        main.removeCallbacks(autoClose)
        runCatching { playing?.stop() }
        playing = null
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
 * A meme, not a lecture: the GIF (or a dancing emoji before one has been
 * fetched) with classic top and bottom text over it, then two buttons. Short
 * on purpose -- it sits over the video, and should read in one glance.
 */
@Composable
internal fun ReminderCard(
    top: String,
    punchline: String,
    minutesToday: Int,
    snooze: Int,
    meme: Drawable?,
    sticker: String,
    onChoice: (ReminderPopup.Choice) -> Unit,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scrim.animateTo(1f, tween(200)) }

    val gradient = instaGradient()
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f * scrim.value))
            // Swallows touches, so the feed cannot scroll on behind the card.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    // Drops in with a little tilt that settles: a sticker slapped on.
                    val v = shown.value
                    alpha = v.coerceIn(0f, 1f)
                    translationY = (1f - v) * 90f
                    rotationZ = (1f - v) * -4f
                    scaleX = 0.9f + 0.1f * v
                    scaleY = 0.9f + 0.1f * v
                }
                .shadow(32.dp, shape, ambientColor = Color(0xFFDD2A7B), spotColor = Color(0xFFDD2A7B))
                .clip(shape)
                .background(Night)
                .border(1.5.dp, gradient, shape)
                .padding(12.dp),
        ) {
            MemePanel(top = top, punchline = punchline, meme = meme, sticker = sticker)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (minutesToday > 0) {
                    EkIcon(EkIcons.Timer, tint = White.copy(alpha = 0.55f), size = 13.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${minutesLabel(minutesToday)} today",
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = White.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (meme != null) {
                    // GIPHY's terms ask for this wherever their GIFs appear.
                    Text(
                        text = "Powered by GIPHY",
                        fontFamily = Poppins,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = White.copy(alpha = 0.4f),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PopupButton(
                    text = "Take a break",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onChoice(ReminderPopup.Choice.Break) },
                )
                PopupButton(
                    // The snooze, in the app's own words.
                    text = "Ek aur $snooze 😏",
                    primary = false,
                    onClick = { onChoice(ReminderPopup.Choice.Later) },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton("Not today") { onChoice(ReminderPopup.Choice.NotToday) }
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.3f)),
                )
                QuietButton("Turn off reminders") { onChoice(ReminderPopup.Choice.TurnOff) }
            }
        }
    }
}

/** The meme: the picture, with outlined meme text across the top and bottom. */
@Composable
private fun MemePanel(top: String, punchline: String, meme: Drawable?, sticker: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E1E24)),
        contentAlignment = Alignment.Center,
    ) {
        if (meme != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(meme)
                        (meme as? Animatable2)?.start()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Sticker(sticker)
        }
        // A soft shade top and bottom, so the text reads on any frame.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
        MemeText(top, Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 12.dp, end = 12.dp))
        MemeText(punchline, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 12.dp, end = 12.dp))
    }
}

/** Classic meme text: heavy white capitals with a black outline. */
@Composable
private fun MemeText(text: String, modifier: Modifier = Modifier) {
    val caps = text.uppercase()
    val style = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
        textAlign = TextAlign.Center,
    )
    Box(modifier) {
        Text(
            text = caps,
            style = style.copy(
                color = Color.Black,
                drawStyle = Stroke(width = 7f, join = StrokeJoin.Round),
            ),
            maxLines = 2,
        )
        Text(text = caps, style = style.copy(color = White), maxLines = 2)
    }
}

/** Before any GIF has been fetched: a big emoji doing a little dance. */
@Composable
private fun Sticker(emoji: String) {
    val dance = rememberInfiniteTransition(label = "sticker")
    val tilt by dance.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt",
    )
    val bob by dance.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF3A2150), Color(0xFF1E1E24)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 76.sp,
            // Sits a little high, clear of a two-line punchline below it.
            modifier = Modifier.padding(bottom = 16.dp).graphicsLayer {
                rotationZ = tilt
                translationY = -bob * 14f
                scaleX = 1f + bob * 0.06f
                scaleY = 1f - bob * 0.04f
            },
        )
    }
}

/** "42 min", "1 h 5 min". */
private fun minutesLabel(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

@Composable
private fun PopupButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(50.dp)
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
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = Poppins,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = White,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = White.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}
