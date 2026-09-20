package com.ekaur.android.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IG = "com.instagram.android"
private const val WHATSAPP = "com.whatsapp"
private const val SYSTEM_UI = "com.android.systemui"

private val tracked: (String) -> Boolean = { it == IG }

/**
 * Covers the reported fault where the pill vanished on every notification --
 * which also silently cost the next reel its count, because believing the user
 * had left ended the session and cleared the counting baselines.
 */
class ForegroundPolicyTest {

    @Test
    fun `a notification from another app while instagram is in front is not leaving`() {
        assertFalse(
            ForegroundPolicy.hasLeftTrackedApp(
                eventPackage = WHATSAPP,
                actualForeground = IG,
                isTracked = tracked,
            )
        )
    }

    @Test
    fun `a system ui window is never treated as leaving`() {
        // The shade, heads-up banners and the volume panel all live here, and
        // none of them mean the user went anywhere.
        assertFalse(
            ForegroundPolicy.hasLeftTrackedApp(
                eventPackage = SYSTEM_UI,
                actualForeground = null,
                isTracked = tracked,
            )
        )
    }

    @Test
    fun `actually switching apps is leaving`() {
        assertTrue(
            ForegroundPolicy.hasLeftTrackedApp(
                eventPackage = WHATSAPP,
                actualForeground = WHATSAPP,
                isTracked = tracked,
            )
        )
    }

    @Test
    fun `an unknown foreground is treated as staying`() {
        // rootInActiveWindow can return null. Guessing "left" would destroy a
        // count; guessing "stayed" only delays a session ending.
        assertFalse(
            ForegroundPolicy.hasLeftTrackedApp(
                eventPackage = WHATSAPP,
                actualForeground = null,
                isTracked = tracked,
            )
        )
    }

    @Test
    fun `an event from the tracked app itself is never leaving`() {
        assertFalse(
            ForegroundPolicy.hasLeftTrackedApp(
                eventPackage = IG,
                actualForeground = IG,
                isTracked = tracked,
            )
        )
    }
}
