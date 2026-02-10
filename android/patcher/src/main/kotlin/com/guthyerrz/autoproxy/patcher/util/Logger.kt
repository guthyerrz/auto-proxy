package com.guthyerrz.autoproxy.patcher.util

object Logger {

    private const val RESET = "\u001B[0m"
    private const val GREEN = "\u001B[32m"
    private const val YELLOW = "\u001B[33m"
    private const val RED = "\u001B[31m"
    private const val CYAN = "\u001B[36m"

    fun info(message: String) {
        println("${GREEN}[✓]${RESET} $message")
    }

    fun warn(message: String) {
        println("${YELLOW}[!]${RESET} $message")
    }

    fun error(message: String) {
        System.err.println("${RED}[✗]${RESET} $message")
    }

    fun step(message: String) {
        println("${CYAN}[→]${RESET} $message")
    }
}
