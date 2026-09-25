package com.ekaur.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides when Ek Aur switches itself off.
 *
 * Every case guards one of the two costly mistakes: firing while the user is
 * still scrolling (counting dies), or refusing to fire when they have genuinely
 * left (payments stay blocked, which is the whole thing this is meant to fix).
 */
class AutoOffPolicyTest {

    private val grace = 8_000L

    @Test
    fun `switches off once away from instagram past the grace`() {
        assertTrue(
            AutoOffPolicy.shouldDisable(
                enabled = true,
                usageGranted = true,
                foregroundIsTracked = false,
                msSinceTrackedForeground = grace,
                graceMs = grace,
            )
        )
    }

    @Test
    fun `never fires while instagram is still in front`() {
        // The dangerous case: firing here would kill counting mid-scroll.
        assertFalse(
            AutoOffPolicy.shouldDisable(
                enabled = true,
                usageGranted = true,
                foregroundIsTracked = true,
                msSinceTrackedForeground = 10 * grace,
                graceMs = grace,
            )
        )
    }

    @Test
    fun `a quick glance away inside the grace does not switch it off`() {
        assertFalse(
            AutoOffPolicy.shouldDisable(
                enabled = true,
                usageGranted = true,
                foregroundIsTracked = false,
                msSinceTrackedForeground = grace - 1,
                graceMs = grace,
            )
        )
    }

    @Test
    fun `without usage access it never fires, because the foreground is a guess`() {
        // A wrong guess would turn the service off mid-scroll, so auto-off stays
        // off entirely until the foreground can be read for certain.
        assertFalse(
            AutoOffPolicy.shouldDisable(
                enabled = true,
                usageGranted = false,
                foregroundIsTracked = false,
                msSinceTrackedForeground = 10 * grace,
                graceMs = grace,
            )
        )
    }

    @Test
    fun `the setting off means it never fires`() {
        assertFalse(
            AutoOffPolicy.shouldDisable(
                enabled = false,
                usageGranted = true,
                foregroundIsTracked = false,
                msSinceTrackedForeground = 10 * grace,
                graceMs = grace,
            )
        )
    }

    @Test
    fun `the pill comes down sooner than the service switches off`() {
        val pillGrace = 2_000L
        // Away long enough for the pill, not yet for the service.
        assertTrue(
            AutoOffPolicy.shouldHidePill(
                foregroundIsTracked = false,
                msSinceTrackedForeground = pillGrace,
                pillGraceMs = pillGrace,
            )
        )
        assertFalse(
            AutoOffPolicy.shouldDisable(
                enabled = true,
                usageGranted = true,
                foregroundIsTracked = false,
                msSinceTrackedForeground = pillGrace,
                graceMs = grace,
            )
        )
    }

    @Test
    fun `the pill stays while instagram is in front`() {
        assertFalse(
            AutoOffPolicy.shouldHidePill(
                foregroundIsTracked = true,
                msSinceTrackedForeground = 10_000L,
                pillGraceMs = 2_000L,
            )
        )
    }
}
