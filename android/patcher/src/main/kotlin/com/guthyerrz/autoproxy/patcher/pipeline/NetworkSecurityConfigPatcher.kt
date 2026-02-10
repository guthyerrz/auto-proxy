package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import com.guthyerrz.autoproxy.patcher.util.XmlUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

object NetworkSecurityConfigPatcher {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val NSC_PATH = "res/xml/network_security_config.xml"
    private const val NSC_REF = "@xml/network_security_config"

    fun execute(decodedDir: File) {
        Logger.step("Patching network security config")

        val nscFile = File(decodedDir, NSC_PATH)
        if (nscFile.exists()) {
            mergeUserCaTrust(nscFile)
        } else {
            createNetworkSecurityConfig(nscFile)
        }

        ensureManifestReference(decodedDir)
        Logger.info("Network security config updated to trust user CAs")
    }

    internal fun createNetworkSecurityConfig(nscFile: File) {
        nscFile.parentFile?.mkdirs()
        nscFile.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
"""
        )
    }

    internal fun mergeUserCaTrust(nscFile: File) {
        val document = XmlUtils.parse(nscFile)
        mergeUserCaTrustInDocument(document)
        XmlUtils.write(document, nscFile)
    }

    internal fun mergeUserCaTrustInDocument(document: Document) {
        val root = document.documentElement

        // Find or create <base-config>
        var baseConfig: Element? = null
        val baseConfigs = root.getElementsByTagName("base-config")
        if (baseConfigs.length > 0) {
            baseConfig = baseConfigs.item(0) as Element
        }

        if (baseConfig == null) {
            baseConfig = document.createElement("base-config")
            root.insertBefore(baseConfig, root.firstChild)
        }

        // Find or create <trust-anchors> inside <base-config>
        var trustAnchors: Element? = null
        val trustAnchorsList = baseConfig.getElementsByTagName("trust-anchors")
        if (trustAnchorsList.length > 0) {
            trustAnchors = trustAnchorsList.item(0) as Element
        }

        if (trustAnchors == null) {
            trustAnchors = document.createElement("trust-anchors")
            baseConfig.appendChild(trustAnchors)
        }

        // Check if user CA trust already exists
        val certificates = trustAnchors.getElementsByTagName("certificates")
        var hasUserCert = false
        for (i in 0 until certificates.length) {
            val cert = certificates.item(i) as Element
            if (cert.getAttribute("src") == "user") {
                hasUserCert = true
                break
            }
        }

        if (!hasUserCert) {
            val userCert = document.createElement("certificates")
            userCert.setAttribute("src", "user")
            trustAnchors.appendChild(userCert)
        }
    }

    internal fun ensureManifestReference(decodedDir: File) {
        val manifestFile = File(decodedDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) return

        val document = XmlUtils.parse(manifestFile)
        val application = document.getElementsByTagName("application").item(0) as? Element ?: return

        val existingRef = application.getAttributeNS(ANDROID_NS, "networkSecurityConfig")
        if (existingRef.isNullOrEmpty()) {
            application.setAttributeNS(ANDROID_NS, "android:networkSecurityConfig", NSC_REF)
            XmlUtils.write(document, manifestFile)
        }
    }
}
