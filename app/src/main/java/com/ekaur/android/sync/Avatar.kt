package com.ekaur.android.sync

/**
 * The arithmetic behind turning a camera photo into a profile picture.
 *
 * Kept away from the bitmap work so the awkward parts are settled on the JVM.
 * A modern phone photo is 4000x3000 and about 48MB decoded, which is enough to
 * kill the app outright, so nothing here ever loads a full-size image: the
 * sample size is chosen first and the decoder never sees the original.
 */
object Avatar {

    /** Displayed at most ~56dp, so 256 is already generous on a dense screen. */
    const val SIZE = 256

    /**
     * The power-of-two shrink factor to decode with.
     *
     * Always leaves the decoded image at least [target] on its shorter side, so
     * the crop that follows never has to enlarge anything.
     */
    fun sampleSizeFor(width: Int, height: Int, target: Int = SIZE): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        var sample = 1
        var shorter = minOf(width, height)
        while (shorter / 2 >= target) {
            shorter /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * The size to decode a photo at before the user frames it.
     *
     * [sampleSizeFor] can only halve, so a 4032x3024 photo lands at 2016x1512
     * and about 12MB -- fine on a good phone, not on a cheap one. Where the
     * decoder can scale freely this pins the shorter side to [target] exactly,
     * so the working bitmap is the same size whatever came in. A photo already
     * smaller than that is left alone rather than enlarged.
     */
    fun workingSize(width: Int, height: Int, target: Int = 1024): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || target <= 0) return width to height
        val shorter = minOf(width, height)
        if (shorter <= target) return width to height
        val factor = target.toDouble() / shorter
        return maxOf(1, Math.round(width * factor).toInt()) to
            maxOf(1, Math.round(height * factor).toInt())
    }

    /** The centred square to take from a [width] x [height] image. */
    fun centreCrop(width: Int, height: Int): Crop {
        val side = minOf(width, height).coerceAtLeast(0)
        return Crop(
            x = (width - side) / 2,
            y = (height - side) / 2,
            side = side,
        )
    }

    /**
     * Where a person's picture lives.
     *
     * [version] busts the cache: the file keeps the same name for ever, so
     * without it every phone would go on showing the picture it first saw.
     */
    fun urlFor(baseUrl: String, userId: String, version: Long?): String? {
        if (version == null || userId.isBlank()) return null
        return "${baseUrl.trimEnd('/')}/storage/v1/object/public/avatars/$userId.webp?v=$version"
    }

    /** One letter for the circle shown when somebody has no picture. */
    fun initialOf(username: String): String =
        username.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

    data class Crop(val x: Int, val y: Int, val side: Int)
}
