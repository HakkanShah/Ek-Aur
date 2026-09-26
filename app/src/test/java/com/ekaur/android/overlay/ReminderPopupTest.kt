package com.ekaur.android.overlay

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The reminder popup is a window of its own over Instagram. Build 56 crashed
 * the app the moment it appeared: Compose looks for its lifecycle from the
 * window's root view, and the root was a plain frame (there to catch Back)
 * rather than the Compose view the lifecycle had been set on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderPopupTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the popup shows, composes and closes without crashing`() {
        ShadowSettings.setCanDrawOverlays(true)
        val popup = ReminderPopup(context)

        popup.show(
            count = 100,
            unit = "Reels + Shorts",
            minutesToday = 42,
            snooze = 20,
            line = "Tap tap.",
            onChoice = {},
        )
        // Attaching the window and composing happen on the next frames.
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(popup.isShowing)

        popup.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(popup.isShowing)
    }

    @Test
    fun `without the overlay permission nothing is shown and nothing breaks`() {
        ShadowSettings.setCanDrawOverlays(false)
        val popup = ReminderPopup(context)
        popup.show(100, "Reels", 0, 20, "Tap tap.") {}
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(popup.isShowing)
    }
}
