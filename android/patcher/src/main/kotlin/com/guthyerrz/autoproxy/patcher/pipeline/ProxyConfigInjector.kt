package com.guthyerrz.autoproxy.patcher.pipeline

import com.guthyerrz.autoproxy.patcher.util.Logger
import java.io.File
import java.util.Properties

object ProxyConfigInjector {

    private const val CONFIG_DIR = "assets/auto_proxy"
    private const val CONFIG_FILE = "config.properties"

    fun execute(decodedDir: File, host: String, port: Int) {
        Logger.step("Writing proxy config: $host:$port")

        val configDir = File(decodedDir, CONFIG_DIR)
        configDir.mkdirs()

        val configFile = File(configDir, CONFIG_FILE)
        val props = Properties()
        props.setProperty("host", host)
        props.setProperty("port", port.toString())

        configFile.writer().use { writer ->
            props.store(writer, "Auto Proxy baked-in configuration")
        }

        Logger.info("Proxy config written to $CONFIG_DIR/$CONFIG_FILE")
    }

    internal fun writeConfig(outputFile: File, host: String, port: Int) {
        outputFile.parentFile?.mkdirs()
        val props = Properties()
        props.setProperty("host", host)
        props.setProperty("port", port.toString())
        outputFile.writer().use { writer ->
            props.store(writer, "Auto Proxy baked-in configuration")
        }
    }
}
