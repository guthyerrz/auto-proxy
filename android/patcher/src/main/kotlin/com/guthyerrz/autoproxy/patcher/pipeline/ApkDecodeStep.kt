package com.guthyerrz.autoproxy.patcher.pipeline

import brut.androlib.ApkDecoder
import brut.androlib.Config
import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

object ApkDecodeStep {

    fun execute(apkFile: File, outputDir: File) {
        Logger.step("Decoding APK: ${apkFile.name}")
        try {
            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }

            // Skip dex→smali decompilation entirely. All dex files are kept as
            // raw binaries so they never go through a lossy baksmali→smali
            // round-trip that can fail on certain bytecode patterns.
            // The SDK classes are injected as a new dex file by SmaliInjector.
            val config = Config.getDefaultConfig()
            config.forceDelete = true
            config.setDecodeSources(Config.DECODE_SOURCES_NONE)
            val decoder = ApkDecoder(config, apkFile)
            decoder.decode(outputDir)
            Logger.info("Decoded to: ${outputDir.absolutePath}")
        } catch (e: Exception) {
            throw PatcherException("Failed to decode APK: ${e.message}", e)
        }
    }
}
