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

    /** Which edge the window is anchored to right now. */
    private var anchoredRight = false

    /** The last collapsed width seen, so identical measurements cost nothing. */
    private var lastCollapsedWidth = -1

    /** Set when a drag ended mid-message and its anchoring still has to land. */
    private var placementPending = false

    /** True while a line is showing and the window is stretched full width. */
    private var inMessageMode = false

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
                // A line stretches the window to full width, then it returns to
                // the parked compact pill. Driven from composition so the window
                // change and the pill's own fillMaxWidth land on the same signal.
                LaunchedEffect(message != null) { setMessageMode(message != null) }
                IslandPill(
                    count = count,
                    message = message,
                    maxWidthPx = maxWidth,
                    // Reports the width of the pill without its message, which
                    // is the only width this window is ever placed against. An
                    // expanded pill must never influence where the pill rests,
                    // and it does not need to: the anchored edge is fixed, so
                    // the window grows inward by itself when a message arrives.
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
        placementPending = false
        inMessageMode = false
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
            // Absolute LEFT rather than START: an RTL locale must not flip which
            // edge the pill is anchored to. Swapped to RIGHT once the collapsed
            // width is known and the pill turns out to be parked on that half.
            gravity = Gravity.TOP or Gravity.LEFT
            // Clamped on read: a position stored by an older build, or one that
            // no longer fits after a rotation, must never be applied off-screen.
            val (safeX, safeY) = OverlayPlacement.clampOrigin(
                x = prefs.x(defaultX()),
                y = prefs.y(DEFAULT_Y),
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                margin = marginPx,
            )
            x = safeX
            y = safeY
        }

        // The window starts left-anchored at the stored position, which is the
        // same absolute place it would sit under either anchoring. If the pill
        // turns out to be parked on the right half, the first collapsed
        // measurement swaps the gravity for an identical position -- so the
        // swap is invisible, and nothing has moved.
        anchoredRight = false
        lastCollapsedWidth = -1
        placementPending = false
        placement.settle(layout.x)
        availableWidth.value =
            OverlayPlacement.availableWidth(layout.x, screenWidth, marginPx)
        return layout
    }

    /**
     * Learns where the pill rests, and anchors the window to the nearer edge.
     *
     * Runs only for collapsed measurements, and in practice only when the pill
     * is first shown or the number gains a digit. A message never reaches this:
     * the anchored edge stays fixed while the window grows inward, so there is
     * nothing to reposition and nothing that could overwrite the resting spot.
     */
    private fun onCollapsedMeasured(width: Int) {
        if (width <= 0) return
        // While a line stretches the window full width, the collapsed core is
        // still measured -- record it so the parked placement is right on the way
        // back down, but do not re-anchor now or it would fight the full width.
        if (inMessageMode) {
            lastCollapsedWidth = width
            return
        }
        if (width == lastCollapsedWidth && !placementPending) return
        val view = composeView ?: return
        val layout = params ?: return

        lastCollapsedWidth = width
        placementPending = false
        applyPlacement(view, layout, placement.onCollapsedMeasure(width, screenWidth, marginPx))
    }

    /**
     * Stretches the window to full width for a line, then restores the parked
     * compact pill. The user keeps a freely draggable pill; a message simply
     * fills margin-to-margin so it reads as evenly distributed, wherever the pill
     * was parked. Runs on the main thread (called from composition).
     */
    private fun setMessageMode(present: Boolean) {
        if (present == inMessageMode) return
        val view = composeView ?: return
        val layout = params ?: return
        inMessageMode = present

        if (present) {
            layout.gravity = Gravity.TOP or Gravity.LEFT
            layout.x = marginPx
            layout.width = screenWidth - 2 * marginPx
            anchoredRight = false
            availableWidth.value = screenWidth - 2 * marginPx
        } else {
            // Back to the parked position. Re-measure through the placement so a
            // digit gained while the line was up is accounted for.
            val w = if (lastCollapsedWidth > 0) lastCollapsedWidth else placement.collapsedWidth
            val placed = if (w > 0) {
                placement.onCollapsedMeasure(w, screenWidth, marginPx)
            } else {
                placement.placement(screenWidth, marginPx)
            }
            layout.width = WindowManager.LayoutParams.WRAP_CONTENT
            layout.gravity = Gravity.TOP or if (placed.anchorsRight) Gravity.RIGHT else Gravity.LEFT
            layout.x = placed.offset
            anchoredRight = placed.anchorsRight
            availableWidth.value = placed.availableWidth
        }
        runCatching { windowManager.updateViewLayout(view, layout) }
    }

    /** Applies a resolved [Placement] to the window, if anything changed. */
    private fun applyPlacement(
        view: android.view.View,
        layout: WindowManager.LayoutParams,
        placed: Placement,
    ) {
        availableWidth.value = placed.availableWidth

        val edge = if (placed.anchorsRight) Gravity.RIGHT else Gravity.LEFT
        val gravity = Gravity.TOP or edge
        if (layout.gravity == gravity && layout.x == placed.offset) return

        layout.gravity = gravity
        layout.x = placed.offset
        anchoredRight = placed.anchorsRight
        // Posted rather than applied inline: this runs from layout, and the
        // window manager must not be reentered mid-pass.
        view.post { runCatching { windowManager.updateViewLayout(view, layout) } }
    }

    private fun defaultX(): Int {
        val width = context.resources.displayMetrics.widthPixels
        // Roughly centred; the pill is small and centres itself well enough.
        return (width * 0.42f).roundToInt()
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

        private var startX = 0
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

        override fun onTouch(view: android.view.View, event: MotionEvent): Boolean {
            gesture.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    // x is measured from the anchored edge, so under right
                    // anchoring the offset shrinks as the finger moves right.
                    val travel = (event.rawX - touchX).roundToInt()
                    // Clamped so the pill cannot be pushed off the display and
                    // left unreachable.
                    layout.x = OverlayPlacement.clamp(
                        x = startX + if (anchoredRight) -travel else travel,
                        width = view.width,
                        screenWidth = screenWidth,
                        margin = marginPx,
                    )
                    val maxY = (screenHeight - view.height).coerceAtLeast(0)
                    layout.y = (startY + (event.rawY - touchY).roundToInt()).coerceIn(0, maxY)
                    runCatching { windowManager.updateViewLayout(view, layout) }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Dropping it on the other half of the screen flips which
                    // way the next message opens. Dropping it while a message is
                    // showing keeps the anchored edge, so the pill stays under
                    // where it was let go once the message goes.
                    val placed = placement.onDragEnd(
                        offset = layout.x,
                        anchoredRight = anchoredRight,
                        viewWidth = view.width,
                        screenWidth = screenWidth,
                        margin = marginPx,
                    )
                    if (lastCollapsedWidth > 0 && view.width > lastCollapsedWidth) {
                        // A message is still on screen. Re-anchoring now would
                        // shift the line mid-sentence and re-measure it, so the
                        // new anchoring lands when the pill next collapses.
                        placementPending = true
                    } else {
                        applyPlacement(view, layout, placed)
                    }
                    // The resting left edge is stored, never a dragged offset or
                    // an expanded one, so a reload lands back on the same spot.
                    prefs.save(placement.restingLeft, layout.y)
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
