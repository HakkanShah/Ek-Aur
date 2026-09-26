package com.ekaur.android.setup

import com.ekaur.android.setup.RestrictedSetting.MODE_ALLOWED
import com.ekaur.android.setup.RestrictedSetting.MODE_DEFAULT
import com.ekaur.android.setup.RestrictedSetting.MODE_ERRORED
import com.ekaur.android.setup.RestrictedSetting.MODE_IGNORED
import com.ekaur.android.setup.RestrictedSetting.SOURCE_DOWNLOADED_FILE
import com.ekaur.android.setup.RestrictedSetting.SOURCE_LOCAL_FILE
import com.ekaur.android.setup.RestrictedSetting.SOURCE_STORE
import com.ekaur.android.setup.RestrictedSetting.SOURCE_UNSPECIFIED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedSettingTest {

    @Test
    fun `below android 13 the gate does not exist`() {
        assertEquals(Verdict.NotApplicable, RestrictedSetting.assess(32, SOURCE_DOWNLOADED_FILE, MODE_ERRORED))
        assertEquals(Verdict.NotApplicable, RestrictedSetting.assess(26, null, null))
        assertFalse(RestrictedSetting.applies(Verdict.NotApplicable))
    }

    @Test
    fun `an errored op means the dialog has been shown`() {
        assertEquals(Verdict.Restricted, RestrictedSetting.assess(33, SOURCE_DOWNLOADED_FILE, MODE_ERRORED))
        assertEquals(Verdict.Restricted, RestrictedSetting.assess(35, null, MODE_ERRORED))
    }

    @Test
    fun `an allowed op means the user has cleared it`() {
        assertEquals(Verdict.Cleared, RestrictedSetting.assess(33, SOURCE_DOWNLOADED_FILE, MODE_ALLOWED))
        assertEquals(Verdict.Cleared, RestrictedSetting.assess(34, SOURCE_LOCAL_FILE, MODE_ALLOWED))
    }

    @Test
    fun `a default op on android 15 is decided by the package source`() {
        assertEquals(Verdict.LikelyRestricted, RestrictedSetting.assess(35, SOURCE_DOWNLOADED_FILE, MODE_DEFAULT))
        assertEquals(Verdict.LikelyRestricted, RestrictedSetting.assess(35, SOURCE_LOCAL_FILE, MODE_DEFAULT))
        assertEquals(Verdict.Unknown, RestrictedSetting.assess(35, SOURCE_STORE, MODE_DEFAULT))
        assertEquals(Verdict.Unknown, RestrictedSetting.assess(35, SOURCE_UNSPECIFIED, MODE_DEFAULT))
    }

    @Test
    fun `an unreadable op falls back to the package source`() {
        assertEquals(Verdict.LikelyRestricted, RestrictedSetting.assess(33, SOURCE_DOWNLOADED_FILE, null))
        assertEquals(Verdict.Unknown, RestrictedSetting.assess(33, null, null))
        assertEquals(Verdict.Unknown, RestrictedSetting.assess(33, SOURCE_UNSPECIFIED, MODE_IGNORED))
    }

    @Test
    fun `everything on android 13 and up applies`() {
        for (v in Verdict.entries.filter { it != Verdict.NotApplicable }) {
            assertTrue(v.name, RestrictedSetting.applies(v))
        }
    }

    @Test
    fun `only a known or likely gate is asked for up front`() {
        assertEquals(true, RestrictedSetting.needsUnblockStep(Verdict.Restricted))
        assertEquals(true, RestrictedSetting.needsUnblockStep(Verdict.LikelyRestricted))
        assertEquals(false, RestrictedSetting.needsUnblockStep(Verdict.Cleared))
        assertEquals(false, RestrictedSetting.needsUnblockStep(Verdict.Unknown))
        assertEquals(false, RestrictedSetting.needsUnblockStep(Verdict.NotApplicable))
    }
}
