package com.ekaur.android.overlay

/**
 * Where the floating pill sits, and how a message grows around it.
 *
 * Pure arithmetic, kept out of [OverlayHost] so it can be tested without a
 * window. The pill is placed by its **centre**: the window is centred on the
 * resting point and grows symmetrically, so a message spreads equally left and
 * right -- the Dynamic-Island effect -- instead of opening only toward one edge.
 */
object OverlayPlacement {

    /**
     * Keeps a pill of [width] centred at [center] fully on screen, margins
     * included, by clamping the centre to the range its half-width allows.
     */
    fun clampCenter(center: Int, width: Int, screenWidth: Int, margin: Int): Int {
        val half = width / 2
        val min = margin + half
        val max = screenWidth - margin - half
        return if (min > max) screenWidth / 2 else center.coerceIn(min, max)
    }

    /**
     * The widest a pill centred at [center] can grow while **both** edges stay
     * inside the margins -- i.e. symmetric growth around the centre.
     *
     * Because [clampCenter] never lets the centre come closer than half a pill
     * to a margin, this is always at least the collapsed width, so the resting
     * pill always fits; a message parked near an edge simply wraps to two lines
     * rather than pushing off-screen.
     */
    fun symmetricWidth(center: Int, screenWidth: Int, margin: Int): Int =
        (2 * minOf(center - margin, screenWidth - margin - center)).coerceAtLeast(0)

    /**
     * The signed x for a `CENTER_HORIZONTAL` window so its centre lands on
     * [center]. The window manager then keeps that centre fixed as the pill
     * grows, which is what makes the expansion symmetric with no reposition.
     */
    fun centerOffset(center: Int, screenWidth: Int): Int = center - screenWidth / 2

    /**
     * Keeps a stored centre on screen before the pill has ever been measured.
     *
     * A centre saved by an older build, or one that no longer fits after a
     * rotation, must not be applied as-is -- the window would be placed off the
     * display and the user would have nothing to grab.
     */
    fun clampOriginCenter(
        center: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int,
    ): Pair<Int, Int> {
        val cx = center.coerceIn(margin, (screenWidth - margin).coerceAtLeast(margin))
        val cy = y.coerceIn(0, (screenHeight - margin).coerceAtLeast(0))
        return cx to cy
    }
}
