package com.ekaur.android.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Gets a finished card out of the app.
 *
 * One generic image share rather than per-app integrations: the system sheet
 * already lists WhatsApp (chat and status), Instagram (story and post), X and
 * everything else installed, and every one of them accepts an image/png. A
 * bespoke intent per network would be four things to keep working instead of
 * one.
 */
object CardSharing {

    private const val DIRECTORY = "cards"

    /** Writes [bitmap] where the share sheet can reach it, and returns the intent. */
    fun intentFor(context: Context, bitmap: Bitmap, caption: String): Intent {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        // One name, overwritten each time: these are disposable, and a folder
        // that grows for ever inside the app's cache is a slow leak.
        val file = File(dir, "ek-aur-card.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.cards",
            file,
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // Instagram ignores this; WhatsApp uses it as the caption and X as
            // the tweet body. Harmless where it is not wanted.
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** The words that ride along where the app receiving it allows any. */
    fun captionFor(stats: CardStats): String =
        "${stats.reelsToday} reels. ${CardCopy.challengeFor(stats.reelsToday)}\n${CardCopy.URL}"
}
