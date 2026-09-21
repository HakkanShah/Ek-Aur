package com.ekaur.android.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * A Quick Settings tile that switches the counting off for a payment.
 *
 * A UPI or banking app can block a payment outright while any accessibility
 * service is on, and counting a reel needs one -- the two cannot be live at the
 * same moment. Android also never lets an app turn its own accessibility back
 * on, so there is no fully automatic answer. This is the least-friction manual
 * one: a tile in the shade, one tap to pause before paying, one tap to resume.
 *
 * Tapping it:
 *  - while counting is on, switches the service off (`disableSelf`), which
 *    removes it from the enabled list the payment app reads.
 *  - while it is off, drops the user straight onto the accessibility toggle, so
 *    turning it back on is a single switch rather than a hunt through settings.
 */
class PauseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (ServiceControl.isAccessibilityServiceEnabled(this)) {
            // On: pause it. If the service was somehow not running, this is a
            // no-op, which is the right outcome -- either way nothing is left
            // for a payment app to object to.
            ServiceControl.pauseForPayment()
            refresh()
        } else {
            // Off: send them to the switch to turn it back on. Collapsing the
            // shade with the activity is the tile-correct way to launch.
            openAccessibilitySettings()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = ServiceControl.isAccessibilityServiceEnabled(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "on" else "off"
        }
        tile.updateTile()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // startActivityAndCollapse(Intent) was deprecated at API 34 in
            // favour of a PendingIntent, which throws if the plain overload is
            // used there.
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
