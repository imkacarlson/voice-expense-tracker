package com.voiceexpense.ai.model

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Manages lifecycle/availability of MediaPipe LLM model.
 * Checks for the .task model in app-private storage: filesDir/llm/model.task.
 */
class ModelManager {
    private val mutex = Mutex()

    @Volatile private var ready: Boolean = false

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
        val sizeMb: Int = -1
    )

    suspend fun ensureModelAvailable(context: Context? = null): ModelStatus = mutex.withLock {
        if (ready) return ModelStatus.Ready
        val path = context?.let { File(it.filesDir, DEFAULT_RELATIVE_MODEL_PATH) }
        if (path == null || !path.exists() || !path.isFile) {
            ready = false
            return ModelStatus.Unavailable("Model file missing at app files: $DEFAULT_RELATIVE_MODEL_PATH")
        }
        ready = true
        return ModelStatus.Ready
    }

    fun isModelReady(): Boolean = ready

    suspend fun getModelInfo(context: Context? = null): ModelInfo = mutex.withLock {
        val f = context?.let { File(it.filesDir, DEFAULT_RELATIVE_MODEL_PATH) }
        val sizeMb = if (f != null && f.exists()) (f.length() / (1024 * 1024)).toInt() else -1
        ModelInfo(sizeMb = sizeMb)
    }

    fun unload() {
        ready = false
    }

    companion object {
        const val DEFAULT_RELATIVE_MODEL_PATH: String = "llm/model.task"
    }
}
