package com.ekaur.android.service

import com.ekaur.android.service.ForegroundPolicy.Reading
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundPolicyTest {

    private val keyboard = "com.google.android.inputmethod.latin"

    @Test
    fun `a counted app in front is believed at once`() {
        val p = ForegroundPolicy()
        assertEquals(Reading.InTracked, p.read("com.google.android.youtube", isTracked = true, keyboard))
    }

    @Test
    fun `one reading of somewhere else is not enough to call it leaving`() {
        val p = ForegroundPolicy()
        assertEquals(Reading.Unknown, p.read("com.whatsapp", isTracked = false, keyboard))
        assertEquals(Reading.Left, p.read("com.whatsapp", isTracked = false, keyboard))
    }

    @Test
    fun `coming back in between resets the count`() {
        val p = ForegroundPolicy()
        p.read("com.whatsapp", isTracked = false, keyboard)
        p.read("com.google.android.youtube", isTracked = true, keyboard)
        assertEquals(Reading.Unknown, p.read("com.whatsapp", isTracked = false, keyboard))
    }

    @Test
    fun `system screens and the keyboard never count as leaving`() {
        val p = ForegroundPolicy()
        listOf("com.android.systemui", "com.google.android.gms", "com.miui.securitycenter", keyboard).forEach { pkg ->
            repeat(3) { assertEquals(pkg, Reading.Unknown, p.read(pkg, isTracked = false, keyboard)) }
        }
    }

    @Test
    fun `no reading changes nothing`() {
        assertEquals(Reading.Unknown, ForegroundPolicy().read(null, isTracked = false, keyboard))
    }
}
