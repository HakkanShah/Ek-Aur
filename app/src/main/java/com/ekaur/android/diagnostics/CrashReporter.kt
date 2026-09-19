package com.ekaur.android.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches uncaught exceptions to a file so a crash is diagnosable.
 *
 * Without ADB there is no logcat, so an unhandled exception would otherwise be
 * invisible -- it would just look like "the app stopped counting". On the next
 * launch the app surfaces whatever landed here with a share button.
 */
class CrashReporter(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            // Always hand back to the platform handler so the process still dies
            // normally rather than hanging in a half-dead state.
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file.writeText(
            buildString {
                append("EK AUR crash\n")
                append("when=").append(stamp).append('\n')
                append("thread=").append(thread.name).append('\n')
                append("android=").append(android.os.Build.VERSION.SDK_INT).append('\n')
                append("device=").append(android.os.Build.MANUFACTURER)
                append(' ').append(android.os.Build.MODEL).append("\n\n")
                append(stack)
            }
        )
    }

    fun pendingReport(): String? = file.takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private companion object {
        const val FILE_NAME = "last_crash.txt"
    }
}
