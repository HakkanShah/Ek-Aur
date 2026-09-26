package com.ekaur.android.ui.onboarding

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ekaur.android.MainActivity
import com.ekaur.android.overlay.OverlayLifecycleOwner
import com.ekaur.android.service.KeepAlive
import com.ekaur.android.service.ServiceControl
import com.ekaur.android.ui.common.EkIcon
import com.ekaur.android.ui.common.EkIcons
import com.ekaur.android.ui.theme.Poppins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A small card that floats over Settings while someone is doing a setup step.
 *
 * The moment people left the app for Settings, the instructions were gone:
 * friends reported not knowing which row to tap or where the menu was. With
 * the overlay switched on (setup's first step) the app can keep a tiny film
 * of the exact taps on screen the whole time they're in Settings, and watch
 * for the step to go through -- then it brings them back to the app by itself.
 *
 * Android hides other apps' windows on a few sensitive Settings screens (the
 * accessibility permission popup, for one). The card simply disappears there
 * and comes back after; nothing here fights that.
 */
object SetupGuide {

    enum class Kind { Accessibility, Restricted, Restart, Autostart, Usage }

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null
    private var view: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private val kind = mutableStateOf(Kind.Accessibility)
    private val success = mutableStateOf(false)

    /**
     * Shows the card for [what] (if the app may draw over others) and starts
     * watching for the step to go through.
     */
    fun start(context: Context, what: Kind) {
        val app = context.applicationContext
        kind.value = what
        success.value = false
        show(app)
        watch(app, timeoutMs = 5 * 60_000L)
    }

    /**
     * Only watches: brings the app back when [done] turns true. For the
     * overlay switch itself, where no card can be drawn yet -- but the moment
     * it's granted, the app is allowed to come back to the front.
     */
    fun returnWhen(context: Context, timeoutMs: Long = 3 * 60_000L, done: (Context) -> Boolean) {
        val app = context.applicationContext
        watchJob?.cancel()
        watchJob = main.launch {
            val until = System.currentTimeMillis() + timeoutMs
            while (isActive && System.currentTimeMillis() < until) {
                delay(POLL_MS)
                if (done(app)) {
                    bringBack(app)
                    return@launch
                }
            }
        }
    }

    /** Called when the app is back in front: the card has done its job. */
    fun stop() {
        watchJob?.cancel()
        watchJob = null
        remove()
    }

    private fun watch(app: Context, timeoutMs: Long) {
        watchJob?.cancel()
        watchJob = main.launch {
            val until = System.currentTimeMillis() + timeoutMs
            while (isActive && System.currentTimeMillis() < until) {
                delay(POLL_MS)
                when (kind.value) {
                    Kind.Accessibility, Kind.Restart -> if (ServiceControl.isAccessibilityServiceRunning(app)) {
                        finish(app)
                        return@launch
                    }
                    Kind.Restricted -> if (ServiceControl.restrictedSettingsOpMode(app) == AppOpsManager.MODE_ALLOWED) {
                        // Unblocked: take them straight to the switch, and
                        // change the card to match.
                        kind.value = Kind.Accessibility
                        ServiceControl.openAccessibilityServiceDetails(app)
                    }
                    Kind.Autostart -> if (KeepAlive.autostart(app) == true) {
                        finish(app)
                        return@launch
                    }
                    Kind.Usage -> if (ServiceControl.hasUsageAccess(app)) {
                        finish(app)
                        return@launch
                    }
                }
            }
            remove()
        }
    }

    private suspend fun finish(app: Context) {
        success.value = true
        delay(900)
        bringBack(app)
        remove()
    }

