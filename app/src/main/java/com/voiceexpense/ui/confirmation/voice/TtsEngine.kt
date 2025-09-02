package com.voiceexpense.ui.confirmation.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Minimal TTS wrapper with cooperative cancellation and stop().
 * This starts as a stub that simply delays proportional to text length.
 * Replace with Android TextToSpeech integration in production.
 */
open class TtsEngine {
    @Volatile private var stopped: Boolean = false

    open suspend fun speak(text: String) = withContext(Dispatchers.Default) {
        stopped = false
        // Simulate speaking time; 15ms per character capped.
        val millis = text.length.coerceAtMost(160) * 15L
        var elapsed = 0L
        val step = 60L
        while (!stopped && elapsed < millis) {
            delay(step)
            elapsed += step
        }
    }

    fun stop() {
        stopped = true
    }
}
