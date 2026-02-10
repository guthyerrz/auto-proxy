package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.XmlUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element
import java.io.File

class ManifestPatcherTest {

    @Test
    fun `adds ContentProvider to minimal manifest`(@TempDir tempDir: File) {
        val manifest = copyFixture("sample-manifest-minimal.xml", tempDir)
        ManifestPatcher.execute(tempDir)

        val doc = XmlUtils.parse(manifest)
        val providers = doc.getElementsByTagName("provider")
        assertEquals(1, providers.length)

        val provider = providers.item(0) as Element
        assertEquals(
            "com.guthyerrz.autoproxy.AutoProxyInitializer",
            provider.getAttributeNS("http://schemas.android.com/apk/res/android", "name"),
        )
        assertEquals(
            "com.example.testapp.autoproxy-init",
            provider.getAttributeNS("http://schemas.android.com/apk/res/android", "authorities"),
        )
        assertEquals(
            "false",
            provider.getAttributeNS("http://schemas.android.com/apk/res/android", "exported"),
        )
    }

    @Test
    fun `detects already patched manifest`(@TempDir tempDir: File) {
        copyFixture("sample-manifest-minimal.xml", tempDir)
        ManifestPatcher.execute(tempDir)

        assertThrows(PatcherException::class.java) {
            ManifestPatcher.execute(tempDir)
        }
    }

    @Test
    fun `reads package name correctly`() {
        val doc = loadFixtureDoc("sample-manifest-minimal.xml")
        assertEquals("com.example.testapp", ManifestPatcher.getPackageName(doc))
    }

    private fun copyFixture(name: String, tempDir: File): File {
        val input = javaClass.classLoader.getResourceAsStream("fixtures/$name")!!
        val manifest = File(tempDir, "AndroidManifest.xml")
        input.use { src -> manifest.outputStream().use { dst -> src.copyTo(dst) } }
        return manifest
    }

    private fun loadFixtureDoc(name: String): org.w3c.dom.Document {
        val input = javaClass.classLoader.getResourceAsStream("fixtures/$name")!!
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(input)
    }
}
