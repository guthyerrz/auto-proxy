package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File

object ZipalignStep {

    fun execute(inputApk: File, outputApk: File) {
        Logger.step("Zipaligning APK")

        val zipalign = findZipalign()
            ?: throw PatcherException(
                "zipalign not found. Set ANDROID_HOME or ensure zipalign is on PATH."
            )

        if (outputApk.exists()) {
            outputApk.delete()
        }

        val process = ProcessBuilder(
            zipalign.absolutePath, "-f", "-p", "4",
            inputApk.absolutePath, outputApk.absolutePath
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw PatcherException("zipalign failed (exit $exitCode): $output")
        }

        Logger.info("Zipaligned: ${outputApk.absolutePath}")
    }

    internal fun findZipalign(): File? {
        // Try PATH first
        val pathResult = runCatching {
            val process = ProcessBuilder("which", "zipalign").start()
            val path = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (path.isNotEmpty()) File(path) else null
        }.getOrNull()

        if (pathResult?.exists() == true) return pathResult

        // Try ANDROID_HOME/build-tools/*/zipalign
        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: return null

        val buildToolsDir = File(androidHome, "build-tools")
        if (!buildToolsDir.isDirectory) return null

        return buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedDescending()
            ?.map { File(it, "zipalign") }
            ?.firstOrNull { it.exists() && it.canExecute() }
    }
}
