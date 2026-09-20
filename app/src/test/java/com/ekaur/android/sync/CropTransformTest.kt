package com.ekaur.android.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop window against the bitmap it reads from.
 *
 * Every case here protects one thing: `Bitmap.createBitmap` throws if the rect
 * leaves the bitmap by a single pixel, and on this phone a throw is an error
 * message with no stack trace behind it.
 */
class CropTransformTest {

    @Test
    fun `the smallest allowed scale exactly covers the window`() {
        // A landscape photo is limited by its height, a portrait one by width.
        val wide = CropTransform.minScale(1000, 400, 300f)
        assertEquals(300f / 400f, wide, 0.0001f)

        val tall = CropTransform.minScale(400, 1000, 300f)
        assertEquals(300f / 400f, tall, 0.0001f)
    }

    @Test
    fun `zooming out past the window is refused`() {
        val floor = CropTransform.minScale(1000, 800, 500f)

        assertEquals(floor, CropTransform.clampScale(0.001f, 1000, 800, 500f), 0.0001f)
        assertEquals(
            floor * CropTransform.MAX_ZOOM,
            CropTransform.clampScale(9999f, 1000, 800, 500f),
            0.0001f,
        )
    }

    @Test
    fun `the photo can never be dragged off the window`() {
        val scale = 1f
        // 1000px drawn over a 400px window: it may slide 600px, no further.
        assertEquals(0f, CropTransform.clampOffset(50f, 1000, scale, 400f), 0.0001f)
        assertEquals(-600f, CropTransform.clampOffset(-5000f, 1000, scale, 400f), 0.0001f)
        assertEquals(-123f, CropTransform.clampOffset(-123f, 1000, scale, 400f), 0.0001f)
    }

    @Test
    fun `an untouched photo crops to the same square as before`() {
        // The default has to match what the app did with no crop screen at all,
        // so opening it and pressing straight through changes nothing.
        val (iw, ih) = 1000 to 400
        val window = 300f
        val scale = CropTransform.minScale(iw, ih, window)
        val crop = CropTransform.crop(
            iw, ih, window, scale,
            CropTransform.centreOffset(iw, scale, window),
            CropTransform.centreOffset(ih, scale, window),
        )

        assertEquals(Avatar.centreCrop(iw, ih), crop)
    }

    @Test
    fun `zooming in takes a smaller square, still inside the photo`() {
        val scale = CropTransform.minScale(800, 800, 400f) * 2f
        val crop = CropTransform.crop(800, 800, 400f, scale, -200f, -200f)

        assertEquals(400, crop.side)
        assertTrue(crop.x in 0..400)
        assertTrue(crop.y in 0..400)
    }

    @Test
    fun `a crop can never read outside the bitmap`() {
        // The invariant that matters. Sweep sizes, zoom levels and offsets far
        // beyond what a gesture could legally produce.
        val sizes = listOf(64 to 64, 1000 to 400, 400 to 1000, 1080 to 1920, 4032 to 3024)
        val window = 360f

        for ((iw, ih) in sizes) {
            val floor = CropTransform.minScale(iw, ih, window)
            for (step in 0..12) {
                val scale = CropTransform.clampScale(floor * (1f + step * 0.5f), iw, ih, window)
                for (raw in listOf(-1e6f, -777.7f, -0.5f, 0f, 12f, 1e6f)) {
                    val ox = CropTransform.clampOffset(raw, iw, scale, window)
                    val oy = CropTransform.clampOffset(-raw, ih, scale, window)
                    val crop = CropTransform.crop(iw, ih, window, scale, ox, oy)

                    assertTrue("$iw x $ih side ${crop.side}", crop.side >= 1)
                    assertTrue("$iw x $ih x", crop.x >= 0 && crop.x + crop.side <= iw)
                    assertTrue("$iw x $ih y", crop.y >= 0 && crop.y + crop.side <= ih)
                }
            }
        }
    }

    @Test
    fun `nonsense input produces a usable crop rather than a crash`() {
        assertEquals(Avatar.Crop(0, 0, 0), CropTransform.crop(0, 0, 100f, 1f, 0f, 0f))
        assertEquals(Avatar.centreCrop(50, 80), CropTransform.crop(50, 80, 0f, 1f, 0f, 0f))
        assertEquals(Avatar.centreCrop(50, 80), CropTransform.crop(50, 80, 100f, 0f, 0f, 0f))
        assertEquals(1f, CropTransform.minScale(0, 0, 100f), 0.0001f)
    }

    @Test
    fun `a window wider than the photo centres instead of leaving a gap`() {
        // Unreachable through clamped scales, but it must not return an offset
        // that puts an empty stripe in somebody's profile picture.
        assertEquals(50f, CropTransform.clampOffset(-999f, 100, 1f, 200f), 0.0001f)
    }
}
