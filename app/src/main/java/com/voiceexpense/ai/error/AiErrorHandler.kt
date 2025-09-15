package com.voiceexpense.ai.error

import com.voiceexpense.ai.model.ModelManager
import java.util.concurrent.atomic.AtomicInteger

object AiErrorHandler {
    data class RecoveryAction(
        val message: String,
        val canRetry: Boolean,
        val retryDelayMs: Long = 0L
    )

    fun fromModelStatus(status: ModelManager.ModelStatus): RecoveryAction = when (status) {
        is ModelManager.ModelStatus.Ready -> RecoveryAction("Model ready", canRetry = false)
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

    // Hybrid processing error handling
    private val consecutiveHybridFailures = AtomicInteger(0)
    private const val breakerThreshold = 3

    fun recordHybridFailure() {
        consecutiveHybridFailures.incrementAndGet()
    }

    fun resetHybridFailures() {
        consecutiveHybridFailures.set(0)
    }

    fun isHybridCircuitOpen(): Boolean = consecutiveHybridFailures.get() >= breakerThreshold

    fun fromHybridErrors(errors: List<String>): RecoveryAction {
        val msg = if (errors.isNotEmpty()) errors.joinToString("; ") else "Unknown hybrid processing error"
        val open = isHybridCircuitOpen()
        return if (open) {
            RecoveryAction(
                message = "AI temporarily disabled due to repeated failures. Using fallback.",
                canRetry = true,
                retryDelayMs = 5_000L
            )
        } else {
            RecoveryAction(
                message = "Hybrid error: $msg",
                canRetry = true,
                retryDelayMs = 500L
            )
        }
    }
}
