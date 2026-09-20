package com.ekaur.android.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceKeyTest {

    @Test
    fun `the same phone always produces the same key`() {
        // The whole recovery story rests on this being stable across installs.
        assertEquals(DeviceKey.of("abc123"), DeviceKey.of("abc123"))
    }

    @Test
    fun `different phones do not collide`() {
        assertNotEquals(DeviceKey.of("abc123"), DeviceKey.of("abc124"))
    }

    @Test
    fun `the raw android id never leaves the device`() {
        val key = DeviceKey.of("abc123")

        assertEquals(false, key!!.contains("abc123"))
        assertEquals(32, key.length)
    }

    @Test
    fun `the known broken android id is refused`() {
        // A value some emulators and modified builds all report. Trusting it
        // would hand one person's account to everyone who shares it.
        assertNull(DeviceKey.of("9774d56d682e549c"))
        assertNull(DeviceKey.of("9774D56D682E549C"))
    }

    @Test
    fun `a missing id is not a key`() {
        assertNull(DeviceKey.of(null))
        assertNull(DeviceKey.of(""))
        assertNull(DeviceKey.of("   "))
    }
}
