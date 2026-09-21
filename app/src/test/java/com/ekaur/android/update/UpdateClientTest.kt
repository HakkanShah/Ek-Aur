package com.ekaur.android.update

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UpdateClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = UpdateClient(
        repo = "HakkanShah/Ek-Aur",
        apiBase = server.url("").toString().removeSuffix("/"),
    )

    @Test
    fun `reads the latest release from the api`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                { "tag_name": "v0.18.0", "html_url": "https://x/y",
                  "assets": [ { "name": "ekaur-v0.18.0-build36.apk",
                    "browser_download_url": "https://x/ekaur-v0.18.0-build36.apk" } ] }
                """.trimIndent()
            )
        )

        val release = client().latest()!!

        assertEquals(36, release.versionCode)
        assertEquals("0.18.0", release.versionName)
        val path = server.takeRequest().path
        assertEquals("/repos/HakkanShah/Ek-Aur/releases/latest", path)
    }

    @Test
    fun `a 404 (no releases yet) is treated as nothing to update to`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "message": "Not Found" }"""))

        assertNull(client().latest())
    }

    // A minimal APK-shaped body: the ZIP local-file header the client checks for.
    private fun apkBody(payload: String = "ek-aur-apk-payload"): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + payload.toByteArray()

    private fun bodyOf(bytes: ByteArray) =
        MockResponse().setResponseCode(200).setBody(Buffer().write(bytes))

    @Test
    fun `downloads a valid apk to the destination file`() {
        val bytes = apkBody()
        server.enqueue(bodyOf(bytes))
        val dest = File.createTempFile("ekaur", ".apk").apply { delete() }

        val file = client().download(
            url = server.url("/download/app.apk").toString(),
            dest = dest,
            expectedSize = bytes.size.toLong(),
        ) { }

        assertTrue(file != null)
        assertArrayEquals(bytes, dest.readBytes())
        assertFalse(File(dest.parentFile, dest.name + ".part").exists())
        dest.delete()
    }

    @Test
    fun `a short download is rejected and leaves nothing behind`() {
        // The server delivers the whole body, but the API said the asset is
        // bigger -- exactly the truncated-stream case that produced a broken APK.
        val bytes = apkBody()
        server.enqueue(bodyOf(bytes))
        val dest = File.createTempFile("ekaur", ".apk").apply { delete() }

        val file = client().download(
            url = server.url("/download/app.apk").toString(),
            dest = dest,
            expectedSize = bytes.size.toLong() + 4096,
        ) { }

        assertNull(file)
        assertFalse(dest.exists())
        assertFalse(File(dest.parentFile, dest.name + ".part").exists())
    }

    @Test
    fun `a body that is not an apk is rejected`() {
        // e.g. an HTML error page served with a 200.
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>nope</html>"))
        val dest = File.createTempFile("ekaur", ".apk").apply { delete() }

        val file = client().download(server.url("/download/app.apk").toString(), dest) { }

        assertNull(file)
        assertFalse(dest.exists())
    }

    @Test
    fun `looksLikeApk only accepts a zip header`() {
        val good = File.createTempFile("good", ".apk")
            .apply { writeBytes(apkBody()) }
        val bad = File.createTempFile("bad", ".apk")
            .apply { writeText("not a zip") }

        assertTrue(UpdateClient.looksLikeApk(good))
        assertFalse(UpdateClient.looksLikeApk(bad))
        good.delete()
        bad.delete()
    }
}
