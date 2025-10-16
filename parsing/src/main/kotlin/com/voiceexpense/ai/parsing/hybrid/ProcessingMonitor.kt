package com.voiceexpense.ai.parsing.hybrid

import java.util.concurrent.atomic.AtomicLong

object ProcessingMonitor {
    data class Snapshot(
        val total: Long,
        val ai: Long,
        val heuristic: Long,
        val validated: Long,
        val totalTimeMs: Long
    ) {
        val aiRate: Double get() = if (total == 0L) 0.0 else ai.toDouble() / total
        val fallbackRate: Double get() = if (total == 0L) 0.0 else heuristic.toDouble() / total
        val validationRate: Double get() = if (total == 0L) 0.0 else validated.toDouble() / total
        val avgTimeMs: Double get() = if (total == 0L) 0.0 else totalTimeMs.toDouble() / total
    }

    private val total = AtomicLong(0)
    private val ai = AtomicLong(0)
    private val heuristic = AtomicLong(0)
    private val validated = AtomicLong(0)
    private val totalTimeMs = AtomicLong(0)

    fun record(result: HybridParsingResult) {
        total.incrementAndGet()
        when (result.method) {
            ProcessingMethod.AI -> ai.incrementAndGet()
            ProcessingMethod.HEURISTIC -> heuristic.incrementAndGet()
        }
        if (result.validated) validated.incrementAndGet()
        totalTimeMs.addAndGet(result.stats.durationMs)
    }

    fun snapshot(): Snapshot = Snapshot(
        total = total.get(),
        ai = ai.get(),
        heuristic = heuristic.get(),
        validated = validated.get(),
        totalTimeMs = totalTimeMs.get()
    )

    fun reset() {
        total.set(0)
        ai.set(0)
        heuristic.set(0)
        validated.set(0)
        totalTimeMs.set(0)
    }
}

