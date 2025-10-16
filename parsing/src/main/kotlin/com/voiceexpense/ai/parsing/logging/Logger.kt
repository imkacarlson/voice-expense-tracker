package com.voiceexpense.ai.parsing.logging

/**
 * Platform-agnostic logging interface.
 * Implementations provide platform-specific logging (Android, Console, etc.)
 */
interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String)
    fun i(tag: String, message: String)
    fun v(tag: String, message: String)
}

/**
 * Companion object for easy logging access.
 * Use Log.d(), Log.e(), etc. just like android.util.Log
 */
object Log {
    private var logger: Logger = NoOpLogger()

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    fun d(tag: String, message: String) = logger.d(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = logger.e(tag, message, throwable)
    fun w(tag: String, message: String) = logger.w(tag, message)
    fun i(tag: String, message: String) = logger.i(tag, message)
    fun v(tag: String, message: String) = logger.v(tag, message)
}

/**
 * No-op logger used as default (does nothing).
 * Prevents crashes if no logger is set.
 */
private class NoOpLogger : Logger {
    override fun d(tag: String, message: String) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
    override fun w(tag: String, message: String) {}
    override fun i(tag: String, message: String) {}
    override fun v(tag: String, message: String) {}
}
