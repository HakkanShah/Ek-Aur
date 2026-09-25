package com.ekaur.android.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
import com.ekaur.android.detect.TrackedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every icon is built from hand-written path strings, so a typo would only
 * show up as a crash the first time a screen draws it. Build them all here.
 */
class IconsTest {

    private val all: List<ImageVector> = with(EkIcons) {
        listOf(
            Back, Close, Check, ChevronDown, Warning, Alert, Lock, Refresh, WifiOff, EyeOff,
            Crown, Pencil, Image, Folder, Copy, Share, Sparkle, Target, Moon, TrendDown, Timer,
            Person, Layers, Battery, Activity, FloatingButton, Tiles, Pause, Download, Gift,
            Box, CheckCircle, MoreVert, Reels, Shorts,
        )
    }

    @Test
    fun `every icon builds on a 24 unit grid with at least one path`() {
        all.forEach { icon ->
            assertEquals(icon.name, 24f, icon.viewportWidth)
            assertEquals(icon.name, 24f, icon.viewportHeight)
            assertTrue(icon.name, icon.root.size > 0)
        }
    }

    @Test
    fun `icon names are unique`() {
        assertEquals(all.size, all.map { it.name }.toSet().size)
    }

    @Test
    fun `each tracked app has its own icon and colour`() {
        assertNotEquals(TrackedApp.Instagram.icon, TrackedApp.YouTube.icon)
        assertNotEquals(TrackedApp.Instagram.mark, TrackedApp.YouTube.mark)
        assertNotEquals(TrackedApp.Instagram.markSoft, TrackedApp.YouTube.markSoft)
    }
}
