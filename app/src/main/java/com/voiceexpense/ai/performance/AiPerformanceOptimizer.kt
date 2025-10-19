package com.voiceexpense.ai.performance

import android.content.Context
import com.voiceexpense.ai.mediapipe.MediaPipeGenAiClient
import com.voiceexpense.ai.model.ModelManager
import kotlin.system.measureTimeMillis

object AiPerformanceOptimizer {
    data class WarmMetrics(
        val modelStatus: ModelManager.ModelStatus,
        val ensureModelMs: Long,
        val clientInitMs: Long,
        val totalMs: Long,
        val clientReady: Boolean
    )

    suspend fun warmModel(mm: ModelManager, context: Context): Pair<ModelManager.ModelStatus, Long> {
        var status: ModelManager.ModelStatus = ModelManager.ModelStatus.Unavailable("not started")
        val duration = measureTimeMillis {
            status = mm.ensureModelAvailable(context)
            // A real impl would await readiness and maybe pre-load prompts/resources
        }
        return status to duration
    }

    suspend fun warmGenAi(
        context: Context,
        mm: ModelManager,
        client: MediaPipeGenAiClient
    ): WarmMetrics {
        val (status, ensureModelMs) = warmModel(mm, context)
        var ready = false
        val clientInitMs = measureTimeMillis {
            ready = client.isAvailable()
        }
        val totalMs = ensureModelMs + clientInitMs
        return WarmMetrics(
            modelStatus = status,
            ensureModelMs = ensureModelMs,
            clientInitMs = clientInitMs,
            totalMs = totalMs,
            clientReady = ready
        )
    }

    suspend fun <T> measureParse(block: suspend () -> T): Pair<T, Long> {
        var result: T? = null
        val took = measureTimeMillis { result = block() }
        return result as T to took
    }
}
