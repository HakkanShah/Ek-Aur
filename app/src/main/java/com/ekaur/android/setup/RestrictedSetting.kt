package com.ekaur.android.setup

/**
 * Whether Android is likely to block this app's accessibility switch behind
 * the "Restricted setting" dialog -- decided from two facts the platform will
 * tell an app about itself, and nothing else.
 *
 * Android 13 introduced the gate for apps installed from a file (a browser
 * download or a file manager), and records the decision in an app-op that
 * flips to "errored" once the dialog has been shown and to "allowed" once the
 * user has gone through App info and allowed it. Android 15 leaves the op at
 * its default and derives the gate from the latest install's package source,
 * so the default mode must never be read as "not restricted".
 *
 * The verdict only ever changes the copy on the setup screens. It never hides
 * the recovery steps, because the reads behind it are best effort.
 */
enum class Verdict {
    /** Below Android 13: the gate does not exist. */
    NotApplicable,
    /** The dialog has been shown; the App info menu item now exists. */
    Restricted,
    /** The user has allowed restricted settings; only the switch is left. */
    Cleared,
    /** Installed from a file on Android 13+, so the dialog is coming. */
    LikelyRestricted,
    /** Android 13+, but nothing conclusive could be read. */
    Unknown,
}

object RestrictedSetting {

    // Mirrors android.app.AppOpsManager.MODE_* -- kept as plain ints so this
    // stays a pure function testable on the JVM.
    const val MODE_ALLOWED = 0
    const val MODE_IGNORED = 1
    const val MODE_ERRORED = 2
    const val MODE_DEFAULT = 3

    // Mirrors android.content.pm.PackageInstaller.PACKAGE_SOURCE_*.
    const val SOURCE_UNSPECIFIED = 0
    const val SOURCE_STORE = 1
    const val SOURCE_LOCAL_FILE = 2
    const val SOURCE_DOWNLOADED_FILE = 3
    const val SOURCE_OTHER = 4

    /** The hidden op the platform keeps the decision in. Exists from API 33. */
    const val OP_ACCESS_RESTRICTED_SETTINGS = "android:access_restricted_settings"

    /** The first Android version with the gate. */
    const val FIRST_SDK = 33

    fun assess(sdkInt: Int, packageSource: Int?, opMode: Int?): Verdict {
        if (sdkInt < FIRST_SDK) return Verdict.NotApplicable
        return when (opMode) {
            MODE_ERRORED -> Verdict.Restricted
            MODE_ALLOWED -> Verdict.Cleared
            else -> when (packageSource) {
                SOURCE_LOCAL_FILE, SOURCE_DOWNLOADED_FILE -> Verdict.LikelyRestricted
                else -> Verdict.Unknown
            }
        }
    }

    /** Whether the recovery steps should be offered at all. */
    fun applies(verdict: Verdict): Boolean = verdict != Verdict.NotApplicable
}
