package com.ekaur.android.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFlowTest {

    private fun step(
        welcomed: Boolean = true,
        overlay: Boolean = false,
        keepAlive: Boolean = false,
        service: Boolean = false,
        running: Boolean = false,
        unblock: Boolean = false,
    ) = SetupFlow.nextStep(welcomed, overlay, keepAlive, service, running, unblock)

    @Test
    fun `a fresh install starts on welcome`() {
        assertEquals(SetupStep.Welcome, step(welcomed = false))
    }

    @Test
    fun `overlay comes first, so the guide can float over Settings`() {
        assertEquals(SetupStep.Overlay, step())
    }

    @Test
    fun `the phone's own limits are cleared before accessibility`() {
        assertEquals(SetupStep.KeepAlive, step(overlay = true))
        assertEquals(SetupStep.Accessibility, step(overlay = true, keepAlive = true))
    }

    @Test
    fun `a switch that is on but never started asks for a restart`() {
        assertEquals(SetupStep.Restart, step(overlay = true, keepAlive = true, service = true))
    }

    @Test
    fun `on but not running, with the phone's limits still on, fixes those first`() {
        // The Xiaomi report: switch on, service never started, battery restricted.
        assertEquals(SetupStep.KeepAlive, step(overlay = true, service = true))
    }

    @Test
    fun `running and overlay is done, whatever else`() {
        assertEquals(SetupStep.Done, step(welcomed = false, overlay = true, service = true, running = true))
        assertEquals(SetupStep.Done, step(overlay = true, keepAlive = false, service = true, running = true))
    }

    @Test
    fun `a running service without overlay only asks for overlay`() {
        assertEquals(SetupStep.Overlay, step(welcomed = false, service = true, running = true))
    }

    @Test
    fun `restricted settings are asked for before the switch they block`() {
        assertEquals(SetupStep.Unblock, step(overlay = true, keepAlive = true, unblock = true))
        assertEquals(SetupStep.Accessibility, step(overlay = true, keepAlive = true, unblock = false))
        // Keep alive still comes first.
        assertEquals(SetupStep.KeepAlive, step(overlay = true, unblock = true))
    }

    @Test
    fun `a switch already on never asks to unblock`() {
        assertEquals(SetupStep.Restart, step(overlay = true, keepAlive = true, service = true, unblock = true))
        assertEquals(SetupStep.Done, step(overlay = true, service = true, running = true, unblock = true))
    }

    @Test
    fun `dots follow the steps, with unblock only where it applies`() {
        assertEquals(-1, SetupFlow.dotIndex(SetupStep.Welcome, unblock = false))
        assertEquals(0, SetupFlow.dotIndex(SetupStep.Overlay, unblock = false))
        assertEquals(1, SetupFlow.dotIndex(SetupStep.KeepAlive, unblock = false))
        assertEquals(2, SetupFlow.dotIndex(SetupStep.Accessibility, unblock = false))
        assertEquals(2, SetupFlow.dotIndex(SetupStep.Restart, unblock = false))
        assertEquals(3, SetupFlow.dotted(unblock = false).size)

        assertEquals(2, SetupFlow.dotIndex(SetupStep.Unblock, unblock = true))
        assertEquals(3, SetupFlow.dotIndex(SetupStep.Accessibility, unblock = true))
        assertEquals(3, SetupFlow.dotIndex(SetupStep.Restart, unblock = true))
        assertEquals(4, SetupFlow.dotted(unblock = true).size)
    }

    @Test
    fun `status line is pluralised`() {
        assertEquals("You're all set.", SetupFlow.statusLine(0))
        assertEquals("1 step left", SetupFlow.statusLine(1))
        assertEquals("2 steps left", SetupFlow.statusLine(2))
        assertEquals("4 steps left", SetupFlow.statusLine(4))
    }
}
