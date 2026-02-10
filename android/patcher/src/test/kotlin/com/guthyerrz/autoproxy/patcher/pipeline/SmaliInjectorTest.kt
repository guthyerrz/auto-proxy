package com.guthyerrz.autoproxy.patcher.pipeline

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SmaliInjectorTest {

    @Test
    fun `injects smali files into decoded directory`(@TempDir tempDir: File) {
        SmaliInjector.execute(tempDir)

        val smaliDir = File(tempDir, "smali/com/guthyerrz/autoproxy")
        assertTrue(smaliDir.exists())
        assertTrue(smaliDir.isDirectory)

        // Core classes that must always exist (inner class names may vary by compiler)
        val requiredFiles = listOf(
            "AutoProxy.smali",
            "AutoProxyInitializer.smali",
            "OkHttpPatcher.smali",
            "AutoProxyPlatform.smali",
        )

        for (fileName in requiredFiles) {
            val file = File(smaliDir, fileName)
            assertTrue(file.exists(), "Expected file $fileName to exist")
            assertTrue(file.length() > 0, "Expected file $fileName to be non-empty")
        }

        // Should also have inner class / lambda smali files
        val allSmali = smaliDir.listFiles()?.filter { it.extension == "smali" } ?: emptyList()
        assertTrue(allSmali.size >= requiredFiles.size, "Expected at least ${requiredFiles.size} smali files, got ${allSmali.size}")
    }

    @Test
    fun `detects already injected SDK`(@TempDir tempDir: File) {
        SmaliInjector.execute(tempDir)

        assertThrows(PatcherException::class.java) {
            SmaliInjector.execute(tempDir)
        }
    }

    @Test
    fun `AutoProxy smali reads cert from assets instead of R raw`(@TempDir tempDir: File) {
        SmaliInjector.execute(tempDir)

        val autoProxySmali = File(tempDir, "smali/com/guthyerrz/autoproxy/AutoProxy.smali")
        val content = autoProxySmali.readText()

        assertTrue(content.contains("auto_proxy/ca_cert.pem"), "Should reference asset path for cert")
        assertFalse(content.contains("R\$raw"), "Should not reference R.raw")
    }

    @Test
    fun `AutoProxyInitializer smali includes config properties fallback`(@TempDir tempDir: File) {
        SmaliInjector.execute(tempDir)

        val initSmali = File(tempDir, "smali/com/guthyerrz/autoproxy/AutoProxyInitializer.smali")
        val content = initSmali.readText()

        assertTrue(
            content.contains("auto_proxy/config.properties"),
            "Should reference config.properties asset path",
        )
    }
}
