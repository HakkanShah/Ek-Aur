package com.ekaur.android.overlay

/**
 * Where the pill rests, and how much room a message has around it.
 *
 * The resting **centre** is the one piece of authoritative state. It changes
 * only when the stored position is loaded or when a drag ends -- never as a side
 * effect of a message appearing. The window is centred on it and grows
 * symmetrically, so the pill never moves when it opens: nothing here computes a
 * new position for an expanding pill, because the window's centre is fixed.
 */
class PillPlacement {

    /** Absolute centre of the collapsed pill. The one piece of real state. */
    var restingCenter: Int = 0
        private set

    /** Learned from the first collapsed measurement. */
    var collapsedWidth: Int = 0
        private set

    /** Applies a stored position, on show or after a reset. */
    fun settle(center: Int) {
        restingCenter = center
    }

    /**
     * Records a collapsed measurement and returns the geometry to apply.
     *
     * Only collapsed measurements reach this; an expanded pill must never move
     * the resting centre. Re-clamps in case a gained digit widened the pill near
     * an edge.
     */
    fun onCollapsedMeasure(width: Int, screenWidth: Int, margin: Int): Placement {
        collapsedWidth = width
        restingCenter = OverlayPlacement.clampCenter(restingCenter, width, screenWidth, margin)
        return placement(screenWidth, margin)
    }

    /**
     * Takes a finished drag and makes its centre the new resting position.
     *
     * Because the window is centred, the pill collapses back under exactly where
     * it was dropped, whether or not a message was on screen when it was let go.
     */
    fun onDragEnd(center: Int, screenWidth: Int, margin: Int): Placement {
        val width = collapsedWidth.coerceAtLeast(1)
        restingCenter = OverlayPlacement.clampCenter(center, width, screenWidth, margin)
        return placement(screenWidth, margin)
    }

    /** The geometry the current resting centre implies. */
    fun placement(screenWidth: Int, margin: Int): Placement = Placement(
        offset = OverlayPlacement.centerOffset(restingCenter, screenWidth),
        availableWidth = OverlayPlacement.symmetricWidth(restingCenter, screenWidth, margin),
    )
}

/**
 * How the overlay window should be placed.
 *
 * [offset] is the signed x for a `CENTER_HORIZONTAL` window, so the window
 * manager keeps the pill centred and grows it symmetrically on its own when a
 * message arrives -- no reposition, and nothing that could move the pill.
 */
data class Placement(
    val offset: Int,
    val availableWidth: Int,
)
