package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.XmlUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element
import java.io.File

class NetworkSecurityConfigPatcherTest {

    @Test
    fun `creates NSC when none exists`(@TempDir tempDir: File) {
        val nscFile = File(tempDir, "res/xml/network_security_config.xml")
        assertFalse(nscFile.exists())

        NetworkSecurityConfigPatcher.createNetworkSecurityConfig(nscFile)
        assertTrue(nscFile.exists())

        val doc = XmlUtils.parse(nscFile)
        val baseConfigs = doc.getElementsByTagName("base-config")
        assertEquals(1, baseConfigs.length)

        val trustAnchors = doc.getElementsByTagName("trust-anchors")
        assertEquals(1, trustAnchors.length)

        val certs = doc.getElementsByTagName("certificates")
        assertEquals(2, certs.length)

        val srcs = (0 until certs.length).map { (certs.item(it) as Element).getAttribute("src") }
        assertTrue(srcs.contains("system"))
        assertTrue(srcs.contains("user"))
    }

    @Test
    fun `merges user CA into existing NSC`(@TempDir tempDir: File) {
        val nscFile = copyFixture("sample-network-security-config.xml", tempDir)

        NetworkSecurityConfigPatcher.mergeUserCaTrust(nscFile)

        val doc = XmlUtils.parse(nscFile)
        val baseConfig = doc.getElementsByTagName("base-config").item(0) as Element
        val trustAnchors = baseConfig.getElementsByTagName("trust-anchors").item(0) as Element
        val certs = trustAnchors.getElementsByTagName("certificates")

        val srcs = (0 until certs.length).map { (certs.item(it) as Element).getAttribute("src") }
        assertTrue(srcs.contains("system"))
        assertTrue(srcs.contains("user"))
    }

    @Test
    fun `does not duplicate user CA trust`(@TempDir tempDir: File) {
        val nscFile = copyFixture("sample-network-security-config.xml", tempDir)

        NetworkSecurityConfigPatcher.mergeUserCaTrust(nscFile)
        NetworkSecurityConfigPatcher.mergeUserCaTrust(nscFile)

        val doc = XmlUtils.parse(nscFile)
        val baseConfig = doc.getElementsByTagName("base-config").item(0) as Element
        val trustAnchors = baseConfig.getElementsByTagName("trust-anchors").item(0) as Element
        val certs = trustAnchors.getElementsByTagName("certificates")

        val userCount = (0 until certs.length).count {
            (certs.item(it) as Element).getAttribute("src") == "user"
        }
        assertEquals(1, userCount)
    }

    @Test
    fun `ensures manifest reference to NSC`(@TempDir tempDir: File) {
        copyManifestFixture("sample-manifest-minimal.xml", tempDir)

        NetworkSecurityConfigPatcher.ensureManifestReference(tempDir)

        val doc = XmlUtils.parse(File(tempDir, "AndroidManifest.xml"))
        val app = doc.getElementsByTagName("application").item(0) as Element
        val nscRef = app.getAttributeNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig")
        assertEquals("@xml/network_security_config", nscRef)
    }

    @Test
    fun `preserves existing NSC manifest reference`(@TempDir tempDir: File) {
        copyManifestFixture("sample-manifest-with-nsc.xml", tempDir)

        NetworkSecurityConfigPatcher.ensureManifestReference(tempDir)

        val doc = XmlUtils.parse(File(tempDir, "AndroidManifest.xml"))
        val app = doc.getElementsByTagName("application").item(0) as Element
        val nscRef = app.getAttributeNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig")
        assertEquals("@xml/network_security_config", nscRef)
    }

    private fun copyFixture(name: String, tempDir: File): File {
        val input = javaClass.classLoader.getResourceAsStream("fixtures/$name")!!
        val dir = File(tempDir, "res/xml")
        dir.mkdirs()
        val file = File(dir, "network_security_config.xml")
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        return file
    }

    private fun copyManifestFixture(name: String, tempDir: File): File {
        val input = javaClass.classLoader.getResourceAsStream("fixtures/$name")!!
        val manifest = File(tempDir, "AndroidManifest.xml")
        input.use { src -> manifest.outputStream().use { dst -> src.copyTo(dst) } }
        return manifest
    }
}
