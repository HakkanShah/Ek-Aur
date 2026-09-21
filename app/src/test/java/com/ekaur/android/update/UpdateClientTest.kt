package com.ekaur.android.update

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun `downloads the asset to the destination file`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("APK-BYTES"))
        val dest = File.createTempFile("ekaur", ".apk").apply { delete() }

        val file = client().download(server.url("/download/app.apk").toString(), dest) { }

        assertTrue(file != null)
        assertEquals("APK-BYTES", dest.readText())
        dest.delete()
    }
}
