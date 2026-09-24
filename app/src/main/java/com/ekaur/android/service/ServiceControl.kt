package com.ekaur.android.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.ekaur.android.BuildConfig
import com.ekaur.android.R
import com.ekaur.android.setup.OemHint
import com.ekaur.android.setup.OemHints
import com.ekaur.android.setup.RestrictedSetting
import com.ekaur.android.setup.Verdict

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
     * Opens Ek Aur's own accessibility page, where the per-service shortcut lives.
     *
     * That page carries the toggle that puts the service on the accessibility
     * floating button -- the fastest way to switch counting off for a payment
     * and back on, since the button is reachable from any screen, the bank app
     * included. The deep link with the component only lands on the right page on
     * some builds; realme and a few others ignore the extra, so this falls back
     * to the full accessibility list, and the setup screen spells out the row to
     * tap for the rest.
     */
    fun openAccessibilityServiceDetails(context: Context) {
        val component = ComponentName(context, EkAurAccessibilityService::class.java)
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(details) }
            .onFailure { openAccessibilitySettings(context) }
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

    /**
     * Asks Android to add the pause tile straight to the Quick Settings shade.
     *
     * Saves the user hunting for it in the tile editor. Only offered from
     * Android 13, where [android.app.StatusBarManager.requestAddTileService]
     * exists; below that the tile is added by hand from the shade's edit
     * screen. The system shows its own confirmation, and ignores a repeat once
     * the tile is already there, so this is safe to tap more than once.
     */
    fun requestAddPauseTile(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(android.app.StatusBarManager::class.java) ?: return
        runCatching {
            manager.requestAddTileService(
                ComponentName(context, PauseTileService::class.java),
                context.getString(R.string.app_name),
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_tile_tally),
                { it.run() },
                {},
            )
        }
    }

    /**
     * Whether usage access is granted.
     *
     * The service reads which app is foreground from usage stats -- that is how
     * it knows to bring the pill down and switch itself off once the user
     * leaves Instagram. Checked through [android.app.AppOpsManager] rather than
     * a permission, because usage access is a special access, not a runtime
     * grant.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            ops.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }.getOrDefault(android.app.AppOpsManager.MODE_ERRORED)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

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

    /**
     * Opens the system's list of all apps, for finding this app's App info the
     * long way round.
     *
     * Some manufacturers answer the App info deep link with a cut-down page
     * that has no overflow menu at all, so "Allow restricted settings" is
     * nowhere on it -- while the same app opened from Settings → Apps shows the
     * full page. Offered as a fallback, never first.
     */
    fun openAppsList(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { openAppInfo(context) }
    }

    /** Brings Instagram to the front. False if it is not installed. */
    fun openInstagram(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(INSTAGRAM)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun isInstagramInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(INSTAGRAM) != null

    /**
     * How this build got onto the phone, as the platform recorded it.
     *
     * Android 13+ keeps the installer's declared package source, and it is
     * what decides whether the accessibility switch will be met with the
     * "Restricted setting" dialog. Null below 13 or when it cannot be read.
     */
    fun installPackageSource(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        }.getOrNull()
    }

    /**
     * The mode of the hidden app-op Android keeps the restricted-setting
     * decision in, for this app's own uid.
     *
     * The op name is not a public constant, but an app may check its own ops
     * by name (the same call [hasUsageAccess] makes). A build that does not
     * know the name throws, which reads as null and leaves the decision to the
     * package source.
     */
    fun restrictedSettingsOpMode(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val ops = context.getSystemService(android.app.AppOpsManager::class.java) ?: return null
        return runCatching {
            ops.unsafeCheckOpNoThrow(
                RestrictedSetting.OP_ACCESS_RESTRICTED_SETTINGS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }.getOrNull()
    }

    /** Whether the accessibility switch is likely to be gated, and how far along. */
    fun restrictedVerdict(context: Context): Verdict = RestrictedSetting.assess(
        sdkInt = Build.VERSION.SDK_INT,
        packageSource = installPackageSource(context),
        opMode = restrictedSettingsOpMode(context),
    )

    /** Where the settings screens live on this phone's brand of Android. */
    fun oemHint(): OemHint = OemHints.forDevice(Build.MANUFACTURER, Build.BRAND)

    /** One line naming the phone and the build, for a help email. */
    fun deviceLine(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · Ek Aur ${BuildConfig.VERSION_NAME} " +
            "(${BuildConfig.VERSION_CODE})"

    /**
     * Opens a mail app with a message to the developer already written.
     *
     * The one thing a stuck person can always do. The body carries the phone
     * model and what the app could read about the gate, so the reply can be
     * specific rather than "which phone do you have?".
     */
    fun emailDeveloper(context: Context, subject: String, body: String): Boolean {
        val uri = Uri.parse(
            "mailto:$DEVELOPER_EMAIL" +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body),
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    const val DEVELOPER_EMAIL = "hakkanparbej@gmail.com"
    private const val INSTAGRAM = "com.instagram.android"

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
