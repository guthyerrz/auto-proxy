package com.guthyerrz.autoproxy.patcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.guthyerrz.autoproxy.patcher.pipeline.PatcherException
import com.guthyerrz.autoproxy.patcher.util.Logger

class AutoProxyCli : CliktCommand(name = "auto-proxy") {
    override fun help(context: Context) = "Auto Proxy APK Patcher — inject proxy config into compiled APKs"
    override fun run() = Unit
}

fun main(args: Array<String>) {
    try {
        AutoProxyCli()
            .subcommands(PatchCommand())
            .main(args)
    } catch (e: PatcherException) {
        Logger.error(e.message ?: "Unknown error")
        System.exit(1)
    }
}
