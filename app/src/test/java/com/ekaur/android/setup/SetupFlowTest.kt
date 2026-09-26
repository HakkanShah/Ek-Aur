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
    ) = SetupFlow.nextStep(welcomed, overlay, keepAlive, service, running)

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
    fun `dots follow the steps`() {
        assertEquals(-1, SetupFlow.dotIndex(SetupStep.Welcome))
        assertEquals(0, SetupFlow.dotIndex(SetupStep.Overlay))
        assertEquals(1, SetupFlow.dotIndex(SetupStep.KeepAlive))
        assertEquals(2, SetupFlow.dotIndex(SetupStep.Accessibility))
        assertEquals(2, SetupFlow.dotIndex(SetupStep.Restart))
    }

    @Test
    fun `status line is pluralised`() {
        assertEquals("You're all set.", SetupFlow.statusLine(0))
        assertEquals("1 step left", SetupFlow.statusLine(1))
        assertEquals("2 steps left", SetupFlow.statusLine(2))
        assertEquals("4 steps left", SetupFlow.statusLine(4))
    }
}
