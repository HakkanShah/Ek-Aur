package com.ekaur.android.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import com.ekaur.android.sync.Avatar
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Getting a photo off the phone and into a profile picture.
 *
 * Two steps, deliberately separate. [decode] produces a bitmap the user can
 * look at and frame; [encode] takes the square they chose and makes the file
 * that is uploaded. Nothing is sent until they have seen it.
 *
 * Decoding goes through `ImageDecoder` rather than `BitmapFactory`, which is
 * the fix for photos that would not open at all. `BitmapFactory` knows JPEG,
 * PNG, WebP, GIF and BMP and nothing else -- so a HEIC, the default camera
 * format on a lot of recent phones, simply came back null. `ImageDecoder`
 * hands the work to the platform's own decoders, so it reads whatever the
 * phone can display, and it applies the EXIF rotation as it goes, which
 * `BitmapFactory` never did: a photo taken in portrait used to be saved
 * sideways.
 */
object AvatarPhoto {

    /**
     * How large a bitmap the crop screen works with.
     *
     * Big enough that zooming in still leaves real pixels behind the 256px
     * result, small enough that a cheap phone is not asked for 50MB. A full
     * camera photo is around 4000x3000 and about 48MB decoded, which is enough
     * to kill the app, so the decoder is told to shrink before it allocates
     * anything.
     */
    const val WORKING_SIZE = 1024

    /** Comfortably under the bucket's 256KB ceiling at 256px square. */
    private const val QUALITY = 80

    /** Why a photo could not be opened, in the terms the screen has to explain. */
    enum class Failure { CannotOpen, NotAnImage, TooBig }

    class PhotoException(val failure: Failure, cause: Throwable? = null) :
        Exception(failure.name, cause)

    /**
     * Reads [uri] into a bitmap small enough to work with.
     *
     * Throws [PhotoException] rather than returning null, so the caller can say
     * which of the three things went wrong instead of offering one message for
     * all of them.
     */
    fun decode(context: Context, uri: Uri): Bitmap = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeModern(context, uri)
        } else {
            decodeLegacy(context, uri)
        }
    } catch (e: OutOfMemoryError) {
        throw PhotoException(Failure.TooBig, e)
    }

    /**
     * The chosen square, at [Avatar.SIZE], as the WebP that gets uploaded.
     *
     * [crop] comes from `CropTransform`, which guarantees it is inside the
     * bitmap -- `Bitmap.createBitmap` throws on a rect that is not, and this
     * runs where a throw has nowhere to be seen.
     */
    fun encode(source: Bitmap, crop: Avatar.Crop): ByteArray {
        require(crop.side > 0) { "empty crop" }

        val square = Bitmap.createBitmap(source, crop.x, crop.y, crop.side, crop.side)
        val scaled = square.scale(Avatar.SIZE)
        return try {
            ByteArrayOutputStream().use { out ->
                scaled.compress(webpFormat(), QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (scaled !== square) scaled.recycle()
            if (square !== source) square.recycle()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(context: Context, uri: Uri): Bitmap {
        val source = try {
            ImageDecoder.createSource(context.contentResolver, uri)
        } catch (e: Exception) {
            throw PhotoException(Failure.CannotOpen, e)
        }

        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // The default allocator can hand back a hardware bitmap, whose
                // pixels cannot be read -- so cropping or compressing it throws.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                // setTargetSize rather than a sample size: sampling can
                // only halve, so a 4032x3024 photo would land at 2016x1512 and
                // about 12MB. This pins it to a known size instead.
                val (w, h) = Avatar.workingSize(
                    info.size.width, info.size.height, WORKING_SIZE,
                )
                decoder.setTargetSize(w, h)
            }
        } catch (e: IOException) {
            throw PhotoException(Failure.CannotOpen, e)
        } catch (e: ImageDecoder.DecodeException) {
            throw PhotoException(Failure.NotAnImage, e)
        } catch (e: Exception) {
            throw PhotoException(Failure.NotAnImage, e)
        }
    }

    /**
     * API 26 and 27, which predate `ImageDecoder`.
     *
     * Same two-pass shape as before: dimensions first, then a decode at a
     * shrink factor, so the full-size bitmap never exists. The rotation
     * `ImageDecoder` would have applied is read off the EXIF and applied here.
     */
    private fun decodeLegacy(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openOrThrow(context, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PhotoException(Failure.NotAnImage)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = Avatar.sampleSizeFor(bounds.outWidth, bounds.outHeight, WORKING_SIZE)
        }
        val decoded = openOrThrow(context, uri).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw PhotoException(Failure.NotAnImage)

        return orient(context, uri, decoded)
    }

    private fun openOrThrow(context: Context, uri: Uri) =
        try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            throw PhotoException(Failure.CannotOpen, e)
        } ?: throw PhotoException(Failure.CannotOpen)

    @Suppress("DEPRECATION")
    private fun orient(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            openOrThrow(context, uri).use { stream ->
                when (android.media.ExifInterface(stream).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        } catch (e: Exception) {
            // A missing or unreadable EXIF block is not a reason to refuse the
            // photo; it only means it is shown the way it was stored.
            0f
        }

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
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
