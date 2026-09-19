package com.ekaur.android.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Whether the accessibility service is switched on, and how to get the user to
 * the screen where they can switch it on.
 *
 * The enabled state has to be read out of a system setting -- there is no
 * callback for it -- so screens re-read this on resume.
 */
object ServiceControl {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, EkAurAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        for (entry in splitter) {
            if (ComponentName.unflattenFromString(entry) == expected) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
