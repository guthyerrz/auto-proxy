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
            val config = Config.getDefaultConfig()
            config.forceDelete = true
            val decoder = ApkDecoder(config, apkFile)
            decoder.decode(outputDir)
            Logger.info("Decoded to: ${outputDir.absolutePath}")
        } catch (e: Exception) {
            throw PatcherException("Failed to decode APK: ${e.message}", e)
        }
    }
}
