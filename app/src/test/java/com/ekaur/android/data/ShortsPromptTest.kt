package com.ekaur.android.data

import com.ekaur.android.data.prefs.SettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Build 50 marked apps as chosen while YouTube was invisible to it, so the
 * Shorts question has its own flag that those users haven't set yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShortsPromptTest {

    private val settings = SettingsStore(RuntimeEnvironment.getApplication())

    @Test
    fun `a build 50 user who already chose apps is still asked about Shorts`() {
        settings.appsChosen = true
        assertFalse(settings.shortsAsked)
    }

    @Test
    fun `answering the question is remembered`() {
        settings.shortsAsked = true
        assertTrue(SettingsStore(RuntimeEnvironment.getApplication()).shortsAsked)
    }
}
