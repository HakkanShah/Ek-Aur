package com.ekaur.android.service

/**
 * Whether the accessibility service should switch itself off.
 *
 * The user asked for Ek Aur to turn off once they leave Instagram, so a payment
 * app -- which blocks while any accessibility service is on -- is clean by
 * default and never needs a manual pause. Turning it back on is always their
 * one tap, because Android never lets an app grant itself accessibility.
 *
 * The decision is kept here, in plain Kotlin away from the service, because
 * getting it wrong is expensive in both directions: fire while the user is
 * still in Instagram and counting dies mid-scroll; never fire and the point is
 * lost. It is unit tested on the JVM like the rest of the app's real logic.
 */
object AutoOffPolicy {

    /**
     * @param enabled the user's auto-off setting.
     * @param usageGranted whether usage access is granted -- without it the
     *   foreground cannot be read reliably, and a wrong guess would kill the
     *   service mid-scroll, so auto-off stays off entirely.
     * @param foregroundIsInstagram whether Instagram is in front right now.
     * @param msSinceInstagramForeground how long since Instagram was last in
     *   front. A grace, so a glance at a notification or a quick reply does not
     *   turn the service off.
     * @param graceMs how long the user must be away from Instagram first.
     */
    fun shouldDisable(
        enabled: Boolean,
        usageGranted: Boolean,
        foregroundIsInstagram: Boolean,
        msSinceInstagramForeground: Long,
        graceMs: Long,
    ): Boolean {
        if (!enabled || !usageGranted) return false
        if (foregroundIsInstagram) return false
        return msSinceInstagramForeground >= graceMs
    }

    /**
     * Whether the floating counter should come down.
     *
     * Sooner than [shouldDisable], and independent of it: the pill is only ever
     * useful over Instagram, so it goes the moment the user is elsewhere, well
     * before the service itself switches off. Never touches counting.
     */
    fun shouldHidePill(
        foregroundIsInstagram: Boolean,
        msSinceInstagramForeground: Long,
        pillGraceMs: Long,
    ): Boolean {
        if (foregroundIsInstagram) return false
        return msSinceInstagramForeground >= pillGraceMs
    }
}
