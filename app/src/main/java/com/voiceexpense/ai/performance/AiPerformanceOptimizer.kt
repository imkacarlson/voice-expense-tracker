package com.voiceexpense.ai.performance

import com.voiceexpense.ai.model.ModelManager
import kotlin.system.measureTimeMillis

object AiPerformanceOptimizer {
    data class Metrics(
        val modelWarmMs: Long,
        val parseMs: Long
    )

    suspend fun warmModel(mm: ModelManager): Long = measureTimeMillis {
        mm.ensureModelAvailable()
        // A real impl would await readiness and maybe pre-load prompts/resources
    }

    suspend fun <T> measureParse(block: suspend () -> T): Pair<T, Long> {
        var result: T? = null
        val took = measureTimeMillis { result = block() }
        return result as T to took
    }
}

