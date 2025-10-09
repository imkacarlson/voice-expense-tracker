package com.voiceexpense.ai.mediapipe

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import android.util.Log
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
        private const val TRACE_TAG = "AI.Trace"
    }

    @Volatile private var llm: LlmInference? = null
    private val initializing = AtomicBoolean(false)

    override fun isAvailable(): Boolean = tryInitSync()

    override suspend fun structured(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        Log.i(TRACE_TAG, "MediaPipeGenAiClient.structured() invoked length=${prompt.length}")
        if (!tryInitSync()) {
            Log.w(TRACE_TAG, "structured() aborted: model not initialized")
            Result.failure(IllegalStateException("Model not initialized"))
        } else {
            try {
                val out = llm!!.generateResponse(prompt)
                if (out.isNullOrBlank()) {
                    Log.w("AI.MP", "Empty response from LLM")
                    Result.failure(IllegalStateException("Empty response"))
                } else {
                    Log.d("AI.MP", "LLM respond ok, length=${out.length}")
                    logFullModelResponse(out)
                    Result.success(out)
                }
            } catch (t: Throwable) {
                Log.w("AI.MP", "generateResponse failed: ${t.message}")
                Result.failure(t)
            }
        }
    }

    /** Attempt to initialize the model if not already. */
    private fun tryInitSync(): Boolean {
        if (llm != null) return true
        if (!initializing.compareAndSet(false, true)) return llm != null
        try {
            val modelFile = File(context.filesDir, RELATIVE_MODEL_PATH)
            Log.i(TRACE_TAG, "tryInitSync() checking ${modelFile.absolutePath}")
            if (!modelFile.exists() || !modelFile.isFile) {
                Log.d("AI.MP", "Model missing at ${modelFile.absolutePath}")
                Log.w(TRACE_TAG, "tryInitSync() missing model file")
                return false
            }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            Log.i("AI.MP", "Model initialized: ${modelFile.absolutePath}")
            Log.i(TRACE_TAG, "tryInitSync() initialized model at ${modelFile.absolutePath}")
            return true
        } catch (t: Throwable) {
            llm = null
            Log.w("AI.MP", "Model init failed: ${t.message}")
            Log.e(TRACE_TAG, "tryInitSync() failed: ${t.message}")
            return false
        } finally {
            initializing.set(false)
        }
    }

    fun close() {
        try { llm?.close() } catch (_: Throwable) {}
        llm = null
    }

    private fun logFullModelResponse(response: String) {
        try {
            val text = response.ifBlank { "<blank>" }
            Log.d("AI.Debug", "MediaPipe response start >>>")
            val chunkSize = 2000
            var index = 0
            var chunk = 1
            while (index < text.length) {
                val end = (index + chunkSize).coerceAtMost(text.length)
                val segment = text.substring(index, end)
                Log.d("AI.Debug", "MediaPipe response chunk $chunk:\n$segment")
                index = end
                chunk += 1
            }
            Log.d("AI.Debug", "<<< MediaPipe response end (${text.length} chars)")
        } catch (_: Throwable) {
            // ignore logging failures
        }
    }
}
