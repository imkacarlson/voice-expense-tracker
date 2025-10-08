package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.ParsedResult

enum class ProcessingMethod { AI, HEURISTIC }

data class ProcessingStatistics(
    val durationMs: Long
)

data class HybridParsingResult(
    val result: ParsedResult,
    val method: ProcessingMethod,
    val validated: Boolean,
    val confidence: Float,
    val stats: ProcessingStatistics,
    val rawJson: String? = null,
    val errors: List<String> = emptyList(),
    val staged: StagedParsingResult? = null
)
