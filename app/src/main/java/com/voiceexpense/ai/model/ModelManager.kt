package com.voiceexpense.ai.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages lifecycle of on-device GenAI model (e.g., Gemini Nano via AICore).
 * This implementation provides a safe, coroutine-based shell that can be
 * wired to ML Kit / AICore when dependencies are available.
 */
class ModelManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var ready: Boolean = false

    private var prepareJob: Job? = null

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

        // Cancel any ongoing preparation to avoid overlap
        prepareJob?.cancel()

        // Placeholder for real AICore/ML Kit model check + download
        // In a real implementation, inspect device AICore availability and
        // trigger model download if needed, reporting progress to UI.
        prepareJob = scope.launch {
            // Simulated preparation latency
            delay(200)
            ready = true
        }

        // Optimistically report Downloading while job completes
        return ModelStatus.Downloading
    }

    fun isModelReady(): Boolean = ready

    suspend fun getModelInfo(): ModelInfo = mutex.withLock {
        // Populate from AICore/ML Kit APIs when integrated
        ModelInfo()
    }

    fun unload() {
        ready = false
        prepareJob?.cancel()
        prepareJob = null
    }
}
