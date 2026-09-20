package com.ekaur.android.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendCodeTest {

    @Test
    fun `a code pasted out of a chat still works`() {
        // How one actually arrives: lower-cased by a keyboard, with the display
        // spacing or a dash still attached, and a newline from the paste.
        assertEquals("MJYW4M", FriendCode.normalise("  mjyw-4m \n"))
        assertEquals("MJYW4M", FriendCode.normalise("MJY W4M"))
        assertEquals("MJYW4M", FriendCode.normalise("mjyw4m"))
    }

    @Test
    fun `an obviously wrong code is rejected before any network call`() {
        assertFalse(FriendCode.isPlausible(null))
        assertFalse(FriendCode.isPlausible(""))
        assertFalse("too short", FriendCode.isPlausible("ABC"))
        assertFalse("too long", FriendCode.isPlausible("ABCDEFG"))
    }

    @Test
    fun `characters the alphabet deliberately avoids are rejected`() {
        // 0/O and 1/I are not in the alphabet precisely because these codes get
        // read aloud and typed by hand, so a code containing them is a typo.
        assertFalse(FriendCode.isPlausible("MJYW40"))
        assertFalse(FriendCode.isPlausible("MJYW4O"))
        assertFalse(FriendCode.isPlausible("MJYW4I"))
        assertFalse(FriendCode.isPlausible("MJYW41"))
    }

    @Test
    fun `a real code is accepted`() {
        assertTrue(FriendCode.isPlausible("MJYW4M"))
        assertTrue(FriendCode.isPlausible("UCQMA6"))
        assertTrue("normalised first", FriendCode.isPlausible(" ucq-ma6 "))
    }

    @Test
    fun `codes are shown split, so they can be read back over a call`() {
        assertEquals("UCQ MA6", FriendCode.forDisplay("UCQMA6"))
        assertEquals("UCQ MA6", FriendCode.forDisplay("ucq ma6"))
    }
}
