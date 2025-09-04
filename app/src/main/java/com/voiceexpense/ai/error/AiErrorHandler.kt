package com.voiceexpense.ai.error

import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.speech.RecognitionError

object AiErrorHandler {
    data class RecoveryAction(
        val message: String,
        val canRetry: Boolean,
        val retryDelayMs: Long = 0L
    )

    fun fromRecognitionError(err: RecognitionError): RecoveryAction = when (err) {
        is RecognitionError.NoPermission -> RecoveryAction(
            message = "Microphone permission required. Please enable in settings.",
            canRetry = false
        )
        is RecognitionError.Timeout -> RecoveryAction(
            message = "Didn’t catch that. Try speaking again in a quiet place.",
            canRetry = true,
            retryDelayMs = 500L
        )
        is RecognitionError.Api -> RecoveryAction(
            message = "Speech recognition error (${err.code}). Please try again.",
            canRetry = true,
            retryDelayMs = 300L
        )
        is RecognitionError.Other -> RecoveryAction(
            message = err.message,
            canRetry = true,
            retryDelayMs = 300L
        )
        is RecognitionError.Unavailable -> RecoveryAction(
            message = "Speech recognition not available on this device.",
            canRetry = false
        )
    }

    fun fromModelStatus(status: ModelManager.ModelStatus): RecoveryAction = when (status) {
        is ModelManager.ModelStatus.Ready -> RecoveryAction("Model ready", canRetry = false)
        is ModelManager.ModelStatus.Downloading -> RecoveryAction(
            message = "Downloading on-device model…",
            canRetry = true,
            retryDelayMs = 1_000L
        )
        is ModelManager.ModelStatus.Unavailable -> RecoveryAction(
            message = "Model unavailable: ${status.reason}",
            canRetry = true,
            retryDelayMs = 2_000L
        )
        is ModelManager.ModelStatus.Error -> RecoveryAction(
            message = "Model error: ${status.throwable.message}",
            canRetry = true,
            retryDelayMs = 2_000L
        )
    }
}

