package com.ekaur.android.diagnostics

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Android keeps one running instance per provider class in a process, so two
 * providers declared with the same class share one instance and one set of
 * paths. That is what crashed Share events and Report a bug in build 54: the
 * export provider's requests went to the card provider.
 */
class ProviderManifestTest {

    private val providers: List<Element> by lazy {
        val manifest = File("src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val nodes = doc.getElementsByTagName("provider")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.android(attr: String) =
        getAttributeNS("http://schemas.android.com/apk/res/android", attr)

    @Test
    fun `every provider has its own class`() {
        val names = providers.map { it.android("name") }
        assertEquals("duplicate provider classes: $names", names.size, names.toSet().size)
    }

    @Test
    fun `the export authority is served by ExportsProvider with the export paths`() {
        val exports = providers.single { it.android("authorities").endsWith(".fileprovider") }
        assertEquals(".diagnostics.ExportsProvider", exports.android("name"))
        val paths = exports.getElementsByTagName("meta-data").item(0) as Element
        assertEquals("@xml/file_paths", paths.android("resource"))
        assertTrue(File("src/main/res/xml/file_paths.xml").readText().contains("path=\"exports/\""))
    }
}
