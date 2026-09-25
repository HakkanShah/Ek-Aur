package com.ekaur.android.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ekaur.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gets text off the device.
 *
 * With no USB on the test phone, the share sheet is the only debug cable there
 * is -- dumps travel back through chat. Everything diagnostic must be one tap
 * from here.
 *
 * The file goes as an attachment with one short line of text beside it. It
 * used to carry the whole dump (up to 100,000 characters) as the message text
 * as well, and WhatsApp sends shared text as a chat message and refuses one
 * that long, so the share failed there; Files ignored the text and saved the
 * attachment, which is why only that worked.
 */
object TextExport {

    fun share(context: Context, fileName: String, content: String, chooserTitle: String) {
        val file = write(context, fileName, content)
        val intent = intentFor(context, file, summaryOf(fileName, content))
        val chooser = Intent.createChooser(intent, chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    /** Writes [content] to a fresh, timestamped file in the shared exports folder. */
    fun write(context: Context, fileName: String, content: String): File {
        val dir = File(context.cacheDir, EXPORTS).apply { mkdirs() }
        // Names are timestamped now, so tidy up: nothing here is needed after
        // a day, and the cache folder shouldn't grow for ever.
        val stale = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
        val stamped = fileName.substringBeforeLast('.') + "-" + stamp() + "." + fileName.substringAfterLast('.', "txt")
        return File(dir, stamped).apply { writeText(content) }
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** The share intent: the file as an attachment, and a short line of text. */
    fun intentFor(context: Context, file: File, summary: String): Intent {
        val uri = uriFor(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, summary)
            putExtra(Intent.EXTRA_TEXT, summary)
            // Through the chooser, the read grant only reaches the chosen app
            // when the file is in the ClipData as well.
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copy(context: Context, label: String, content: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
    }

    /** "Ek Aur ekaur-events · 160 lines · v0.22.3 (53) · Xiaomi 2201117TI" */
    fun summaryOf(fileName: String, content: String): String =
        "Ek Aur ${fileName.substringBeforeLast('.')} · ${content.lines().size} lines · " +
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    const val EXPORTS = "exports"
}
