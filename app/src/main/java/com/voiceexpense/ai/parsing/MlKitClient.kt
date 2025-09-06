package com.voiceexpense.ai.parsing

import android.content.Context
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.RewritingRequest
import com.voiceexpense.ai.model.ModelManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.guava.await

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
                val res = rewriter.runInference(request).await()
                val best = firstTextFromResult(res)
                if (!best.isNullOrBlank()) Result.success(best) else Result.failure(Exception("No rewrite results returned"))
            } finally {
                try { rewriter.close() } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Run a structured prompt expected to yield JSON. Performs basic post-processing
     * to normalize potential fences and validates shape via StructuredOutputValidator.
     */
    suspend fun structured(prompt: String): Result<String> {
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
                val request = RewritingRequest.builder(prompt).build()
                val res = rewriter.runInference(request).await()
                val rawText = firstTextFromResult(res) ?: return Result.failure(Exception("No results"))
                val normalized = StructuredOutputValidator.normalizeMlKitJson(rawText)
                val vr = StructuredOutputValidator.validateTransactionJson(normalized)
                if (vr.valid) Result.success(normalized) else Result.failure(Exception(vr.error ?: "invalid json"))
            } finally {
                try { rewriter.close() } catch (_: Throwable) {}
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Attempt to extract first text from various possible RewritingResult shapes
    private fun firstTextFromResult(result: Any): String? {
        fun listFrom(method: String): List<*>? = try {
            val m = result.javaClass.getMethod(method)
            (m.invoke(result) as? List<*>)
        } catch (_: Throwable) { null }

        val candidates: List<*>? = listFrom("getResults")
            ?: listFrom("getCandidates")
            ?: listFrom("getRewrites")
            ?: listFrom("getTexts")
            ?: listFrom("getOutputs")

        val first = candidates?.firstOrNull() ?: return null
        if (first is CharSequence) return first.toString()

        val accessors = listOf("getText", "text", "getOutputText", "getContent", "getRewrite", "getResult")
        for (name in accessors) {
            try {
                val m = first.javaClass.getMethod(name)
                val v = m.invoke(first)
                if (v is CharSequence) return v.toString()
            } catch (_: Throwable) { /* try next */ }
        }
        return first.toString()
    }

    /** Release resources and unload model. */
    fun close() {
        initialized = false
        modelManager.unload()
    }
}
