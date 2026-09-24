package com.ekaur.android.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemHintsTest {

    @Test
    fun `samsung is matched case-insensitively`() {
        val hint = OemHints.forDevice("Samsung", "samsung")
        assertEquals("Samsung", hint.brand)
        assertEquals("Installed apps", hint.listSection)
        assertFalse(hint.hedged)
    }

    @Test
    fun `redmi and poco report xiaomi as manufacturer and match on brand`() {
        assertEquals("Xiaomi", OemHints.forDevice("Xiaomi", "Redmi").brand)
        assertEquals("Xiaomi", OemHints.forDevice("Xiaomi", "POCO").brand)
        assertEquals("Xiaomi", OemHints.forDevice("Xiaomi", "xiaomi").brand)
    }

    @Test
    fun `coloros brands are hedged`() {
        for (m in listOf("realme", "OPPO", "OnePlus")) {
            val hint = OemHints.forDevice(m, m)
            assertTrue(m, hint.hedged)
            assertTrue(hint.menuLine.contains("usually"))
            assertTrue(hint.appInfoPath.contains("App management"))
        }
    }

    @Test
    fun `iqoo reports vivo`() {
        val hint = OemHints.forDevice("vivo", "iQOO")
        assertEquals("vivo / iQOO", hint.brand)
        assertTrue(hint.hedged)
    }

    @Test
    fun `unknown, blank and null fall back to stock`() {
        for (hint in listOf(
            OemHints.forDevice("Google", "google"),
            OemHints.forDevice("", ""),
            OemHints.forDevice(null, null),
            OemHints.forDevice("motorola", "motorola"),
        )) {
            assertNull(hint.brand)
            assertEquals("Downloaded apps", hint.listSection)
            assertFalse(hint.hedged)
            assertFalse(hint.menuLine.contains("usually"))
        }
    }

    @Test
    fun `every menu line ends on the allow item`() {
        for (m in listOf("samsung", "xiaomi", "realme", "vivo", "google")) {
            assertTrue(OemHints.forDevice(m, m).menuLine.endsWith("Allow restricted settings."))
        }
    }
}
