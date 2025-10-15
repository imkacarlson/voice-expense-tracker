package com.voiceexpense.ai.model

import android.util.Log
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages lifecycle/availability of MediaPipe LLM model.
 * Checks for .task or .litertlm models in app-private storage: filesDir/llm/
 */
class ModelManager {
    private val mutex = Mutex()

    @Volatile private var ready: Boolean = false
    @Volatile private var detectedModelPath: String? = null

    /** Model availability status. */
    sealed class ModelStatus {
        data object Ready : ModelStatus()
        data class Unavailable(val reason: String) : ModelStatus()
        data class Error(val throwable: Throwable) : ModelStatus()
    }

    /** Minimal model info metadata. */
    data class ModelInfo(
        val name: String = "mediapipe-llm",
        val version: String = "unknown",
        val sizeMb: Int = -1,
        val fileName: String? = null
    )

    /**
     * Detects a valid model file in the llm/ directory.
     * Looks for files with .task or .litertlm extensions.
     * Returns the relative path if found, null otherwise.
     */
    private fun detectModelFile(context: Context): String? {
        val llmDir = File(context.filesDir, MODEL_DIRECTORY)
        if (!llmDir.exists() || !llmDir.isDirectory) {
            Log.d(TAG, "detectModelFile() llm directory does not exist")
            return null
        }

        val modelFiles = llmDir.listFiles { file ->
            file.isFile && (file.name.endsWith(TASK_EXTENSION) || file.name.endsWith(LITERTLM_EXTENSION))
        }

        if (modelFiles.isNullOrEmpty()) {
            Log.d(TAG, "detectModelFile() no .task or .litertlm files found")
            return null
        }

        // Return the first valid model file found
        val modelFile = modelFiles.first()
        val relativePath = "$MODEL_DIRECTORY/${modelFile.name}"
        Log.i(TAG, "detectModelFile() found model: ${modelFile.name}")
        return relativePath
    }

    suspend fun ensureModelAvailable(context: Context? = null): ModelStatus =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (ready && detectedModelPath != null) return@withLock ModelStatus.Ready

                if (context == null) {
                    ready = false
                    return@withLock ModelStatus.Unavailable("Context not provided")
                }

                val modelPath = detectModelFile(context)
                Log.i(TAG, "ensureModelAvailable() detected path=$modelPath")

                if (modelPath == null) {
                    ready = false
                    detectedModelPath = null
                    Log.w(TAG, "ensureModelAvailable() missing model file")
                    return@withLock ModelStatus.Unavailable(
                        "No model file (.task or .litertlm) found in $MODEL_DIRECTORY/"
                    )
                }

                val path = File(context.filesDir, modelPath)
                if (!path.exists() || !path.isFile) {
                    ready = false
                    detectedModelPath = null
                    Log.w(TAG, "ensureModelAvailable() model file not accessible")
                    return@withLock ModelStatus.Unavailable(
                        "Model file not accessible: $modelPath"
                    )
                }

                ready = true
                detectedModelPath = modelPath
                Log.i(TAG, "ensureModelAvailable() marked ready=true for $modelPath")
                return@withLock ModelStatus.Ready
            }
        }

    /**
     * Returns the detected model file path relative to filesDir.
     * Must call ensureModelAvailable() first.
     */
    fun getModelPath(): String? = detectedModelPath

    fun isModelReady(): Boolean = ready

    suspend fun getModelInfo(context: Context? = null): ModelInfo =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (context == null) return@withLock ModelInfo()

                val modelPath = detectedModelPath ?: detectModelFile(context)
                val f = modelPath?.let { File(context.filesDir, it) }
                val sizeMb = if (f != null && f.exists()) (f.length() / (1024 * 1024)).toInt() else -1
                val fileName = f?.name
                ModelInfo(sizeMb = sizeMb, fileName = fileName)
            }
        }

    fun unload() {
        ready = false
        detectedModelPath = null
    }

    companion object {
        const val MODEL_DIRECTORY: String = "llm"
        const val TASK_EXTENSION: String = ".task"
        const val LITERTLM_EXTENSION: String = ".litertlm"

        @Deprecated("Use detectModelFile() instead", ReplaceWith("detectModelFile()"))
        const val DEFAULT_RELATIVE_MODEL_PATH: String = "llm/model.task"

        private const val TAG = "AI.Trace"
    }
}
