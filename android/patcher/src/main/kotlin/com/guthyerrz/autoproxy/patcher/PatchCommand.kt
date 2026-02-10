package com.guthyerrz.autoproxy.patcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.guthyerrz.autoproxy.patcher.pipeline.ApkPatcher
import com.guthyerrz.autoproxy.patcher.pipeline.ApkSignStep
import com.guthyerrz.autoproxy.patcher.pipeline.PatcherException
import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

class PatchCommand : CliktCommand(name = "patch") {

    override fun help(context: Context) = "Patch an APK to route traffic through a proxy"

    private val inputApk by argument("input-apk", help = "Path to the input APK file")
        .file(mustExist = true, mustBeReadable = true)

    private val host by option("--host", help = "Proxy host address")
        .default("")

    private val port by option("--port", help = "Proxy port number")
        .int()
        .default(0)

    private val cert by option("--cert", help = "Path to CA certificate (.pem)")
        .file(mustExist = true, mustBeReadable = true)

    private val output by option("-o", "--output", help = "Output APK path (default: <input>-patched.apk)")
        .file()

    private val keystore by option("--keystore", help = "Path to signing keystore")
        .file(mustExist = true, mustBeReadable = true)

    private val ksPass by option("--ks-pass", help = "Keystore password")

    private val keyAlias by option("--key-alias", help = "Key alias in keystore")

    private val keyPass by option("--key-pass", help = "Key password")

    override fun run() {
        if (host.isEmpty()) {
            throw PatcherException("--host is required")
        }
        if (port == 0) {
            throw PatcherException("--port is required and must be > 0")
        }

        val outputApk = output ?: run {
            val name = inputApk.nameWithoutExtension
            File(inputApk.parentFile, "$name-patched.apk")
        }

        val signingConfig = if (keystore != null) {
            ApkSignStep.SigningConfig(
                keystorePath = keystore!!,
                keystorePassword = ksPass ?: throw PatcherException("--ks-pass required when using --keystore"),
                keyAlias = keyAlias ?: throw PatcherException("--key-alias required when using --keystore"),
                keyPassword = keyPass ?: throw PatcherException("--key-pass required when using --keystore"),
            )
        } else {
            null
        }

        try {
            ApkPatcher(
                inputApk = inputApk,
                outputApk = outputApk,
                proxyHost = host,
                proxyPort = port,
                certFile = cert,
                signingConfig = signingConfig,
            ).patch()
        } catch (e: PatcherException) {
            Logger.error(e.message ?: "Unknown error")
            throw e
        }
    }
}
