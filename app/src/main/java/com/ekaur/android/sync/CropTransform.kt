package com.ekaur.android.sync

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the crop window sits over the photo while the user drags and pinches.
 *
 * Pure arithmetic, away from the gesture handling, because this is the part
 * that has to be right: a rounding slip here is a crop that reads a pixel
 * outside the bitmap, and `Bitmap.createBitmap` answers that with a crash on a
 * screen that has no way to report one.
 *
 * One coordinate convention throughout. The window is a square of side
 * [window] screen pixels; the photo is drawn at [scale] screen pixels per image
 * pixel, with its top-left corner at `offset` measured from the window's
 * top-left corner. Offsets are therefore zero or negative -- the photo always
 * hangs off the window rather than sitting inside it.
 */
object CropTransform {

    /** Far enough in to crop a face out of a group photo, and no further. */
    const val MAX_ZOOM = 6f

    /**
     * The smallest scale that still covers the window.
     *
     * Below this a corner of the window would be empty, so the user could
     * produce a picture with a bite out of it.
     */
    fun minScale(imageWidth: Int, imageHeight: Int, window: Float): Float {
        if (imageWidth <= 0 || imageHeight <= 0 || window <= 0f) return 1f
        return max(window / imageWidth, window / imageHeight)
    }

    fun clampScale(scale: Float, imageWidth: Int, imageHeight: Int, window: Float): Float {
        val floor = minScale(imageWidth, imageHeight, window)
        return scale.coerceIn(floor, floor * MAX_ZOOM)
    }

    /**
     * Keeps one axis of the photo covering the window.
     *
     * [extent] is the photo's size along that axis, in image pixels.
     */
    fun clampOffset(offset: Float, extent: Int, scale: Float, window: Float): Float {
        val drawn = extent * scale
        // Only reachable if the scale was not clamped first; centring is the
        // sane answer rather than an empty edge.
        if (drawn <= window) return (window - drawn) / 2f
        return offset.coerceIn(window - drawn, 0f)
    }

    /**
     * The square of the photo currently framed, in image pixels.
     *
     * Always inside the bitmap and always at least one pixel, whatever the
     * floating-point state of the gesture that produced it.
     */
    fun crop(
        imageWidth: Int,
        imageHeight: Int,
        window: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Avatar.Crop {
        if (imageWidth <= 0 || imageHeight <= 0) return Avatar.Crop(0, 0, 0)
        if (window <= 0f || scale <= 0f) return Avatar.centreCrop(imageWidth, imageHeight)

        val side = (window / scale)
            .roundToInt()
            .coerceIn(1, min(imageWidth, imageHeight))

        val x = (-offsetX / scale).roundToInt().coerceIn(0, imageWidth - side)
        val y = (-offsetY / scale).roundToInt().coerceIn(0, imageHeight - side)
        return Avatar.Crop(x, y, side)
    }

    /**
     * The offset that centres the photo, used when a new one is opened.
     *
     * Starting centred at the minimum scale means the first thing the user sees
     * is exactly what they would have got without touching anything.
     */
    fun centreOffset(extent: Int, scale: Float, window: Float): Float =
        (window - extent * scale) / 2f
}
