package com.ekaur.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ekaur.android.R
import com.ekaur.android.service.ServiceControl

/**
 * A home-screen toggle that shows whether Ek Aur is on, and switches it.
 *
 * The system accessibility button cannot be restyled, so this is the custom
 * control instead: acid and loud while the service runs, blacked-out when it
 * does not, and a tap flips it. Turning off is instant (`disableSelf`); turning
 * on drops the user on the accessibility switch, because Android never lets an
 * app enable its own accessibility -- so that half stays one tap, not zero.
 *
 * The widget is drawn by the app, not the accessibility service, on purpose:
 * the service does not exist while it is off, so nothing it draws could show
 * the off state. This can.
 */
class EkAurWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (ServiceControl.isAccessibilityServiceEnabled(context)) {
                // On: switch it off at once. onUnbind also refreshes, so the
                // chip flips even if this read is a moment stale.
                ServiceControl.pauseForPayment()
            } else {
                // Off: Android will not let the app enable it, so hand the user
                // to the switch. onServiceConnected refreshes once it is on.
                ServiceControl.openAccessibilityServiceDetails(context)
            }
            refresh(context)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.ekaur.android.widget.TOGGLE"

        private const val INK = 0xFF0A0A0A.toInt()
        private const val ACID = 0xFFC8FF00.toInt()
        private const val CHALK = 0xFFF2F2F2.toInt()
        private const val SMOKE = 0xFF8A8A8A.toInt()

        /** Redraws every placed widget. Called from the service on/off, and on tap. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, EkAurWidgetProvider::class.java)
            )
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val on = ServiceControl.isAccessibilityServiceEnabled(context)
            val views = RemoteViews(context.packageName, R.layout.widget_toggle)

            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (on) R.drawable.widget_bg_on else R.drawable.widget_bg_off,
            )
            views.setTextViewText(R.id.widget_state, if (on) "ON" else "OFF")
            // On the acid fill everything is ink; off, the wordmark greys out
            // and the tally keeps the accent so the chip is still recognisably
            // Ek Aur.
            views.setInt(R.id.widget_icon, "setColorFilter", if (on) INK else ACID)
            views.setTextColor(R.id.widget_label, if (on) INK else SMOKE)
            views.setTextColor(R.id.widget_state, if (on) INK else CHALK)

            val toggle = Intent(context, EkAurWidgetProvider::class.java)
                .setAction(ACTION_TOGGLE)
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                toggle,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            manager.updateAppWidget(id, views)
        }
    }
}
