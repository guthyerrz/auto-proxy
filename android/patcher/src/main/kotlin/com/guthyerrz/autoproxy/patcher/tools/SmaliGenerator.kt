package com.guthyerrz.autoproxy.patcher.tools

import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.DexFileFactory
import java.io.File

/**
 * CLI entry point for baksmali disassembly.
 * Used by the Gradle `generateSmali` task to convert a DEX file into smali source.
 *
 * Usage: SmaliGenerator <dex-file> <output-dir>
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: SmaliGenerator <dex-file> <output-dir>" }

    val dexFile = File(args[0])
    val outputDir = File(args[1])

    require(dexFile.exists()) { "DEX file not found: ${dexFile.absolutePath}" }
    outputDir.mkdirs()

    println("Loading DEX: ${dexFile.name}")
    val dex = DexFileFactory.loadDexFile(dexFile, null)

    println("Disassembling to: ${outputDir.absolutePath}")
    val options = BaksmaliOptions()
    val success = Baksmali.disassembleDexFile(dex, outputDir, Runtime.getRuntime().availableProcessors(), options)

    require(success) { "Baksmali failed to disassemble DEX" }
    println("Baksmali completed successfully")
}
