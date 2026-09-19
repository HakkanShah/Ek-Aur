package com.ekaur.android.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gets text off the device.
 *
 * With no USB on the test phone, the share sheet is the only debug cable there
 * is -- dumps travel back through chat. Everything diagnostic must be one tap
 * from here.
 */
object TextExport {

    fun share(context: Context, fileName: String, content: String, chooserTitle: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            // Some targets ignore the attachment and take the text; send both.
            putExtra(Intent.EXTRA_TEXT, content.take(MAX_INLINE_CHARS))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun copy(context: Context, label: String, content: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
    }

    private const val MAX_INLINE_CHARS = 100_000
}
