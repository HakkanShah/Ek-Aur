package com.ekaur.android.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ekaur.android.sync.Avatar
import com.ekaur.android.sync.CropTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The encode half of the pipeline, actually run rather than assumed.
 *
 * `Bitmap.createBitmap` throws outright on a rect that leaves the source, and
 * the bucket refuses anything over 256KB, so both are checked against real
 * pixels instead of being reasoned about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarPhotoTest {

    /** A photo with a distinct patch, so a crop can be shown to have moved. */
    private fun photo(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawRect(
            0f, 0f, width / 4f, height / 4f,
            Paint().apply { color = Color.RED },
        )
        return bitmap
    }

    @Test
    fun `whatever is framed comes out as a square at the display size`() {
        val source = photo(1000, 600)
        val window = 400f
        val scale = CropTransform.minScale(source.width, source.height, window)
        val crop = CropTransform.crop(
            source.width, source.height, window, scale,
            CropTransform.centreOffset(source.width, scale, window),
            CropTransform.centreOffset(source.height, scale, window),
        )

        val bytes = AvatarPhoto.encode(source, crop)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertEquals(Avatar.SIZE, decoded.width)
        assertEquals(Avatar.SIZE, decoded.height)
    }

    @Test
    fun `the upload stays under the bucket's ceiling`() {
        // The server refuses anything over 256KB, so a photo that encodes badly
        // has to fail here rather than on the phone.
        val bytes = AvatarPhoto.encode(
            photo(2000, 2000),
            Avatar.Crop(0, 0, 2000),
        )

        assertTrue("${bytes.size} bytes", bytes.size < 256 * 1024)
        assertTrue("suspiciously empty", bytes.size > 64)
    }

    @Test
    fun `the frame the user chose is the frame that is uploaded`() {
        // Top-left holds the red patch; the centre does not. If the crop were
        // ignored, these two would come out the same. Compared by channel
        // rather than exact value, because WebP here is lossy.
        val source = photo(800, 800)

        val corner = decode(AvatarPhoto.encode(source, Avatar.Crop(0, 0, 200)))
        val middle = decode(AvatarPhoto.encode(source, Avatar.Crop(400, 400, 200)))

        assertTrue(
            "corner should be red, was ${Integer.toHexString(corner)}",
            Color.red(corner) > 200 && Color.green(corner) < 60,
        )
        assertTrue(
            "middle should be black, was ${Integer.toHexString(middle)}",
            Color.red(middle) < 60,
        )
    }

    private fun decode(bytes: ByteArray): Int =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).getPixel(128, 128)

    @Test
    fun `every crop a gesture can produce encodes without throwing`() {
        // The whole point of clamping in CropTransform. One pixel outside the
        // source and createBitmap throws, on a screen with no logcat behind it.
        val source = photo(900, 1200)
        val window = 360f
        val floor = CropTransform.minScale(source.width, source.height, window)

        for (step in 0..10) {
            val scale = CropTransform.clampScale(floor * (1f + step * 0.5f), 900, 1200, window)
            for (raw in listOf(-1e6f, -300f, 0f, 1e6f)) {
                val crop = CropTransform.crop(
                    900, 1200, window, scale,
                    CropTransform.clampOffset(raw, 900, scale, window),
                    CropTransform.clampOffset(-raw, 1200, scale, window),
                )
                val bytes = AvatarPhoto.encode(source, crop)
                assertTrue("empty at $scale/$raw", bytes.isNotEmpty())
            }
        }
    }

    @Test
    fun `the source survives being encoded more than once`() {
        // A failed upload leaves the crop screen open so lagao can be pressed
        // again. Recycling the source on the way out would make that retry a
        // crash instead.
        val source = photo(600, 600)

        AvatarPhoto.encode(source, Avatar.Crop(0, 0, 600))
        val second = AvatarPhoto.encode(source, Avatar.Crop(10, 10, 400))

        assertTrue(!source.isRecycled)
        assertTrue(second.isNotEmpty())
    }
}
