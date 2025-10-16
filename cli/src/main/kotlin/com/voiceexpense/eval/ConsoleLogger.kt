package com.voiceexpense.eval

import com.voiceexpense.ai.parsing.logging.Logger

/**
 * Console implementation of Logger for CLI usage
 */
class ConsoleLogger : Logger {
    override fun d(tag: String, message: String) {
        println("DEBUG [$tag]: $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        System.err.println("ERROR [$tag]: $message")
        throwable?.printStackTrace()
    }

    override fun w(tag: String, message: String) {
        println("WARN [$tag]: $message")
    }

    override fun i(tag: String, message: String) {
        println("INFO [$tag]: $message")
    }

    override fun v(tag: String, message: String) {
        println("VERBOSE [$tag]: $message")
    }
}
