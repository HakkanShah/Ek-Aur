package com.ekaur.android.sync

import java.security.MessageDigest

/**
 * A stable identifier for this phone, so a reinstall can find its old account.
 *
 * `ANDROID_ID` is scoped to the app's signing key, the device and the user, and
 * it survives an uninstall -- which is exactly the property needed here. This
 * app is signed with a committed keystore, so the key never changes underneath
 * it and the value stays the same across every build.
 *
 * It is hashed before it leaves the phone. The server only ever needs to match
 * one device against another, never to know which device it is.
 */
object DeviceKey {

    /** Hashed with a fixed app salt, so the stored value is not the raw id. */
    fun of(androidId: String?): String? {
        val id = androidId?.trim().orEmpty()
        // Some emulators and modified builds report this, and a shared constant
        // would hand one person's account to everybody who has it.
        if (id.isEmpty() || id.equals("9774d56d682e549c", ignoreCase = true)) return null

        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("ek-aur:$id".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }
}