    private fun bringBack(app: Context) {
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        runCatching { app.startActivity(intent) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(app: Context) {
        if (view != null) return
        if (!Settings.canDrawOverlays(app)) return
        val wm = app.getSystemService(WindowManager::class.java) ?: return

        val lifecycle = OverlayLifecycleOwner().apply { create() }
        val frame = FrameLayout(app)
        // On the root: Compose looks for the owners from the window's root view.
        frame.setViewTreeLifecycleOwner(lifecycle)
        frame.setViewTreeViewModelStoreOwner(lifecycle)
        frame.setViewTreeSavedStateRegistryOwner(lifecycle)
        val hint = ServiceControl.oemHint()
        frame.addView(
            ComposeView(app).apply {
                setContent {
                    GuideCard(
                        kind = kind.value,
                        success = success.value,
                        listSection = hint.listSection,
                        onClose = { main.launch { stop() } },
                    )
                }
            },
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Never takes focus or touches outside itself: Settings stays fully usable.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = (app.resources.displayMetrics.density * 28).toInt()
        }
        val added = runCatching { wm.addView(frame, params) }.isSuccess
        if (!added) {
            lifecycle.destroy()
            return
        }
        lifecycle.start()
        view = frame
        owner = lifecycle
    }

    private fun remove() {
        val v = view ?: return
        val wm = v.context.getSystemService(WindowManager::class.java)
        runCatching { wm?.removeViewImmediate(v) }
        owner?.destroy()
        owner = null
        view = null
    }

    private const val POLL_MS = 600L
}

/** What the floating card says for each step: a title and at most two short lines. */
internal fun guideWords(kind: SetupGuide.Kind, listSection: String): Pair<String, List<String>> = when (kind) {
    SetupGuide.Kind.Accessibility -> "Switch on Ek Aur" to listOf("Find “$listSection” → Ek Aur", "Turn it on → Allow")
    SetupGuide.Kind.Restricted -> "Unblock Ek Aur" to listOf("Tap ⋮ at the top right", "→ Allow restricted settings")
    SetupGuide.Kind.Restart -> "Restart Ek Aur" to listOf("Turn the switch off", "then on again → Allow")
    SetupGuide.Kind.Autostart -> "Turn on Autostart" to listOf("Find Ek Aur in the list", "Turn its switch on")
    SetupGuide.Kind.Usage -> "Allow usage access" to listOf("Find Ek Aur in the list", "Turn its switch on")
}

@Composable
internal fun GuideCard(
    kind: SetupGuide.Kind,
    success: Boolean,
    listSection: String,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF16161B))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color(0xFF515BD4), Color(0xFFDD2A7B), Color(0xFFF58529))),
                    RoundedCornerShape(24.dp),
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scale = 0.36f
            when (kind) {
                SetupGuide.Kind.Accessibility -> AccessibilitySim(listSection, scale = scale, showCaption = false)
                SetupGuide.Kind.Restricted -> RestrictedSim(scale = scale, showCaption = false)
                SetupGuide.Kind.Restart -> RestartSim(scale = scale, showCaption = false)
                SetupGuide.Kind.Autostart -> AutostartSim(scale = scale, showCaption = false)
                SetupGuide.Kind.Usage -> AutostartSim(scale = scale, showCaption = false, screenTitle = "Usage access")
            }
            Spacer(Modifier.width(14.dp))
            AnimatedContent(
                targetState = success to kind,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "guide-words",
            ) { (done, k) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (done) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF22C55E)),
                                contentAlignment = Alignment.Center,
                            ) { EkIcon(EkIcons.Check, tint = Color.White, size = 16.dp) }
                            Spacer(Modifier.width(8.dp))
                            GuideText("Done!", 17, FontWeight.Bold, Color.White)
                        }
                        GuideText("Taking you back…", 13, FontWeight.Normal, Color(0xCCFFFFFF))
                    } else {
                        val (title, lines) = guideWords(k, listSection)
                        GuideText("EK AUR GUIDE", 10, FontWeight.Bold, Color(0xFFFF6FA5))
                        GuideText(title, 17, FontWeight.Bold, Color.White)
                        Spacer(Modifier.height(2.dp))
                        lines.forEach { GuideText(it, 13, FontWeight.Medium, Color(0xD9FFFFFF)) }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { EkIcon(EkIcons.Close, tint = Color.White, size = 14.dp) }
    }
}

@Composable
private fun GuideText(text: String, size: Int, weight: FontWeight, color: Color) {
    Text(text = text, color = color, fontSize = size.sp, lineHeight = (size * 1.25).sp, fontWeight = weight, fontFamily = Poppins)
}
