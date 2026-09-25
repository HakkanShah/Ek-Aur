package com.ekaur.android.diagnostics

import android.content.Intent
import android.net.Uri
import com.ekaur.android.EkAurApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Getting evidence off a friend's phone: the share that failed on WhatsApp,
 * and the bug report that replaces it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReportingTest {

    private val app = RuntimeEnvironment.getApplication() as EkAurApp

    @Test
    fun `a shared dump travels as a file with one short line, never the whole text`() {
        val dump = "x".repeat(200_000)
        val file = TextExport.write(app, "ekaur-events.txt", dump)
        val intent = TextExport.intentFor(app, file, TextExport.summaryOf("ekaur-events.txt", dump))

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue("text should be a one-liner, was ${text.length} chars", text.length < 200)
        val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("${app.packageName}.fileprovider", stream.authority)
        assertNotNull("the grant only reaches the chosen app through ClipData", intent.clipData)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(dump, file.readText())
    }

    @Test
    fun `exported files get distinct timestamped names`() {
        val file = TextExport.write(app, "ekaur-events.txt", "a")
        assertTrue(file.name, file.name.matches(Regex("""ekaur-events-\d{8}-\d{6}\.txt""")))
    }

    @Test
    fun `a bug report attaches the events and the status`() = runTest {
        File(app.filesDir, CRASH_FILE).delete()
        val files = BugReport.attachments(app, app.container)
        assertEquals(listOf("ekaur-events", "ekaur-status"), files.map { it.name.substringBefore("-2") })
    }

    @Test
    fun `a pending crash rides along`() = runTest {
        File(app.filesDir, CRASH_FILE).writeText("EK AUR crash\nboom")
        try {
            val files = BugReport.attachments(app, app.container)
            assertEquals(3, files.size)
            assertTrue(files.last().readText().contains("boom"))
        } finally {
            File(app.filesDir, CRASH_FILE).delete()
        }
    }

    @Test
    fun `the status says what the app can see about itself`() = runTest {
        val status = BugReport.statusText(app, app.container)
        listOf(
            "version:", "device:", "android:", "accessibility on:", "service running:",
            "usage access:", "auto-off:", "last auto-off:", "shorts:", "reels:",
            "shorts page size:", "detector state:", "foreground (usage):",
        ).forEach { assertTrue("missing '$it' in\n$status", status.contains(it)) }
    }

    private companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
