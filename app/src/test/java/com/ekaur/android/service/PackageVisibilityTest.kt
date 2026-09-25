package com.ekaur.android.service

import com.ekaur.android.detect.TrackedApp
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the build-50 bug: on Android 11+ an app not declared in <queries>
 * is invisible, so every counted app must be declared, or it reads as not
 * installed and can never be switched on.
 */
class PackageVisibilityTest {

    @Test
    fun `every counted app is declared as visible in the manifest`() {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File)
            .first { it.exists() }
            .readText()
        val queries = manifest.substringAfter("<queries>", "").substringBefore("</queries>")
        for (app in TrackedApp.entries) {
            assertTrue(
                "${app.packageName} missing from <queries>",
                queries.contains("android:name=\"${app.packageName}\""),
            )
        }
    }
}
