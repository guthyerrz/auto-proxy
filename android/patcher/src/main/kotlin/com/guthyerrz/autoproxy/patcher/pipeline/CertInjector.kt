package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

object CertInjector {

    private const val CERT_DIR = "assets/auto_proxy"
    private const val CERT_FILE = "ca_cert.pem"
    private const val DEFAULT_CERT_RESOURCE = "default_ca_cert.pem"

    fun execute(decodedDir: File, certFile: File?) {
        Logger.step("Injecting CA certificate")

        val targetDir = File(decodedDir, CERT_DIR)
        targetDir.mkdirs()
        val targetFile = File(targetDir, CERT_FILE)

        if (certFile != null) {
            if (!certFile.exists()) {
                throw PatcherException("Certificate file not found: ${certFile.absolutePath}")
            }
            certFile.copyTo(targetFile, overwrite = true)
            Logger.info("Copied CA cert from: ${certFile.absolutePath}")
        } else {
            val defaultCert = javaClass.classLoader.getResourceAsStream(DEFAULT_CERT_RESOURCE)
                ?: throw PatcherException("Default CA certificate resource not found")
            defaultCert.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Logger.info("Using embedded default CA certificate")
        }
    }
}
