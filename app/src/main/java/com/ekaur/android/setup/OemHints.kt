package com.ekaur.android.setup

/**
 * Where things live on this phone's brand of Android.
 *
 * The "Restricted setting" unblock is the same everywhere -- App info, the
 * overflow menu, "Allow restricted settings" -- but every manufacturer names
 * the screens differently and buries App info a different number of taps
 * deep. Telling someone "go to App info" on a realme is not enough; telling
 * them "Settings, Apps, App management, Ek Aur" is. Nothing here is Android
 * glue: the caller hands in [android.os.Build] strings and gets copy back.
 */
data class OemHint(
    /** A short brand label for the screen, e.g. "Samsung". Null on stock. */
    val brand: String?,
    /** The section of the accessibility list where sideloaded services sit. */
    val listSection: String,
    /** The taps from Settings to this app's App info page. */
    val appInfoPath: String,
    /** True on the brands the "there is no ⋮" reports come from. */
    val hedged: Boolean,
) {
    /** The whole unblock instruction, ending with the rule everyone misses. */
    val menuLine: String
        get() = buildString {
            append(appInfoPath)
            append(" → ⋮ (top right")
            if (hedged) append(", usually")
            append(") → Allow restricted settings.")
        }
}

object OemHints {

    const val MENU_RULE = "The menu only appears after you've tapped the switch once."

    private val STOCK = OemHint(
        brand = null,
        listSection = "Downloaded apps",
        appInfoPath = "Settings → Apps → Ek Aur",
        hedged = false,
    )

    /** Picks the hint for this device from its manufacturer and brand strings. */
    fun forDevice(manufacturer: String?, brand: String?): OemHint {
        val m = manufacturer.orEmpty().trim().lowercase()
        val b = brand.orEmpty().trim().lowercase()
        fun any(vararg keys: String) = keys.any { m.startsWith(it) || b.startsWith(it) }

        return when {
            any("samsung") -> OemHint(
                brand = "Samsung",
                listSection = "Installed apps",
                appInfoPath = "Settings → Apps → Ek Aur",
                hedged = false,
            )
            // Redmi and POCO phones report manufacturer "Xiaomi" and only the
            // brand string tells them apart; all three run the same settings.
            any("xiaomi", "redmi", "poco") -> OemHint(
                brand = "Xiaomi",
                listSection = "Downloaded apps",
                appInfoPath = "Settings → Apps → Manage apps → Ek Aur",
                hedged = false,
            )
            any("realme", "oppo", "oneplus") -> OemHint(
                brand = "realme / OPPO / OnePlus",
                listSection = "Installed services",
                appInfoPath = "Settings → Apps → App management → Ek Aur",
                hedged = true,
            )
            // iQOO reports manufacturer "vivo".
            any("vivo", "iqoo") -> OemHint(
                brand = "vivo / iQOO",
                listSection = "Downloaded apps",
                appInfoPath = "Settings → Apps → App management → Ek Aur",
                hedged = true,
            )
            else -> STOCK
        }
    }
}
