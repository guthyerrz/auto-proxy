package com.guthyerrz.autoproxy.patcher.util

import com.guthyerrz.autoproxy.patcher.pipeline.PatcherException
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

object DebugKeystore {

    private const val ALIAS = "auto-proxy-debug"
    private const val PASSWORD = "android"

    data class KeystoreConfig(
        val path: File,
        val password: String,
        val keyAlias: String,
        val keyPassword: String,
    )

    fun getOrCreate(directory: File): KeystoreConfig {
        val ksFile = File(directory, "auto-proxy-debug.keystore")
        if (!ksFile.exists()) {
            generate(ksFile)
        }
        return KeystoreConfig(
            path = ksFile,
            password = PASSWORD,
            keyAlias = ALIAS,
            keyPassword = PASSWORD,
        )
    }

    private fun generate(file: File) {
        try {
            file.parentFile?.mkdirs()
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            val keyPair = keyPairGen.generateKeyPair()

            val subject = X500Principal("CN=Auto Proxy Debug, O=Auto Proxy")
            val notBefore = Date()
            val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000 * 30) // 30 years

            // Use sun.security.x509 to generate a self-signed cert
            val cert = generateSelfSignedCert(keyPair, subject, notBefore, notAfter)

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, PASSWORD.toCharArray())
            keyStore.setKeyEntry(
                ALIAS,
                keyPair.private,
                PASSWORD.toCharArray(),
                arrayOf(cert),
            )

            file.outputStream().use { keyStore.store(it, PASSWORD.toCharArray()) }
            Logger.info("Generated debug keystore: ${file.absolutePath}")
        } catch (e: Exception) {
            throw PatcherException("Failed to generate debug keystore: ${e.message}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateSelfSignedCert(
        keyPair: java.security.KeyPair,
        subject: X500Principal,
        notBefore: Date,
        notAfter: Date,
    ): X509Certificate {
        // Use Bouncy Castle-style approach via sun.security.x509
        val info = Class.forName("sun.security.x509.X509CertInfo").getDeclaredConstructor().newInstance()
        val validity = Class.forName("sun.security.x509.CertificateValidity")
            .getDeclaredConstructor(Date::class.java, Date::class.java)
            .newInstance(notBefore, notAfter)
        val sn = Class.forName("sun.security.x509.CertificateSerialNumber")
            .getDeclaredConstructor(Int::class.java)
            .newInstance((System.currentTimeMillis() / 1000).toInt())
        val owner = Class.forName("sun.security.x509.X500Name")
            .getDeclaredConstructor(String::class.java)
            .newInstance(subject.name)

        val infoClass = info.javaClass
        val setMethod = infoClass.getMethod("set", String::class.java, Any::class.java)

        setMethod.invoke(info, "validity", validity)
        setMethod.invoke(info, "serialNumber", sn)
        setMethod.invoke(info, "subject", owner)
        setMethod.invoke(info, "issuer", owner)
        setMethod.invoke(info, "key",
            Class.forName("sun.security.x509.CertificateX509Key")
                .getDeclaredConstructor(java.security.PublicKey::class.java)
                .newInstance(keyPair.public))

        val algId = Class.forName("sun.security.x509.AlgorithmId")
            .getMethod("get", String::class.java)
            .invoke(null, "SHA256withRSA")
        setMethod.invoke(info, "algorithmID",
            Class.forName("sun.security.x509.CertificateAlgorithmId")
                .getDeclaredConstructor(Class.forName("sun.security.x509.AlgorithmId"))
                .newInstance(algId))

        val certClass = Class.forName("sun.security.x509.X509CertImpl")
        val cert = certClass.getDeclaredConstructor(infoClass).newInstance(info)
        certClass.getMethod("sign", java.security.PrivateKey::class.java, String::class.java)
            .invoke(cert, keyPair.private, "SHA256withRSA")

        return cert as X509Certificate
    }
}
