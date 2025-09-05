package com.voiceexpense.ai.parsing

import com.voiceexpense.ai.model.ModelManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thin wrapper around ML Kit GenAI Rewriting API.
 *
 * Notes:
 * - Keeps ML Kit specifics isolated from business logic.
 * - Coordinates lifecycle via [ModelManager].
 * - Safe no-op/failure behavior when the feature is unavailable.
 */
class MlKitClient(
    private val modelManager: ModelManager = ModelManager()
) {
    private val mutex = Mutex()
    @Volatile private var initialized: Boolean = false

    /** Status of the GenAI rewriting capability. */
    sealed class Status {
        data object Available : Status()
        data class Unavailable(val reason: String) : Status()
        data object NotInitialized : Status()
    }

    /** Ensure model is prepared; returns current status. */
    suspend fun ensureReady(): Status = mutex.withLock {
        return try {
            when (val s = modelManager.ensureModelAvailable()) {
                is ModelManager.ModelStatus.Ready -> {
                    initialized = true
                    Status.Available
                }
                is ModelManager.ModelStatus.Downloading -> {
                    initialized = false
                    Status.Unavailable("Model downloading")
                }
                is ModelManager.ModelStatus.Unavailable -> {
                    initialized = false
                    Status.Unavailable(s.reason)
                }
                is ModelManager.ModelStatus.Error -> {
                    initialized = false
                    Status.Unavailable("${s.throwable.message ?: "Unknown error"}")
                }
            }
        } catch (t: Throwable) {
            initialized = false
            Status.Unavailable(t.message ?: "Unknown error")
        }
    }

    /** Quick availability check (does not trigger downloads). */
    fun isAvailable(): Boolean = modelManager.isModelReady()

    /**
     * Rewrite input text using on-device GenAI.
     *
     * Currently a placeholder that validates readiness and returns a controlled failure until
     * the ML Kit dependency is wired. This prevents silent fall-through into unexpected behavior.
     */
    suspend fun rewrite(input: String, systemPrompt: String? = null): Result<String> {
        if (!initialized || !modelManager.isModelReady()) {
            return Result.failure(IllegalStateException("GenAI model not ready"))
        }

        // TODO: Integrate with ML Kit GenAI Rewriting API when available, e.g.:
        // val client = GenAiRewritingClient.create(...)
        // val output = client.rewrite(input, options)
        // return Result.success(output)
        return Result.failure(UnsupportedOperationException("ML Kit GenAI rewriting not wired yet"))
    }

    /** Release resources and unload model. */
    fun close() {
        initialized = false
        modelManager.unload()
    }
}

