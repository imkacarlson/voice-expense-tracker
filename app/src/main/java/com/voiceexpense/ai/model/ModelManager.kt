package com.voiceexpense.ai.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages lifecycle of on-device GenAI model (e.g., Gemini Nano via AICore).
 * This implementation provides a safe, coroutine-based shell that can be
 * wired to ML Kit / AICore when dependencies are available.
 */
class ModelManager {
    private val mutex = Mutex()

    @Volatile
    private var ready: Boolean = false

    /** Model availability status. */
    sealed class ModelStatus {
        data object Ready : ModelStatus()
        data object Downloading : ModelStatus()
        data class Unavailable(val reason: String) : ModelStatus()
        data class Error(val throwable: Throwable) : ModelStatus()
    }

    /** Minimal model info metadata. */
    data class ModelInfo(
        val name: String = "gemini-nano",
        val version: String = "unknown",
        val sizeMb: Int = -1
    )

    suspend fun ensureModelAvailable(): ModelStatus = mutex.withLock {
        if (ready) return ModelStatus.Ready

        // Simplified: assume availability when called. Real implementation would
        // check device capabilities and trigger model download if needed.
        ready = true
        return ModelStatus.Ready
    }

    fun isModelReady(): Boolean = ready

    suspend fun getModelInfo(): ModelInfo = mutex.withLock {
        // Populate from AICore/ML Kit APIs when integrated
        ModelInfo()
    }

    fun unload() {
        ready = false
        
    }
}
