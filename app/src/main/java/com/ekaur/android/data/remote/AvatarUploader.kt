package com.ekaur.android.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.ekaur.android.sync.Avatar
import java.io.ByteArrayOutputStream

/**
 * Turns whatever the photo picker hands back into a small square WebP.
 *
 * The decoding is done in two passes on purpose. A phone photo is routinely
 * 4000x3000, which is about 48MB once decoded and quite capable of killing the
 * app; the first pass reads only the dimensions, and the second decodes at a
 * shrink factor so the full-size bitmap never exists.
 */
object AvatarUploader {

    /** Comfortably under the bucket's 256KB ceiling at 256px square. */
    private const val QUALITY = 80

    /**
     * Reads [uri], crops it square, scales it to [Avatar.SIZE] and encodes it.
     *
     * Returns null when the image cannot be read at all, which the picker can
     * produce for a file that has since been deleted or a provider that refuses
     * to open it.
     */
    fun encode(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = Avatar.sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return try {
            val crop = Avatar.centreCrop(decoded.width, decoded.height)
            if (crop.side <= 0) return null

            val square = Bitmap.createBitmap(decoded, crop.x, crop.y, crop.side, crop.side)
            val scaled = square.scale(Avatar.SIZE)

            ByteArrayOutputStream().use { out ->
                scaled.compress(webpFormat(), QUALITY, out)
                out.toByteArray()
            }.also {
                if (scaled !== square) scaled.recycle()
                if (square !== decoded) square.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun Bitmap.scale(size: Int): Bitmap =
        if (width == size && height == size) this
        else Bitmap.createScaledBitmap(this, size, size, true)

    // WEBP_LOSSY only exists from API 30; the plain WEBP constant is deprecated
    // there but is the only option on the older devices this app supports.
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
}
