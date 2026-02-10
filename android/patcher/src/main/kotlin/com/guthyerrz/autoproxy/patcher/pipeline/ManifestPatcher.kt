package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import com.guthyerrz.autoproxy.patcher.util.XmlUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

object ManifestPatcher {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val PROVIDER_CLASS = "com.guthyerrz.autoproxy.AutoProxyInitializer"
    private const val AUTHORITY_SUFFIX = ".autoproxy-init"

    fun execute(decodedDir: File) {
        val manifestFile = File(decodedDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            throw PatcherException("AndroidManifest.xml not found in decoded APK")
        }

        Logger.step("Patching AndroidManifest.xml")

        val document = XmlUtils.parse(manifestFile)
        val packageName = getPackageName(document)

        checkForExistingProvider(document)
        addProvider(document, packageName)

        XmlUtils.write(document, manifestFile)
        Logger.info("Added ContentProvider to manifest (authority: $packageName$AUTHORITY_SUFFIX)")
    }

    internal fun getPackageName(document: Document): String {
        val manifest = document.documentElement
        return manifest.getAttribute("package").ifEmpty {
            throw PatcherException("No 'package' attribute found in <manifest>")
        }
    }

    internal fun checkForExistingProvider(document: Document) {
        val providers = document.getElementsByTagName("provider")
        for (i in 0 until providers.length) {
            val provider = providers.item(i) as Element
            val name = provider.getAttributeNS(ANDROID_NS, "name")
            if (name == PROVIDER_CLASS) {
                throw PatcherException(
                    "ContentProvider '$PROVIDER_CLASS' already exists in manifest. " +
                        "The APK may already be patched."
                )
            }
        }
    }

    internal fun addProvider(document: Document, packageName: String) {
        val application = document.getElementsByTagName("application").item(0) as? Element
            ?: throw PatcherException("No <application> element found in manifest")

        val provider = document.createElement("provider")
        provider.setAttributeNS(ANDROID_NS, "android:name", PROVIDER_CLASS)
        provider.setAttributeNS(ANDROID_NS, "android:authorities", "$packageName$AUTHORITY_SUFFIX")
        provider.setAttributeNS(ANDROID_NS, "android:exported", "false")

        application.appendChild(provider)
    }

    internal fun patchDocument(document: Document): Document {
        val packageName = getPackageName(document)
        checkForExistingProvider(document)
        addProvider(document, packageName)
        return document
    }
}
