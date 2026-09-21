package com.ekaur.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to the system installer.
 *
 * A sideloaded app can never install itself silently -- Android always shows its
 * own Install confirmation for a non-privileged app -- so the most this can do is
 * make it one tap, and route the user to grant "install unknown apps" the first
 * time if they haven't. The APK is served through the app's FileProvider so the
 * installer can read it without a file:// path.
 */
object UpdateInstaller {

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

    /** Launches the system installer for [apk]. */
    fun install(context: Context, apk: File) {
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
