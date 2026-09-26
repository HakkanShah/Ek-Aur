package com.ekaur.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * Receives the [PackageInstaller] session's status callbacks.
 *
 * A non-privileged app still needs the user to confirm the install, so the OS
 * reports back that user action is pending and hands over the confirm Intent to
 * launch. Success and failure are surfaced as a short toast, since there is no
 * other channel on a phone with no logcat.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // The new build takes over from here.
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // The user backed out of the confirm dialog; say nothing, but
                // offer the Install button again.
                installEnded(context)
            }

            else -> {
                installEnded(context)
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(
                    context,
                    "Update couldn't install" + (msg?.let { ": $it" } ?: "."),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun installEnded(context: Context) {
        (context.applicationContext as? com.ekaur.android.EkAurApp)?.container?.updateManager?.onInstallEnded()
    }

    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
