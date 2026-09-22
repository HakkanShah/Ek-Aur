package com.ekaur.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to the system installer.
 *
 * A sideloaded app can never install itself silently -- Android always shows its
 * own Install confirmation for a non-privileged app -- so the most this can do is
 * make it one tap, and route the user to grant "install unknown apps" the first
 * time if they haven't.
 *
 * The install goes through the [PackageInstaller] session API: the APK's bytes are
 * streamed straight into a system-owned session and committed, so the OS reads the
 * package itself rather than being handed a `content://` file to parse. That older
 * path (ACTION_VIEW + FileProvider) is unreliable on some OEM builds -- it was
 * giving "there was a problem while parsing the package" for a file that installs
 * fine by hand -- so it is kept only as a fallback if a session can't be opened.
 */
object UpdateInstaller {

    const val ACTION_INSTALL_STATUS = "com.ekaur.android.INSTALL_STATUS"

    /** Whether the OS will let this app start an install without a detour first. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Sends the user to grant this app permission to install APKs. */
    fun promptUnknownSources(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Streams [apk] into a system install session and commits it. */
    fun install(context: Context, apk: File) {
        val app = context.applicationContext
        val ok = runCatching {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("ekaur-update", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(statusSender(app, sessionId))
            }
            true
        }.getOrDefault(false)

        // Only if a session could not even be opened -- keep a working path on any
        // device where the session API itself misbehaves.
        if (!ok) installViaView(app, apk)
    }

    /** A PendingIntent the session posts install status (and the confirm UI) to. */
    private fun statusSender(context: Context, sessionId: Int): IntentSender {
        val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }

    /** The old path: view the APK through the FileProvider. Fallback only. */
    private fun installViaView(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
