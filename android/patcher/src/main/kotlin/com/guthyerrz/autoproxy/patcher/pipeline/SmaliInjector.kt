package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

object SmaliInjector {

    private const val SDK_DEX_RESOURCE = "sdk.dex"
    private const val KOTLIN_STDLIB_DEX_RESOURCE = "kotlin-stdlib.dex"
    private val KOTLIN_INTRINSICS_DESCRIPTOR = "Lkotlin/jvm/internal/Intrinsics;".toByteArray(Charsets.UTF_8)

    /**
     * Injects the SDK as a new dex file alongside the existing raw dex files.
     * Copies the pre-built sdk.dex from classpath resources into the decoded APK directory.
     * If the target APK does not contain Kotlin, also injects kotlin-stdlib.dex.
     */
    fun execute(decodedDir: File) {
        Logger.step("Injecting SDK dex")

        val existingDex = File(decodedDir, "classes.dex")
        if (!existingDex.exists()) {
            throw PatcherException("No classes.dex found in decoded APK")
        }

        // Check for Kotlin BEFORE injecting sdk.dex (which itself references Kotlin classes)
        val appHasKotlin = hasKotlinRuntime(decodedDir)

        // Inject sdk.dex
        var nextDexNumber = findNextDexNumber(decodedDir)
        val sdkDexFile = File(decodedDir, "classes${nextDexNumber}.dex")

        val sdkDex = javaClass.classLoader.getResourceAsStream(SDK_DEX_RESOURCE)
            ?: throw PatcherException("Embedded SDK dex not found: $SDK_DEX_RESOURCE")

        sdkDex.use { input ->
            sdkDexFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Logger.info("Injected SDK as ${sdkDexFile.name}")

        // Conditionally inject kotlin-stdlib.dex if target app lacks Kotlin
        if (appHasKotlin) {
            Logger.info("Target APK already contains Kotlin runtime, skipping kotlin-stdlib injection")
        } else {
            Logger.step("Target APK lacks Kotlin runtime, injecting kotlin-stdlib")

            nextDexNumber = findNextDexNumber(decodedDir)
            val kotlinDexFile = File(decodedDir, "classes${nextDexNumber}.dex")

            val kotlinDex = javaClass.classLoader.getResourceAsStream(KOTLIN_STDLIB_DEX_RESOURCE)
                ?: throw PatcherException("Embedded kotlin-stdlib dex not found: $KOTLIN_STDLIB_DEX_RESOURCE")

            kotlinDex.use { input ->
                kotlinDexFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Logger.info("Injected kotlin-stdlib as ${kotlinDexFile.name}")
        }
    }

    /**
     * Scans all classes*.dex files in the decoded directory for the
     * Lkotlin/jvm/internal/Intrinsics; descriptor to detect Kotlin runtime presence.
     */
    private fun hasKotlinRuntime(decodedDir: File): Boolean {
        val dexFiles = decodedDir.listFiles { _, name -> name.matches(Regex("classes\\d*\\.dex")) }
            ?: return false

        return dexFiles.any { dexFile ->
            containsBytes(dexFile, KOTLIN_INTRINSICS_DESCRIPTOR)
        }
    }

    private fun containsBytes(file: File, needle: ByteArray): Boolean {
        val haystack = file.readBytes()
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun findNextDexNumber(decodedDir: File): Int {
        var n = 2
        while (File(decodedDir, "classes${n}.dex").exists()) {
            n++
        }
        return n
    }
}
