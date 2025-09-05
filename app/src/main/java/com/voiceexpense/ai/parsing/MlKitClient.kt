package com.voiceexpense.ai.parsing

import android.content.Context
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.voiceexpense.ai.model.ModelManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around ML Kit GenAI Rewriting API.
 *
 * Notes:
 * - Keeps ML Kit specifics isolated from business logic.
 * - Coordinates lifecycle via [ModelManager].
 * - Safe no-op/failure behavior when the feature is unavailable.
 */
class MlKitClient(
    private val context: Context,
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
     */
    suspend fun rewrite(input: String, systemPrompt: String? = null): Result<String> {
        if (!initialized || !modelManager.isModelReady()) {
            return Result.failure(IllegalStateException("GenAI model not ready"))
        }

        return try {
            val rewriterOptions = RewriterOptions.builder(context)
                .setOutputType(RewriterOptions.OutputType.REPHRASE)
                .setLanguage(RewriterOptions.Language.ENGLISH)
                .build()

            val rewriter = Rewriting.getClient(rewriterOptions)
            try {
                val request = RewritingRequest.builder(input).build()
                val results = rewriter.runInference(request).await().results
                if (results.isNotEmpty()) {
                    Result.success(results.first())
                } else {
                    Result.failure(Exception("No rewrite results returned"))
                }
            } finally {
                try { rewriter.close() } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Release resources and unload model. */
    fun close() {
        initialized = false
        modelManager.unload()
    }
}
