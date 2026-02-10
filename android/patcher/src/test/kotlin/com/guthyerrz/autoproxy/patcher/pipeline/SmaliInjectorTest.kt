package com.guthyerrz.autoproxy.patcher.pipeline

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SmaliInjectorTest {

    @Test
    fun `injects SDK as new dex file`(@TempDir tempDir: File) {
        File(tempDir, "classes.dex").writeBytes(byteArrayOf())

        SmaliInjector.execute(tempDir)

        val sdkDex = File(tempDir, "classes2.dex")
        assertTrue(sdkDex.exists(), "Expected classes2.dex to be created")
        assertTrue(sdkDex.length() > 0, "Expected classes2.dex to be non-empty")
    }

    @Test
    fun `uses next available dex slot`(@TempDir tempDir: File) {
        File(tempDir, "classes.dex").writeBytes(byteArrayOf())
        File(tempDir, "classes2.dex").writeBytes(byteArrayOf())
        File(tempDir, "classes3.dex").writeBytes(byteArrayOf())

        SmaliInjector.execute(tempDir)

        assertTrue(File(tempDir, "classes4.dex").exists(), "Expected classes4.dex to be created")
    }

    @Test
    fun `fails without classes dex`(@TempDir tempDir: File) {
        assertThrows(PatcherException::class.java) {
            SmaliInjector.execute(tempDir)
        }
    }
}
