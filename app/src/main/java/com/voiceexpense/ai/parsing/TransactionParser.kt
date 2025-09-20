package com.voiceexpense.ai.parsing

import android.util.Log
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
        Log.i(TAG, "TransactionParser.parse() start input='${text.take(120)}'")
        val hybridRes = runCatching { hybrid.parse(text, context) }.getOrNull()
        if (hybridRes != null) {
            Log.i(TAG, "TransactionParser.parse() hybrid.method=${hybridRes.method} validated=${hybridRes.validated}")
            return hybridRes.result
        }

        val fallbackDraft = heuristicExtractor.extract(text, context)
        Log.w(TAG, "TransactionParser.parse() hybrid returned null, using pure heuristic result")
        return fallbackDraft.toParsedResult(context)
    }

    companion object {
        private const val TAG = "AI.Trace"
    }
}
