package com.voiceexpense.ai.mediapipe

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import android.util.Log
import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaPipe LLM client implementing the GenAiGateway used by the hybrid parser.
 * Looks for .task or .litertlm models in app-private path: filesDir/llm/
 */
class MediaPipeGenAiClient(private val context: Context, private val modelManager: ModelManager) : GenAiGateway {
    companion object {
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
            // Ensure model is available and detected first
            // This is a blocking call, but tryInitSync is expected to be called from IO context
            val status = kotlinx.coroutines.runBlocking {
                modelManager.ensureModelAvailable(context)
            }

            if (status !is com.voiceexpense.ai.model.ModelManager.ModelStatus.Ready) {
                Log.d("AI.MP", "Model not ready: $status")
                Log.w(TRACE_TAG, "tryInitSync() model not ready")
                return false
            }

            // Use ModelManager to get the detected model file path
            val modelPath = modelManager.getModelPath()
            if (modelPath == null) {
                Log.d("AI.MP", "No model path available from ModelManager")
                Log.w(TRACE_TAG, "tryInitSync() missing model file")
                return false
            }

            val modelFile = File(context.filesDir, modelPath)
            Log.i(TRACE_TAG, "tryInitSync() checking ${modelFile.absolutePath}")

            if (!modelFile.exists() || !modelFile.isFile) {
                Log.d("AI.MP", "Model missing at ${modelFile.absolutePath}")
                Log.w(TRACE_TAG, "tryInitSync() missing model file")
                return false
            }

            // Log model details for debugging
            val fileSizeMB = modelFile.length() / (1024 * 1024)
            val extension = modelFile.extension
            Log.i("AI.MP", "Initializing model: ${modelFile.name} (${fileSizeMB}MB, extension=.$extension)")

            // Check user preference for GPU vs CPU backend
            // Default to GPU for backward compatibility, but allow CPU fallback for Pixel 10 GPU issues
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val useGpu = prefs.getBoolean("use_gpu_backend", true)
            val backend = if (useGpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU

            Log.i("AI.MP", "Selected backend: ${if (useGpu) "GPU" else "CPU"}")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setPreferredBackend(backend)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            Log.i("AI.MP", "Model initialized successfully: ${modelFile.name}")
            Log.i(TRACE_TAG, "tryInitSync() initialized model at ${modelFile.absolutePath}")
            return true
        } catch (t: Throwable) {
            llm = null
            Log.w("AI.MP", "Model init failed: ${t.message}")
            Log.e("AI.MP", "Full error details: ${t.javaClass.simpleName}: ${t.message}")
            Log.e(TRACE_TAG, "tryInitSync() failed: ${t.message}")

            // Add more specific error guidance
            when {
                t.message?.contains("zip") == true -> {
                    Log.e("AI.MP", "Zip error detected - file may be corrupted or incomplete. Try re-downloading the model.")
                }
                t.message?.contains("format") == true -> {
                    Log.e("AI.MP", "Format error - ensure the file is a valid MediaPipe .task or .litertlm bundle.")
                }
            }
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
