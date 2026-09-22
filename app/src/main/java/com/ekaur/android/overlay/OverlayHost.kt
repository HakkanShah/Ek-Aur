package com.ekaur.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Owns the floating counter window.
 *
 * The window is deliberately non-focusable and non-modal: taps anywhere outside
 * the pill pass straight through to whatever is underneath. Getting this wrong
 * makes Instagram unusable, which is a far worse outcome than the pill being in
 * an awkward place, so the flags are conservative and the pill can be dragged.
 */
class OverlayHost(
    private val context: Context,
    private val counts: StateFlow<Int>,
    private val announcer: MilestoneAnnouncer,
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private val prefs = OverlayPrefs(context)

    /** Where the pill rests. Never written while a message is on screen. */
    private val placement = PillPlacement()

    /** The last collapsed width seen, so identical measurements cost nothing. */
    private var lastCollapsedWidth = -1

    /**
     * How wide the pill may grow, in pixels.
     *
     * Handed to the pill rather than a fixed cap so a message is bounded by the
     * room it actually has beside it. Recomputed on show and at the end of a
     * drag, never during one, so the line is never re-measured mid-gesture.
     */
    private val availableWidth = MutableStateFlow(0)

    private val marginPx: Int
        get() = (MARGIN_DP * context.resources.displayMetrics.density).roundToInt()

    private val screenWidth: Int
        get() = context.resources.displayMetrics.widthPixels

    private val screenHeight: Int
        get() = context.resources.displayMetrics.heightPixels

    val isShowing: Boolean get() = composeView != null

    fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        if (!canDrawOverlay()) return

        val owner = OverlayLifecycleOwner().apply { create() }
        val layout = buildParams()

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val count by counts.collectAsState()
                val message by announcer.message.collectAsState()
                val maxWidth by availableWidth.collectAsState()
                // A message needs room the collapsed pill does not have, and a
                // WRAP_CONTENT overlay window does not reliably grow itself for
                // it. So the host explicitly widens the (centred) window while a
                // line shows and returns it to wrap-content after -- driven from
                // composition so the window change lands with the message.
                LaunchedEffect(message != null) { setMessageMode(message != null) }
                IslandPill(
                    count = count,
                    message = message,
                    maxWidthPx = maxWidth,
                    onCollapsedWidth = ::onCollapsedMeasured,
                )
            }
            setOnTouchListener(DragListener(layout))
        }

        runCatching { windowManager.addView(view, layout) }
            .onFailure {
                owner.destroy()
                return
            }

        owner.start()
        composeView = view
        lifecycleOwner = owner
        params = layout
    }

    fun hide() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        // Order matters: the view is detached first, then the lifecycle ends.
        // Destroying while still attached leaves the recomposer running and
        // leaks the store on every show/hide cycle.
        view.disposeComposition()
        lifecycleOwner?.destroy()
        composeView = null
        lifecycleOwner = null
        params = null
        // The next show builds fresh params, so the remembered width belongs to
        // a window that no longer exists and must not suppress its placement.
        lastCollapsedWidth = -1
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // NOT_FOCUSABLE keeps the keyboard and back button with the app
            // underneath; NOT_TOUCH_MODAL lets taps outside the pill through.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Centre-anchored: the window's centre stays put and the pill grows
            // symmetrically around it, so a message spreads equally both ways.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Clamped on read: a centre stored by an older build, or one that no
            // longer fits after a rotation, must never be applied off-screen.
            val (safeCenter, safeY) = OverlayPlacement.clampOriginCenter(
                center = prefs.x(defaultCenter()),
                y = prefs.y(DEFAULT_Y),
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                margin = marginPx,
            )
            x = OverlayPlacement.centerOffset(safeCenter, screenWidth)
            y = safeY
            placement.settle(safeCenter)
        }

        lastCollapsedWidth = -1
        availableWidth.value =
            OverlayPlacement.symmetricWidth(placement.restingCenter, screenWidth, marginPx)
        return layout
    }

    /**
     * Learns where the pill rests and centres the window on it.
     *
     * Runs only for collapsed measurements, and in practice only when the pill
     * is first shown or the number gains a digit. A message never reaches this:
     * the core (face + number) is measured on its own, so its width does not
     * change when a line appears, and the centre stays fixed while the window
     * grows symmetrically.
     */
    private fun onCollapsedMeasured(width: Int) {
        if (width <= 0) return
        if (width == lastCollapsedWidth) return
        val view = composeView ?: return
        val layout = params ?: return

        lastCollapsedWidth = width
        applyPlacement(view, layout, placement.onCollapsedMeasure(width, screenWidth, marginPx))
    }

    /**
     * Gives a message the room it needs, then takes it back.
     *
     * A `WRAP_CONTENT` overlay window does not reliably grow itself when its
     * content grows, so a milestone line was measured but the window stayed at
     * the collapsed width and clipped it -- the "no text on a milestone" bug. So
     * the width is set explicitly: to the symmetric room while a line shows, and
     * back to wrap-content after. The window stays centred on the resting spot
     * throughout, and the pill (a centred box) fills that width, so the line
     * spreads evenly to both sides. Runs on the main thread (from composition).
     */
    private fun setMessageMode(present: Boolean) {
        val view = composeView ?: return
        val layout = params ?: return
        layout.width = if (present) {
            OverlayPlacement.symmetricWidth(placement.restingCenter, screenWidth, marginPx)
                .coerceAtLeast(lastCollapsedWidth)
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        layout.x = OverlayPlacement.centerOffset(placement.restingCenter, screenWidth)
        runCatching { windowManager.updateViewLayout(view, layout) }
    }

    /** Applies a resolved [Placement] to the window, if anything changed. */
    private fun applyPlacement(
        view: android.view.View,
        layout: WindowManager.LayoutParams,
        placed: Placement,
    ) {
        availableWidth.value = placed.availableWidth
        if (layout.x == placed.offset) return

        layout.x = placed.offset
        // Posted rather than applied inline: this runs from layout, and the
        // window manager must not be reentered mid-pass.
        view.post { runCatching { windowManager.updateViewLayout(view, layout) } }
    }

    private fun defaultCenter(): Int {
        // Dead-centre at the top, so the pill reads like a status island by
        // default and a message opens evenly to both sides.
        return context.resources.displayMetrics.widthPixels / 2
    }

    /** Brings the app to the front -- what a double-tap on the pill does. */
    private fun openApp() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        runCatching { context.startActivity(intent) }
    }

    /** Lets the pill be moved out of the way of whatever it is covering. */
    private inner class DragListener(
        private val layout: WindowManager.LayoutParams,
    ) : android.view.View.OnTouchListener {

        // The pill's centre when the drag began, in absolute screen pixels.
        private var startCenter = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        // A double-tap opens the app. Drag (a moving finger) and a double-tap
        // (two quick taps that barely move) don't collide, so both can be live.
        private val gesture = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    openApp()
                    return true
                }
            },
        )

        // The width to clamp the centre against: the resting pill, not whatever
        // wide thing a message has made the view right now.
        private fun clampWidth(view: android.view.View): Int =
            if (lastCollapsedWidth > 0) lastCollapsedWidth else view.width

        override fun onTouch(view: android.view.View, event: MotionEvent): Boolean {
            gesture.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startCenter = layout.x + screenWidth / 2
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    // The centre moves with the finger, clamped so the collapsed
                    // pill can never be pushed off the display and left stranded.
                    val travel = (event.rawX - touchX).roundToInt()
                    val center = OverlayPlacement.clampCenter(
                        center = startCenter + travel,
                        width = clampWidth(view),
                        screenWidth = screenWidth,
                        margin = marginPx,
                    )
                    layout.x = OverlayPlacement.centerOffset(center, screenWidth)
                    val maxY = (screenHeight - view.height).coerceAtLeast(0)
                    layout.y = (startY + (event.rawY - touchY).roundToInt()).coerceIn(0, maxY)
                    runCatching { windowManager.updateViewLayout(view, layout) }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // The centre where it was let go becomes the resting centre.
                    // Because the window stays centred, the pill collapses back
                    // under exactly here, message on screen or not.
                    val center = layout.x + screenWidth / 2
                    val placed = placement.onDragEnd(center, screenWidth, marginPx)
                    applyPlacement(view, layout, placed)
                    prefs.save(placement.restingCenter, layout.y)
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val DEFAULT_Y = 90
        const val MARGIN_DP = 8f
    }
}
