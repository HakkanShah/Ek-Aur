package com.ekaur.android.service

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process

/**
 * The phone-maker switches that decide whether Android is even allowed to
 * start the counting service.
 *
 * A bug report settled why "switched on, but not running" kept coming back
 * on Xiaomi: the accessibility switch was on, the service had received zero
 * events, and battery saver was still restricting the app. Android removes a
 * service it refuses outright from the enabled list; one left on but never
 * started is the phone's own launch control (MIUI/HyperOS "Autostart") saying
 * no. Toggling the switch again cannot fix that, which is why people went in
 * circles. So setup now clears these first.
 */
object KeepAlive {

    /** Which launch-control screen this phone has, if any. */
    enum class Kind { Xiaomi, Oppo, Vivo, None }

    fun kind(manufacturer: String? = Build.MANUFACTURER, brand: String? = Build.BRAND): Kind {
        val m = manufacturer.orEmpty().lowercase()
        val b = brand.orEmpty().lowercase()
        fun any(vararg keys: String) = keys.any { m.startsWith(it) || b.startsWith(it) }
        return when {
            any("xiaomi", "redmi", "poco") -> Kind.Xiaomi
            any("oppo", "realme", "oneplus") -> Kind.Oppo
            any("vivo", "iqoo") -> Kind.Vivo
            else -> Kind.None
        }
    }

    /**
     * Whether Autostart is on for this app: true or false where the phone
     * says, null where it can't be read (every non-Xiaomi phone, and Xiaomi
     * builds that hide it). A null never blocks anything.
     */
    fun autostart(context: Context): Boolean? {
        if (kind() != Kind.Xiaomi) return null
        // MIUI's own helper, present on MIUI 12+ and HyperOS.
        runCatching {
            val utils = Class.forName("android.miui.AppOpsUtils")
            val method = utils.getDeclaredMethod("getApplicationAutoStart", Context::class.java, String::class.java)
            val mode = method.invoke(null, context, context.packageName) as Int
            return mode == AppOpsManager.MODE_ALLOWED
        }
        // Older builds: the app op behind it, 10008.
        runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val mode = method.invoke(ops, XIAOMI_OP_AUTO_START, Process.myUid(), context.packageName) as Int
            return mode == AppOpsManager.MODE_ALLOWED
        }
        return null
    }

    /** True where this phone has a launch-control screen worth sending people to. */
    fun hasAutostartScreen(): Boolean = kind() != Kind.None

    /**
     * Opens the phone's Autostart (or "auto launch") list. Each maker has
     * moved it between versions, so the known screens are tried in turn and
     * App info is the last resort -- the toggle lives there on newer builds.
     */
    fun openAutostart(context: Context): Boolean {
        val candidates = when (kind()) {
            Kind.Xiaomi -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            Kind.Oppo -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            Kind.Vivo -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            Kind.None -> emptyList()
        }
        for (component in candidates) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        ServiceControl.openAppInfo(context)
        return false
    }

    /** MIUI's app op number for Autostart. */
    private const val XIAOMI_OP_AUTO_START = 10008
}
