package com.guthyerrz.autoproxy.patcher.pipeline

import brut.androlib.ApkBuilder
import brut.androlib.Config
import brut.directory.ExtFile
import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

object ApkBuildStep {

    fun execute(decodedDir: File, outputApk: File) {
        Logger.step("Rebuilding APK")
        try {
            if (outputApk.exists()) {
                outputApk.delete()
            }
            val config = Config.getDefaultConfig()
            val extDir = ExtFile(decodedDir)
            val builder = ApkBuilder(config, extDir)
            builder.build(outputApk)
            Logger.info("Built APK: ${outputApk.absolutePath}")
        } catch (e: Exception) {
            throw PatcherException("Failed to rebuild APK: ${e.message}", e)
        }
    }
}
