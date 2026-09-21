package com.ekaur.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val JSON = """
{
  "tag_name": "v0.18.0",
  "name": "Ek Aur 0.18.0",
  "body": "- floating pill polish\n- share card fix",
  "html_url": "https://github.com/HakkanShah/Ek-Aur/releases/tag/v0.18.0",
  "draft": false,
  "prerelease": false,
  "assets": [
    {
      "name": "ekaur-v0.18.0-build36.apk",
      "browser_download_url": "https://github.com/HakkanShah/Ek-Aur/releases/download/v0.18.0/ekaur-v0.18.0-build36.apk",
      "size": 14017284
    }
  ]
}
"""

class UpdateResolverTest {

    @Test
    fun `parses tag, build number and the apk url`() {
        val release = UpdateResolver.parseLatest(JSON)!!

        assertEquals("0.18.0", release.versionName)
        assertEquals(36, release.versionCode)
        assertTrue(release.apkUrl!!.endsWith("ekaur-v0.18.0-build36.apk"))
        assertEquals("- floating pill polish\n- share card fix", release.notes)
        assertEquals(14017284L, release.apkSize)
    }

    @Test
    fun `newer by build number, not by name`() {
        val release = UpdateResolver.parseLatest(JSON)!!

        assertTrue(UpdateResolver.isNewer(35, "0.17.7", release))
        assertFalse(UpdateResolver.isNewer(36, "0.18.0", release))
        assertFalse(UpdateResolver.isNewer(40, "0.18.0", release))
    }

    @Test
    fun `build number is read out of the asset name`() {
        assertEquals(36, UpdateResolver.buildFromAssetName("ekaur-v0.18.0-build36.apk"))
        assertEquals(7, UpdateResolver.buildFromAssetName("app-build7.apk"))
        assertNull(UpdateResolver.buildFromAssetName("ekaur-latest.apk"))
    }

    @Test
    fun `a release with no build number falls back to comparing the tag`() {
        val json = """
            { "tag_name": "v0.19.0", "html_url": "https://x/y",
              "assets": [ { "name": "ekaur.apk", "browser_download_url": "https://x/ekaur.apk" } ] }
        """
        val release = UpdateResolver.parseLatest(json)!!

        assertNull(release.versionCode)
        assertTrue(UpdateResolver.isNewer(36, "0.18.0", release))
        assertFalse(UpdateResolver.isNewer(36, "0.19.0", release))
        assertFalse(UpdateResolver.isNewer(36, "0.20.0", release))
    }

    @Test
    fun `a release with no apk asset still parses, with a null url`() {
        val json = """{ "tag_name": "v0.18.0", "html_url": "https://x/y", "assets": [] }"""
        val release = UpdateResolver.parseLatest(json)!!

        assertEquals("0.18.0", release.versionName)
        assertNull(release.apkUrl)
        assertNull(release.versionCode)
    }

    @Test
    fun `a not-found body is no release`() {
        assertNull(UpdateResolver.parseLatest("""{ "message": "Not Found" }"""))
        assertNull(UpdateResolver.parseLatest("not json at all"))
    }

    @Test
    fun `semver compare handles missing parts`() {
        assertTrue(UpdateResolver.compareSemver("0.18.0", "0.17.7") > 0)
        assertTrue(UpdateResolver.compareSemver("0.18", "0.18.0") == 0)
        assertTrue(UpdateResolver.compareSemver("1.0.0", "0.99.99") > 0)
    }
}
