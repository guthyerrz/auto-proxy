package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

object SmaliInjector {

    private const val SDK_DEX_RESOURCE = "sdk.dex"

    /**
     * Injects the SDK as a new dex file alongside the existing raw dex files.
     * Copies the pre-built sdk.dex from classpath resources into the decoded APK directory.
     */
    fun execute(decodedDir: File) {
        Logger.step("Injecting SDK dex")

        val existingDex = File(decodedDir, "classes.dex")
        if (!existingDex.exists()) {
            throw PatcherException("No classes.dex found in decoded APK")
        }

        val nextDexNumber = findNextDexNumber(decodedDir)
        val sdkDexFile = File(decodedDir, "classes${nextDexNumber}.dex")

        val sdkDex = javaClass.classLoader.getResourceAsStream(SDK_DEX_RESOURCE)
            ?: throw PatcherException("Embedded SDK dex not found: $SDK_DEX_RESOURCE")

        sdkDex.use { input ->
            sdkDexFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Logger.info("Injected SDK as ${sdkDexFile.name}")
    }

    private fun findNextDexNumber(decodedDir: File): Int {
        var n = 2
        while (File(decodedDir, "classes${n}.dex").exists()) {
            n++
        }
        return n
    }
}
