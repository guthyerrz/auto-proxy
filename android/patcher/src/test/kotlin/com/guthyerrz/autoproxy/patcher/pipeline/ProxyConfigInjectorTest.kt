package com.guthyerrz.autoproxy.patcher.pipeline

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

class ProxyConfigInjectorTest {

    @Test
    fun `writes config properties file`(@TempDir tempDir: File) {
        ProxyConfigInjector.execute(tempDir, "192.168.1.100", 8080)

        val configFile = File(tempDir, "assets/auto_proxy/config.properties")
        assertTrue(configFile.exists())

        val props = Properties()
        configFile.inputStream().use { props.load(it) }

        assertEquals("192.168.1.100", props.getProperty("host"))
        assertEquals("8080", props.getProperty("port"))
    }

    @Test
    fun `overwrites existing config`(@TempDir tempDir: File) {
        ProxyConfigInjector.execute(tempDir, "10.0.0.1", 9090)
        ProxyConfigInjector.execute(tempDir, "192.168.1.200", 8888)

        val configFile = File(tempDir, "assets/auto_proxy/config.properties")
        val props = Properties()
        configFile.inputStream().use { props.load(it) }

        assertEquals("192.168.1.200", props.getProperty("host"))
        assertEquals("8888", props.getProperty("port"))
    }

    @Test
    fun `writeConfig helper writes correctly`(@TempDir tempDir: File) {
        val outputFile = File(tempDir, "test.properties")
        ProxyConfigInjector.writeConfig(outputFile, "example.com", 3128)

        val props = Properties()
        outputFile.inputStream().use { props.load(it) }

        assertEquals("example.com", props.getProperty("host"))
        assertEquals("3128", props.getProperty("port"))
    }
}
