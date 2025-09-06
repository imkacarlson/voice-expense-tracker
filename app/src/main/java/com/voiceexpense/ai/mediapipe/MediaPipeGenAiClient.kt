package com.voiceexpense.ai.mediapipe

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaPipe LLM client implementing the GenAiGateway used by the hybrid parser.
 * Looks for a .task model at app-private path: filesDir/llm/model.task.
 */
class MediaPipeGenAiClient(private val context: Context) : GenAiGateway {
    companion object {
        private const val RELATIVE_MODEL_PATH = "llm/model.task"
    }

    @Volatile private var llm: LlmInference? = null
    private val initializing = AtomicBoolean(false)

    override fun isAvailable(): Boolean = tryInitSync()

    override suspend fun structured(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (!tryInitSync()) return@withContext Result.failure(IllegalStateException("Model not initialized"))
        return@withContext try {
            val out = llm!!.generateResponse(prompt)
            if (out.isNullOrBlank()) Result.failure(IllegalStateException("Empty response")) else Result.success(out)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Attempt to initialize the model if not already. */
    private fun tryInitSync(): Boolean {
        if (llm != null) return true
        if (!initializing.compareAndSet(false, true)) return llm != null
        try {
            val modelFile = File(context.filesDir, RELATIVE_MODEL_PATH)
            if (!modelFile.exists() || !modelFile.isFile) {
                return false
            }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.7f)
                .setRandomSeed(101)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            return true
        } catch (_: Throwable) {
            llm = null
            return false
        } finally {
            initializing.set(false)
        }
    }

    fun close() {
        try { llm?.close() } catch (_: Throwable) {}
        llm = null
    }
}

