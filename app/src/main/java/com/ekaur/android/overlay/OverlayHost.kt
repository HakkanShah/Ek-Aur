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

    private val prefs = context.getSharedPreferences("overlay", Context.MODE_PRIVATE)

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
                IslandPill(count = count, message = message)
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
            x = prefs.getInt(KEY_X, defaultX())
            y = prefs.getInt(KEY_Y, DEFAULT_Y)
        }
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
                    layout.x = startX + (event.rawX - touchX).roundToInt()
                    layout.y = startY + (event.rawY - touchY).roundToInt()
                    runCatching { windowManager.updateViewLayout(view, layout) }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit()
                        .putInt(KEY_X, layout.x)
                        .putInt(KEY_Y, layout.y)
                        .apply()
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val DEFAULT_Y = 90
    }
}
