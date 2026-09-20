package com.ekaur.android.sync

/**
 * The six characters that identify a person to their friends.
 *
 * Codes travel through WhatsApp, so by the time one is pasted back it may have
 * picked up spaces, a dash, a stray newline, or have been lower-cased by a
 * keyboard. All of that is the same code, and normalising here means a bad
 * paste never becomes a network round trip that fails for no visible reason.
 */
object FriendCode {

    const val LENGTH = 6

    /** No 0/O or 1/I: the codes get read aloud and typed by hand. */
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** Strips everything that is not a letter or digit, and upper-cases. */
    fun normalise(input: String?): String =
        input.orEmpty().filter { it.isLetterOrDigit() }.uppercase()

    /** Whether [input] could be a code at all, before anything is sent. */
    fun isPlausible(input: String?): Boolean {
        val code = normalise(input)
        return code.length == LENGTH && code.all { it in ALPHABET }
    }

    /** `"UCQMA6"` shown as `"UCQ MA6"`, which is easier to read back over a call. */
    fun forDisplay(code: String): String {
        val clean = normalise(code)
        if (clean.length != LENGTH) return clean
        return clean.substring(0, 3) + " " + clean.substring(3)
    }
}
