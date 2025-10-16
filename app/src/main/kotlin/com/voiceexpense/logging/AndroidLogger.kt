package com.voiceexpense.logging

import android.util.Log as AndroidLog
import com.voiceexpense.ai.parsing.logging.Logger

/**
 * Android implementation of Logger that delegates to android.util.Log
 */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        AndroidLog.d(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            AndroidLog.e(tag, message, throwable)
        } else {
            AndroidLog.e(tag, message)
        }
    }

    override fun w(tag: String, message: String) {
        AndroidLog.w(tag, message)
    }

    override fun i(tag: String, message: String) {
        AndroidLog.i(tag, message)
    }

    override fun v(tag: String, message: String) {
        AndroidLog.v(tag, message)
    }
}
