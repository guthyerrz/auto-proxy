package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File
import java.net.URI
import java.util.jar.JarFile

object SmaliInjector {

    private const val SMALI_RESOURCE_DIR = "smali/com/guthyerrz/autoproxy"
    private const val TARGET_SMALI_DIR = "smali/com/guthyerrz/autoproxy"

    fun execute(decodedDir: File) {
        Logger.step("Injecting SDK smali files")

        val targetDir = File(decodedDir, TARGET_SMALI_DIR)
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            throw PatcherException(
                "SDK smali already exists at ${targetDir.absolutePath}. " +
                    "The APK may already be patched."
            )
        }
        targetDir.mkdirs()

        val smaliFiles = discoverSmaliFiles()
        if (smaliFiles.isEmpty()) {
            throw PatcherException("No smali files found in $SMALI_RESOURCE_DIR")
        }

        var injected = 0
        for (smaliFile in smaliFiles) {
            val resourcePath = "$SMALI_RESOURCE_DIR/$smaliFile"
            val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: throw PatcherException("Missing embedded smali resource: $resourcePath")

            val outFile = File(targetDir, smaliFile)
            inputStream.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            injected++
        }

        Logger.info("Injected $injected smali files into primary dex")
    }

    private fun discoverSmaliFiles(): List<String> {
        val url = javaClass.classLoader.getResource(SMALI_RESOURCE_DIR)
            ?: throw PatcherException("Smali resource directory not found: $SMALI_RESOURCE_DIR")

        return when (url.protocol) {
            "jar" -> {
                val jarPath = url.path.substringBefore("!")
                val jarFile = JarFile(File(URI(jarPath)))
                jarFile.use {
                    it.entries().asSequence()
                        .map { entry -> entry.name }
                        .filter { name ->
                            name.startsWith("$SMALI_RESOURCE_DIR/") && name.endsWith(".smali")
                        }
                        .map { name -> name.substringAfterLast("/") }
                        .toList()
                }
            }
            "file" -> {
                File(url.toURI()).listFiles()
                    ?.filter { it.extension == "smali" }
                    ?.map { it.name }
                    ?: emptyList()
            }
            else -> throw PatcherException("Unsupported resource protocol: ${url.protocol}")
        }
    }
}
