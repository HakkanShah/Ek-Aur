package com.ekaur.android.service

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.ekaur.android.diagnostics.TextExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Reports and feedback must land in the mail app, addressed to it by package.
 * Asking for "any mailto: app" through a selector let Android 13+ refuse the
 * delivery, and the app said no mail app was installed with Gmail sitting
 * right there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MailRoutingTest {

    private val app = RuntimeEnvironment.getApplication()
    private val gmail = ComponentName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmailExternal")
    private val otherMail = ComponentName("com.example.mail", "com.example.mail.Compose")

    private fun installMailApp(component: ComponentName) {
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("mailto") },
        )
    }

    @Test
    fun `a bug report goes straight to Gmail with the files attached`() {
        installMailApp(otherMail)
        installMailApp(gmail)
        val file = TextExport.write(app, "ekaur-events.txt", "events")

        val outcome = ServiceControl.emailWithAttachments(app, "Ek Aur bug", "What happened?", listOf(file))

        assertEquals(ServiceControl.MailOutcome.Mail, outcome)
        val sent = shadowOf(app).nextStartedActivity
        assertEquals("Gmail is preferred when installed", "com.google.android.gm", sent.`package`)
        assertEquals(Intent.ACTION_SEND, sent.action)
        assertEquals(ServiceControl.DEVELOPER_EMAIL, sent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.single())
        assertEquals("Ek Aur bug", sent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(sent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) != null)
        assertTrue(sent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `feedback with no files is still an email, not the share sheet`() {
        installMailApp(gmail)
        val outcome = ServiceControl.emailWithAttachments(app, "Ek Aur feedback", "Hi", emptyList())
        assertEquals(ServiceControl.MailOutcome.Mail, outcome)
        assertEquals("com.google.android.gm", shadowOf(app).nextStartedActivity.`package`)
    }

    @Test
    fun `Gmail is listed first among mail apps`() {
        installMailApp(otherMail)
        installMailApp(gmail)
        assertEquals("com.google.android.gm", ServiceControl.mailApps(app).first())
    }
}
