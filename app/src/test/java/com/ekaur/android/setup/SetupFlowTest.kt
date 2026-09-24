package com.ekaur.android.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFlowTest {

    @Test
    fun `a fresh install starts on welcome`() {
        assertEquals(SetupStep.Welcome, SetupFlow.nextStep(welcomed = false, service = false, overlay = false))
    }

    @Test
    fun `welcome gives way to accessibility once pressed through`() {
        assertEquals(SetupStep.Accessibility, SetupFlow.nextStep(welcomed = true, service = false, overlay = false))
    }

    @Test
    fun `a granted step is never shown again`() {
        // Accessibility on (in Settings, while the app was in the background):
        // the flow moves straight to overlay, welcomed or not.
        assertEquals(SetupStep.Overlay, SetupFlow.nextStep(welcomed = false, service = true, overlay = false))
        assertEquals(SetupStep.Overlay, SetupFlow.nextStep(welcomed = true, service = true, overlay = false))
        // Overlay granted first (an old install): accessibility is still the step.
        assertEquals(SetupStep.Accessibility, SetupFlow.nextStep(welcomed = true, service = false, overlay = true))
    }

    @Test
    fun `both grants is done regardless of welcome`() {
        assertEquals(SetupStep.Done, SetupFlow.nextStep(welcomed = false, service = true, overlay = true))
        assertEquals(SetupStep.Done, SetupFlow.nextStep(welcomed = true, service = true, overlay = true))
    }

    @Test
    fun `progress counts the required grants`() {
        assertEquals(0, SetupFlow.requiredDone(service = false, overlay = false))
        assertEquals(1, SetupFlow.requiredDone(service = true, overlay = false))
        assertEquals(2, SetupFlow.requiredDone(service = true, overlay = true))
    }

    @Test
    fun `status line is pluralised`() {
        assertEquals("You're all set.", SetupFlow.statusLine(0))
        assertEquals("1 step left", SetupFlow.statusLine(1))
        assertEquals("2 steps left", SetupFlow.statusLine(2))
        assertEquals("4 steps left", SetupFlow.statusLine(4))
    }
}
