package com.ekaur.android.overlay

import android.content.Context

/**
 * Where the user last parked the floating pill.
 *
 * Shared between the service, which writes it, and the setup screen, which can
 * clear it. An earlier build let the pill be dragged off the display with no way
 * to get it back, and the only recourse was uninstalling the app -- so putting
 * the reset within reach of the UI is the point of this class existing.
 */
class OverlayPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    val hasSavedPosition: Boolean get() = prefs.contains(KEY_X)

    fun x(default: Int): Int = prefs.getInt(KEY_X, default)

    fun y(default: Int): Int = prefs.getInt(KEY_Y, default)

    fun save(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    /** Forgets the position so the pill returns to its default spot. */
    fun clearPosition() {
        prefs.edit().remove(KEY_X).remove(KEY_Y).apply()
    }

    private companion object {
        const val NAME = "overlay"
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
