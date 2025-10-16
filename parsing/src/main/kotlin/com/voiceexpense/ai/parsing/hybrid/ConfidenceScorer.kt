package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.ParsedResult

object ConfidenceScorer {
    fun score(method: ProcessingMethod, validated: Boolean, parsed: ParsedResult?): Float {
        var s = (parsed?.confidence ?: 0.5f).coerceIn(0f, 1f)

        // Method weighting
        if (method == ProcessingMethod.AI) s += 0.1f

        // Validation signal
        s += if (validated) 0.1f else -0.15f

        // Field completeness
        if (parsed != null) {
            if (parsed.type in setOf("Expense", "Income", "Transfer")) s += 0.1f
            if (parsed.merchant.isNotBlank()) s += 0.05f
            if (parsed.amountUsd != null || parsed.type == "Transfer") s += 0.05f
            if (parsed.tags.isNotEmpty()) s += 0.02f
        }

        return s.coerceIn(0f, 1f)
    }
}

