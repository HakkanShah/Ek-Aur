package com.ekaur.android.setup

import com.ekaur.android.service.KeepAlive
import com.ekaur.android.ui.onboarding.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveStateTest {

    @Test
    fun `phone makers map to their launch-control screens`() {
        assertEquals(KeepAlive.Kind.Xiaomi, KeepAlive.kind("Xiaomi", "Redmi"))
        assertEquals(KeepAlive.Kind.Xiaomi, KeepAlive.kind("Xiaomi", "POCO"))
        assertEquals(KeepAlive.Kind.Oppo, KeepAlive.kind("realme", "realme"))
        assertEquals(KeepAlive.Kind.Oppo, KeepAlive.kind("OnePlus", "OnePlus"))
        assertEquals(KeepAlive.Kind.Vivo, KeepAlive.kind("vivo", "iQOO"))
        assertEquals(KeepAlive.Kind.None, KeepAlive.kind("samsung", "samsung"))
        assertEquals(KeepAlive.Kind.None, KeepAlive.kind(null, null))
    }

    @Test
    fun `switched on is not all set until the phone starts it`() {
        // The Xiaomi report: switch on, never started.
        val p = PermissionState(service = true, running = false, overlay = true)
        assertFalse(p.allGranted)
        assertTrue(p.notRunning)
        assertEquals(1, p.requiredMissing)
        val ok = p.copy(running = true)
        assertTrue(ok.allGranted)
        assertFalse(ok.notRunning)
    }

    @Test
    fun `autostart counts only where the phone has it, and trusts the phone over the visit`() {
        assertTrue(PermissionState(autostartScreen = false).autostartDone)
        assertFalse(PermissionState(autostartScreen = true).autostartDone)
        assertTrue(PermissionState(autostartScreen = true, autostartConfirmed = true).autostartDone)
        // The phone says it's off: a visit doesn't override that.
        assertFalse(PermissionState(autostartScreen = true, autostart = false, autostartConfirmed = true).autostartDone)
        assertTrue(PermissionState(autostartScreen = true, autostart = true).autostartDone)
    }

    @Test
    fun `keep alive needs battery and autostart`() {
        assertFalse(PermissionState(battery = false).keepAliveDone)
        assertTrue(PermissionState(battery = true).keepAliveDone)
        assertFalse(PermissionState(battery = true, autostartScreen = true).keepAliveDone)
    }

    @Test
    fun `an "I allowed it" only counts where the phone can't say`() {
        // Android says still blocked: believe Android.
        assertTrue(PermissionState.unblockNeeded(Verdict.Restricted, confirmed = true))
        // Phone can't say: believe the user.
        assertTrue(PermissionState.unblockNeeded(Verdict.LikelyRestricted, confirmed = false))
        assertFalse(PermissionState.unblockNeeded(Verdict.LikelyRestricted, confirmed = true))
        // Already allowed, or no gate on this phone.
        assertFalse(PermissionState.unblockNeeded(Verdict.Cleared, confirmed = false))
        assertFalse(PermissionState.unblockNeeded(Verdict.NotApplicable, confirmed = false))
        assertFalse(PermissionState.unblockNeeded(Verdict.Unknown, confirmed = false))
    }
}
