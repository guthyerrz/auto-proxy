package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File
import kotlin.io.path.createTempDirectory

class ApkPatcher(
    private val inputApk: File,
    private val outputApk: File,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val certFile: File?,
    private val signingConfig: ApkSignStep.SigningConfig?,
) {

    fun patch() {
        Logger.info("Auto Proxy APK Patcher")
        Logger.info("Input:  ${inputApk.absolutePath}")
        Logger.info("Output: ${outputApk.absolutePath}")
        Logger.info("Proxy:  $proxyHost:$proxyPort")

        if (!inputApk.exists()) {
            throw PatcherException("Input APK not found: ${inputApk.absolutePath}")
        }

        val workDir = createTempDirectory("auto-proxy-patcher").toFile()
        try {
            val decodedDir = File(workDir, "decoded")
            val rebuiltApk = File(workDir, "rebuilt.apk")
            val alignedApk = File(workDir, "aligned.apk")

            // 1. Decode
            ApkDecodeStep.execute(inputApk, decodedDir)

            // 2. Inject smali
            SmaliInjector.execute(decodedDir)

            // 3. Patch manifest
            ManifestPatcher.execute(decodedDir)

            // 4. Write proxy config
            ProxyConfigInjector.execute(decodedDir, proxyHost, proxyPort)

            // 5. Inject CA cert
            CertInjector.execute(decodedDir, certFile)

            // 6. Patch network security config
            NetworkSecurityConfigPatcher.execute(decodedDir)

            // 7. Rebuild
            ApkBuildStep.execute(decodedDir, rebuiltApk)

            // 8. Zipalign
            ZipalignStep.execute(rebuiltApk, alignedApk)

            // 9. Sign
            outputApk.parentFile?.mkdirs()
            ApkSignStep.execute(alignedApk, outputApk, signingConfig)

            Logger.info("Patched APK ready: ${outputApk.absolutePath}")
        } finally {
            workDir.deleteRecursively()
        }
    }
}
