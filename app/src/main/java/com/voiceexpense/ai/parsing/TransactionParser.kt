package com.voiceexpense.ai.parsing

import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.heuristic.toParsedResult
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser

class TransactionParser(
    @Suppress("unused") private val mlKit: ModelManager = ModelManager(),
    private val hybrid: HybridTransactionParser,
    private val heuristicExtractor: HeuristicExtractor = HeuristicExtractor()
) {
    // MediaPipe LLM is used via HybridTransactionParser; falls back to heuristics when needed.
    suspend fun parse(text: String, context: ParsingContext = ParsingContext()): ParsedResult {
        val hybridRes = runCatching { hybrid.parse(text, context) }.getOrNull()
        if (hybridRes != null) {
            return hybridRes.result
        }

        val fallbackDraft = heuristicExtractor.extract(text, context)
        return fallbackDraft.toParsedResult(context)
    }
}
