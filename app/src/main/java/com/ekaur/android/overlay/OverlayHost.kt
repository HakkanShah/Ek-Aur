package com.ekaur.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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

    /** Where the pill sits when it has nothing to say, and which edge it owns. */
    private var collapsedLeft = 0
    private var collapsedRight = 0
    private var anchorsRight = false

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
                IslandPill(
                    count = count,
                    message = message,
                    // Re-placed on every size change so an expanding message
                    // grows inward from the edge the pill is parked on.
                    modifier = Modifier.onSizeChanged { size ->
                        onPillMeasured(size.width, expanded = message != null)
                    },
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
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
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
            gravity = Gravity.TOP or Gravity.START
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
    }

    /**
     * Re-places the window whenever the pill's width changes.
     *
     * The collapsed layout defines the anchor; an expanded one is positioned
     * relative to it so the pill appears to stay put while the message opens
     * away from the nearer screen edge.
     */
    private fun onPillMeasured(width: Int, expanded: Boolean) {
        if (width <= 0) return
        val view = composeView ?: return
        val layout = params ?: return

        if (!expanded) {
            collapsedLeft = layout.x
            collapsedRight = layout.x + width
            anchorsRight = OverlayPlacement.anchorsRight(layout.x, width, screenWidth)
        }

        val target = OverlayPlacement.resolveX(
            collapsedLeft = collapsedLeft,
            collapsedRight = collapsedRight,
            width = width,
            screenWidth = screenWidth,
            anchorsRight = anchorsRight,
            margin = marginPx,
        )
        if (target == layout.x) return

        layout.x = target
        // Posted rather than applied inline: this runs from layout, and the
        // window manager must not be reentered mid-pass.
        view.post { runCatching { windowManager.updateViewLayout(view, layout) } }
    }

    private fun defaultX(): Int {
        val width = context.resources.displayMetrics.widthPixels
        // Roughly centred; the pill is small and centres itself well enough.
        return (width * 0.42f).roundToInt()
    }

    /** Lets the pill be moved out of the way of whatever it is covering. */
    private inner class DragListener(
        private val layout: WindowManager.LayoutParams,
    ) : android.view.View.OnTouchListener {

        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(view: android.view.View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Clamped so the pill cannot be pushed off the display and
                    // left unreachable.
                    layout.x = OverlayPlacement.clamp(
                        x = startX + (event.rawX - touchX).roundToInt(),
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
                    // way the next message opens.
                    collapsedLeft = layout.x
                    collapsedRight = layout.x + view.width
                    anchorsRight =
                        OverlayPlacement.anchorsRight(layout.x, view.width, screenWidth)
                    prefs.save(layout.x, layout.y)
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
