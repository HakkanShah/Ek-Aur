package com.ekaur.android.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

/**
 * Whether the accessibility service is switched on, and how to get the user to
 * the screens where they can switch it on.
 *
 * There is no callback for any of this, so screens re-read it on resume.
 */
object ServiceControl {

    /**
     * Whether the service is enabled in system settings.
     *
     * Asks [AccessibilityManager] first, which is authoritative. The settings
     * string is only a fallback, and is compared leniently: Android may store
     * the component in short form (`pkg/.Class`), which does not compare equal
     * to the fully-qualified name and would otherwise read as "off" while the
     * service was actually running.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, EkAurAccessibilityService::class.java)

        val manager = context.getSystemService(AccessibilityManager::class.java)
        if (manager != null) {
            val running = runCatching {
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            }.getOrNull()
            if (running != null) {
                val found = running.any { info ->
                    info.resolveInfo?.serviceInfo?.let {
                        it.packageName == expected.packageName && it.name == expected.className
                    } == true
                }
                if (found) return true
            }
        }

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry) ?: continue
            if (component.packageName != expected.packageName) continue
            // Tolerate both `pkg/.Class` and `pkg/full.pkg.Class`.
            val name = component.className
            if (name == expected.className || expected.className.endsWith(name)) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Switches the counting off for a payment, and reports whether it worked.
     *
     * A UPI or banking app is required to warn about any accessibility service,
     * and its warning reads the system's enabled list. Switching the service
     * off removes it from that list at once, so the warning has nothing to
     * report. It is turned back on the same way it was first enabled, in
     * Settings -- an app may never grant itself accessibility.
     *
     * Returns false only if the service was not running, in which case there is
     * nothing to pause and the caller can say so.
     */
    fun pauseForPayment(): Boolean = EkAurAccessibilityService.pauseFromUi()

    /** Whether the floating counter is allowed to draw over other apps. */
    fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Whether the app is exempt from battery optimisation.
     *
     * Realme, Oppo, Xiaomi and Vivo kill background services hard, and a killed
     * accessibility service just stops counting with no visible sign. The
     * exemption is the difference between this working for a week and working
     * for an afternoon.
     */
    fun isIgnoringBatteryOptimisations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    fun openBatterySettings(context: Context) {
        // The per-app request dialog is not available to every build, so fall
        // back to the full list rather than doing nothing.
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(fallback) }
    }

    /**
     * Opens this app's own App info page.
     *
     * Android 13+ blocks accessibility for sideloaded apps behind a "Restricted
     * setting" dialog that offers no way forward; the unblock lives in the
     * overflow menu of this screen. Without a direct link most people give up
     * at the dialog.
     */
    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Hands text to the share sheet -- the only way anything leaves this phone. */
    fun shareText(context: android.content.Context, text: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(android.content.Intent.createChooser(intent, "share"))
        }
    }
}
