package com.guthyerrz.autoproxy.patcher.pipeline

import com.android.apksig.ApkSigner
import com.guthyerrz.autoproxy.patcher.util.DebugKeystore
import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ApkSignStep {

    data class SigningConfig(
        val keystorePath: File,
        val keystorePassword: String,
        val keyAlias: String,
        val keyPassword: String,
    )

    fun execute(inputApk: File, outputApk: File, signingConfig: SigningConfig?) {
        Logger.step("Signing APK")

        val config = signingConfig ?: run {
            val debug = DebugKeystore.getOrCreate(inputApk.parentFile)
            SigningConfig(debug.path, debug.password, debug.keyAlias, debug.keyPassword)
        }

        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            config.keystorePath.inputStream().use {
                keyStore.load(it, config.keystorePassword.toCharArray())
            }

            val privateKey = keyStore.getKey(config.keyAlias, config.keyPassword.toCharArray()) as PrivateKey
            val certChain = keyStore.getCertificateChain(config.keyAlias)
                .map { it as X509Certificate }

            val signerConfig = ApkSigner.SignerConfig.Builder(
                "auto-proxy",
                privateKey,
                certChain,
            ).build()

            if (outputApk.exists()) {
                outputApk.delete()
            }

            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()

            signer.sign()
            Logger.info("Signed APK: ${outputApk.absolutePath}")
        } catch (e: Exception) {
            throw PatcherException("Failed to sign APK: ${e.message}", e)
        }
    }
}
